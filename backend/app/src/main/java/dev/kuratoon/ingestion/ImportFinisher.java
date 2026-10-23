package dev.kuratoon.ingestion;

import dev.kuratoon.chapter.Chapter;
import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.chapter.PageEntry;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.image.ImageToolException;
import dev.kuratoon.image.ThumbnailService;
import dev.kuratoon.ingestion.ImportFaults.Step;
import dev.kuratoon.ingestion.JobDetail.CommitPlan;
import dev.kuratoon.media.CoverCrop;
import dev.kuratoon.media.Media;
import dev.kuratoon.media.MediaRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;

/**
 * Commit steps 4–6 for a committed job: move staging into place, thumbnails, sidecar, remove the
 * source folder, DONE. Every step is idempotent, so the same code finishes a normal import and
 * rolls a crashed one forward on startup (docs/spec/02-ingestion.md "Commit order").
 */
@Service
public class ImportFinisher {

    private static final Logger log = LoggerFactory.getLogger(ImportFinisher.class);

    /** The library cannot be completed from what is on disk; the admin has to look. */
    public static class CannotFinishException extends Exception {
        public CannotFinishException(String message) {
            super(message);
        }
    }

    private record MediaFiles(String coverFile, CoverCrop coverCrop, List<Chapter> chapters) {}

    private final ImportJobRepository jobs;
    private final MediaRepository media;
    private final ChapterRepository chapters;
    private final LibraryPaths paths;
    private final ThumbnailService thumbnails;
    private final SidecarService sidecars;
    private final ImportFaults faults;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ImportFinisher(
            ImportJobRepository jobs,
            MediaRepository media,
            ChapterRepository chapters,
            LibraryPaths paths,
            ThumbnailService thumbnails,
            SidecarService sidecars,
            ImportFaults faults,
            TransactionTemplate tx,
            Clock clock) {
        this.jobs = jobs;
        this.media = media;
        this.chapters = chapters;
        this.paths = paths;
        this.thumbnails = thumbnails;
        this.sidecars = sidecars;
        this.faults = faults;
        this.tx = tx;
        this.clock = clock;
    }

    public void finish(UUID jobId) throws CannotFinishException, IOException {
        ImportJob job = tx.execute(s -> jobs.findById(jobId).orElseThrow(() -> new NotFoundException("Import job not found.")));
        CommitPlan plan = job.detail().commit();
        if (job.getCommittedAt() == null || plan == null) {
            throw new IllegalStateException("job " + jobId + " is not committed");
        }
        UUID mediaId = plan.mediaId();
        MediaFiles files = tx.execute(s -> media.findById(mediaId)
                .map(m -> new MediaFiles(m.getCoverFile(), m.getCoverCrop(), chapters.findByMediaIdOrdered(mediaId)))
                .orElse(null));
        if (files == null) {
            throw new CannotFinishException("The imported media " + mediaId + " no longer exists.");
        }

        moveIntoPlace(jobId, plan);
        verifyComplete(mediaId, files);
        faults.checkpoint(Step.AFTER_MOVE);

        List<String> warnings = new ArrayList<>();
        Path mediaDir = paths.mediaDir(mediaId);
        if (files.coverFile() != null && (job.getKind() != ImportKind.UPDATE || !thumbnails.coverThumbsExist(mediaId))) {
            try {
                thumbnails.generateCover(mediaId, mediaDir.resolve(files.coverFile()), files.coverCrop());
            } catch (ImageToolException e) {
                log.warn("Thumbnails for media {} failed: {}", mediaId, e.getMessage());
                warnings.add("Cover thumbnails could not be generated (" + e.getMessage()
                        + "). Regenerate them from the integrity scan.");
            }
        }
        faults.checkpoint(Step.AFTER_THUMBNAILS);

        sidecars.write(mediaId);
        faults.checkpoint(Step.AFTER_SIDECAR);

        removeSource(job, warnings);
        FileSystemUtils.deleteRecursively(paths.staging(jobId));

        int count = plan.mediaFolderNew() ? files.chapters().size() : plan.folders().size();
        tx.executeWithoutResult(s -> {
            ImportJob j = jobs.findById(jobId).orElseThrow();
            for (String w : warnings) {
                j.setDetail(j.detail().withWarning(w));
            }
            j.setDetail(j.detail().withAttention(null));
            j.moveTo(ImportStatus.DONE, doneMessage(j.getKind(), count, plan.replaced().size()), clock.instant());
        });
    }

    private static String doneMessage(ImportKind kind, int chapterCount, int replacedCount) {
        String chaptersText = chapterCount == 1 ? "1 chapter" : chapterCount + " chapters";
        return switch (kind) {
            case NEW -> "Imported " + chaptersText + ".";
            case RESTORE -> "Restored " + chaptersText + ".";
            case UPDATE -> chapterCount == 0
                    ? "Nothing to add: every chapter was skipped."
                    : "Added " + (chapterCount - replacedCount) + ", replaced " + replacedCount + " chapter(s).";
        };
    }

    // --- step 4 ---------------------------------------------------------------------------------

    private void moveIntoPlace(UUID jobId, CommitPlan plan) throws IOException, CannotFinishException {
        Path staged = paths.stagedMedia(jobId);
        Path mediaDir = paths.mediaDir(plan.mediaId());
        if (plan.mediaFolderNew()) {
            if (Files.isDirectory(staged)) {
                if (Files.exists(mediaDir)) {
                    throw new CannotFinishException("The library folder " + mediaDir.getFileName()
                            + " already exists, and the staged copy was not moved over it.");
                }
                Files.move(staged, mediaDir, StandardCopyOption.ATOMIC_MOVE);
            }
            return;
        }
        Path backup = paths.backup(jobId);
        boolean first = true;
        for (String folder : plan.folders()) {
            Path from = staged.resolve(folder);
            Path to = mediaDir.resolve(folder);
            if (!Files.isDirectory(from)) {
                // Already moved by an earlier attempt; verifyComplete checks it is there.
                continue;
            }
            if (plan.replaced().contains(folder)) {
                Path old = backup.resolve(folder);
                if (Files.exists(to)) {
                    if (Files.exists(old)) {
                        throw new CannotFinishException("Chapter folder " + folder
                                + " exists both in the library and in the replace backup.");
                    }
                    Files.createDirectories(backup);
                    Files.move(to, old, StandardCopyOption.ATOMIC_MOVE);
                }
                faults.checkpoint(Step.MID_REPLACE_SWAP);
            } else if (Files.exists(to)) {
                throw new CannotFinishException("Chapter folder " + folder + " already exists in the library folder.");
            }
            Files.createDirectories(mediaDir);
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
            if (first) {
                first = false;
                faults.checkpoint(Step.MID_MOVE);
            }
        }
        FileSystemUtils.deleteRecursively(backup);
    }

    /** Every chapter row has its folder and page files in the library. */
    private void verifyComplete(UUID mediaId, MediaFiles files) throws CannotFinishException {
        Path mediaDir = paths.mediaDir(mediaId);
        if (!Files.isDirectory(mediaDir)) {
            throw new CannotFinishException("The library folder of media " + mediaId
                    + " is missing and there is no staged copy to move into place.");
        }
        for (Chapter c : files.chapters()) {
            for (PageEntry p : c.getPages()) {
                if (!Files.isRegularFile(mediaDir.resolve(c.getFolder()).resolve(p.f()))) {
                    throw new CannotFinishException("Chapter " + c.getFolder() + " is incomplete in the library (missing "
                            + p.f() + ") and there is no staged copy to finish it from.");
                }
            }
        }
        if (files.coverFile() != null && !Files.isRegularFile(mediaDir.resolve(files.coverFile()))) {
            throw new CannotFinishException("The cover " + files.coverFile() + " is missing from the library folder.");
        }
    }

    // --- step 6 ---------------------------------------------------------------------------------

    private void removeSource(ImportJob job, List<String> warnings) {
        Path source = paths.source(job.getSourceFolder());
        Path imported = paths.imported(job.getSourceFolder(), job.getId());
        try {
            if (Files.exists(source) && !Files.exists(imported)) {
                Files.move(source, imported, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException e) {
            log.warn("Could not rename the import folder {}: {}", source, e.toString());
            warnings.add("The import folder could not be renamed or removed (" + e.getMessage()
                    + "). Delete it by hand; it would be imported again as an update.");
            return;
        }
        try {
            FileSystemUtils.deleteRecursively(imported);
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", imported, e.toString());
            warnings.add("The imported folder " + imported.getFileName()
                    + " could not be deleted; it is ignored and removed on the next start.");
        }
    }
}
