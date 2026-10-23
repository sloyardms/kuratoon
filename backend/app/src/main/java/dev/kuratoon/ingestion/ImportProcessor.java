package dev.kuratoon.ingestion;

import dev.kuratoon.image.ImageProbe;
import dev.kuratoon.image.ImageProbe.ImageInfo;
import dev.kuratoon.image.ImageProbe.UnreadableImageException;
import dev.kuratoon.ingestion.ImportFaults.Step;
import dev.kuratoon.ingestion.ImportPlan.PlannedChapter;
import dev.kuratoon.ingestion.ImportPlan.PlannedPage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

/**
 * PROCESSING, commit steps 1–2: copy the import into {@code /library/.staging/<jobId>/} with the
 * library names, then validate the copies. Steps 3–6 are {@link ImportCommitter} and
 * {@link ImportFinisher}.
 */
@Service
public class ImportProcessor {

    /** A staged copy does not match its source; nothing was committed. */
    public static class StagingException extends Exception {
        public StagingException(String message) {
            super(message);
        }
    }

    private final LibraryPaths paths;
    private final ImportFaults faults;

    public ImportProcessor(LibraryPaths paths, ImportFaults faults) {
        this.paths = paths;
        this.faults = faults;
    }

    /** Steps 1–2. On any failure the staging dir is removed again. */
    public void stage(UUID jobId, ImportPlan plan) throws IOException, StagingException {
        Path staging = paths.staging(jobId);
        FileSystemUtils.deleteRecursively(staging);
        Path media = paths.stagedMedia(jobId);
        try {
            Files.createDirectories(media);
            for (PlannedChapter c : plan.chapters()) {
                Path dir = Files.createDirectories(media.resolve(c.folder()));
                for (PlannedPage p : c.pages()) {
                    Files.copy(p.source(), dir.resolve(p.target()), StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            if (plan.cover() != null) {
                Files.copy(plan.cover().source(), media.resolve(plan.cover().target()), StandardCopyOption.REPLACE_EXISTING);
            }
            faults.checkpoint(Step.AFTER_COPY);
            validate(media, plan);
            faults.checkpoint(Step.AFTER_VALIDATE);
        } catch (IOException | StagingException | RuntimeException e) {
            FileSystemUtils.deleteRecursively(staging);
            throw e;
        }
    }

    /** Each staged page has the source's size and the dimensions read during resolution. */
    private static void validate(Path media, ImportPlan plan) throws IOException, StagingException {
        for (PlannedChapter c : plan.chapters()) {
            for (PlannedPage p : c.pages()) {
                Path copy = media.resolve(c.folder()).resolve(p.target());
                if (Files.size(copy) != Files.size(p.source())) {
                    throw new StagingException("Copy of " + p.source().getFileName() + " is incomplete.");
                }
                ImageInfo info;
                try {
                    info = ImageProbe.probe(copy);
                } catch (UnreadableImageException e) {
                    throw new StagingException("Unreadable image " + c.folder() + "/" + p.target() + ": " + e.getMessage() + ".");
                }
                if (info.width() != p.width() || info.height() != p.height()) {
                    throw new StagingException("Image " + p.source().getFileName() + " changed while importing.");
                }
            }
        }
    }
}
