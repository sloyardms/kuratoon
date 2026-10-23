package dev.kuratoon.ingestion;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.chapter.Chapter;
import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.chapter.PageEntry;
import dev.kuratoon.image.ImageToolException;
import dev.kuratoon.image.ThumbnailService;
import dev.kuratoon.ingestion.ImportResolver.Failed;
import dev.kuratoon.ingestion.ImportResolver.NeedsAttention;
import dev.kuratoon.ingestion.ImportResolver.Outcome;
import dev.kuratoon.ingestion.ImportResolver.Ready;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotDocument;
import dev.kuratoon.media.CoverCrop;
import dev.kuratoon.media.Media;
import dev.kuratoon.media.MediaRepository;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Admin maintenance (docs/spec/02-ingestion.md): integrity scan, thumbnail regeneration and
 * "Rebuild library from disk" (the RESTORE code path for folders already in /library).
 */
@Service
public class LibraryMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(LibraryMaintenanceService.class);

    public record MediaRef(UUID id, String title) {}

    /** @param folder the media folder name; @param title from its sidecar, if readable */
    public record OrphanFolder(String folder, String title, boolean hasSidecar) {}

    public record MissingPages(UUID mediaId, String title, String chapter, int missing, String example) {}

    public record IntegrityReport(
            Instant scannedAt,
            int mediaChecked,
            List<OrphanFolder> foldersWithoutRows,
            List<MediaRef> rowsWithoutFolders,
            List<MissingPages> missingPages,
            List<MediaRef> missingThumbnails) {

        public boolean clean() {
            return foldersWithoutRows.isEmpty() && rowsWithoutFolders.isEmpty() && missingPages.isEmpty()
                    && missingThumbnails.isEmpty();
        }
    }

    public record Skipped(String folder, String reason) {}

    /** @param thumbnailsQueued restored media whose thumbnails are being generated in the background */
    public record RebuildResult(List<MediaRef> restored, List<Skipped> skipped, List<String> warnings, int thumbnailsQueued) {}

    public record RegenerateResult(List<MediaRef> regenerated, List<Skipped> failed) {}

    private record MediaFiles(UUID id, String title, String coverFile, CoverCrop crop, List<Chapter> chapters) {}

    private final MediaRepository media;
    private final ChapterRepository chapters;
    private final ImportResolver resolver;
    private final ImportCommitter committer;
    private final ThumbnailService thumbnails;
    private final LibraryPaths paths;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ExecutorService background =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("thumbnail-regeneration").daemon().factory());

    public LibraryMaintenanceService(
            MediaRepository media,
            ChapterRepository chapters,
            ImportResolver resolver,
            ImportCommitter committer,
            ThumbnailService thumbnails,
            LibraryPaths paths,
            AuditService audit,
            TransactionTemplate tx,
            Clock clock) {
        this.media = media;
        this.chapters = chapters;
        this.resolver = resolver;
        this.committer = committer;
        this.thumbnails = thumbnails;
        this.paths = paths;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    // --- integrity scan -------------------------------------------------------------------------

    public IntegrityReport scan() {
        Map<UUID, MediaFiles> rows = new HashMap<>();
        for (UUID id : media.findAllIds()) {
            MediaFiles f = load(id);
            if (f != null) {
                rows.put(id, f);
            }
        }
        List<OrphanFolder> orphans = new ArrayList<>();
        Set<UUID> folders = new LinkedHashSet<>();
        for (Path dir : mediaFolders()) {
            UUID id = UUID.fromString(dir.getFileName().toString());
            folders.add(id);
            if (!rows.containsKey(id)) {
                Path yml = dir.resolve(KuratoonYaml.FILE_NAME);
                orphans.add(new OrphanFolder(dir.getFileName().toString(), sidecarTitle(yml), Files.isRegularFile(yml)));
            }
        }
        List<MediaRef> withoutFolders = new ArrayList<>();
        List<MissingPages> missingPages = new ArrayList<>();
        List<MediaRef> missingThumbs = new ArrayList<>();
        for (MediaFiles f : rows.values().stream().sorted((a, b) -> a.title().compareToIgnoreCase(b.title())).toList()) {
            MediaRef ref = new MediaRef(f.id(), f.title());
            if (!folders.contains(f.id())) {
                withoutFolders.add(ref);
                continue;
            }
            Path dir = paths.mediaDir(f.id());
            for (Chapter c : f.chapters()) {
                List<String> missing = new ArrayList<>();
                for (PageEntry p : c.getPages()) {
                    if (!Files.isRegularFile(dir.resolve(c.getFolder()).resolve(p.f()))) {
                        missing.add(p.f());
                    }
                }
                if (!missing.isEmpty()) {
                    missingPages.add(new MissingPages(f.id(), f.title(), c.getFolder(), missing.size(), missing.getFirst()));
                }
            }
            if (f.coverFile() != null && !thumbnails.coverThumbsExist(f.id())) {
                missingThumbs.add(ref);
            }
        }
        return new IntegrityReport(clock.instant(), rows.size(), orphans, withoutFolders, missingPages, missingThumbs);
    }

    /** Regenerates cover thumbnails now (idempotent); per media result. */
    public RegenerateResult regenerateThumbnails(Collection<UUID> mediaIds) {
        List<MediaRef> done = new ArrayList<>();
        List<Skipped> failed = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(mediaIds)) {
            MediaFiles f = load(id);
            if (f == null) {
                failed.add(new Skipped(id.toString(), "Media not found."));
                continue;
            }
            if (f.coverFile() == null) {
                failed.add(new Skipped(id.toString(), "No cover."));
                continue;
            }
            try {
                thumbnails.generateCover(id, paths.mediaDir(id).resolve(f.coverFile()), f.crop());
                done.add(new MediaRef(id, f.title()));
            } catch (ImageToolException e) {
                failed.add(new Skipped(f.title(), e.getMessage()));
            }
        }
        tx.executeWithoutResult(s -> audit.record("THUMBNAILS_REGENERATED", "library", null,
                Map.of("count", done.size(), "failed", failed.size())));
        return new RegenerateResult(done, failed);
    }

    // --- rebuild --------------------------------------------------------------------------------

    /**
     * Restores every {@code /library/<id>/} whose sidecar describes a media missing from the
     * database, then the relations between them, then queues their thumbnails.
     */
    public RebuildResult rebuild() {
        Set<UUID> existing = new LinkedHashSet<>(media.findAllIds());
        List<MediaRef> restored = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path dir : mediaFolders()) {
            UUID id = UUID.fromString(dir.getFileName().toString());
            if (existing.contains(id)) {
                continue;
            }
            Outcome outcome = resolver.planRestore(dir).orElse(null);
            String folder = dir.getFileName().toString();
            switch (outcome) {
                case null -> skipped.add(new Skipped(folder, "No snapshot sidecar (kuratoon.yml)."));
                case Failed f -> skipped.add(new Skipped(folder, f.message()));
                case NeedsAttention na -> skipped.add(new Skipped(folder, na.message()));
                case Ready ready -> {
                    try {
                        List<String> w = new ArrayList<>(ready.detail().warnings());
                        committer.restoreFromLibrary(ready.plan(), w);
                        restored.add(new MediaRef(id, ready.plan().fields().title()));
                        w.forEach(x -> warnings.add(ready.plan().fields().title() + ": " + x));
                    } catch (RuntimeException e) {
                        log.warn("Rebuild of {} failed", folder, e);
                        skipped.add(new Skipped(folder, e.getMessage()));
                    }
                }
            }
        }

        // Relations: any sidecar relation that touches a restored media, now that both ends exist.
        Set<UUID> restoredIds = new LinkedHashSet<>();
        restored.forEach(r -> restoredIds.add(r.id()));
        if (!restoredIds.isEmpty()) {
            for (Path dir : mediaFolders()) {
                SnapshotDocument s = readSnapshot(dir.resolve(KuratoonYaml.FILE_NAME));
                if (s == null) {
                    continue;
                }
                var relevant = s.relations().stream()
                        .filter(r -> restoredIds.contains(s.id()) || restoredIds.contains(r.mediaId()))
                        .toList();
                if (!relevant.isEmpty()) {
                    List<String> ignored = new ArrayList<>();
                    committer.restoreRelations(s.id(), relevant, ignored);
                    if (restoredIds.contains(s.id())) {
                        ignored.forEach(x -> warnings.add(s.fields().title() + ": " + x));
                    }
                }
            }
        }

        List<UUID> queue = restored.stream().map(MediaRef::id).filter(id -> !thumbnails.coverThumbsExist(id)).toList();
        if (!queue.isEmpty()) {
            background.execute(() -> regenerateThumbnails(queue));
        }
        tx.executeWithoutResult(st -> audit.record("LIBRARY_REBUILT", "library", null,
                Map.of("restored", restored.size(), "skipped", skipped.size())));
        return new RebuildResult(restored, skipped, warnings, queue.size());
    }

    @PreDestroy
    void shutdown() {
        background.shutdownNow();
    }

    // --- helpers --------------------------------------------------------------------------------

    private MediaFiles load(UUID id) {
        return tx.execute(s -> media.findById(id).map((Media m) -> new MediaFiles(m.getId(), m.getTitle(),
                m.getCoverFile(), m.getCoverCrop(), chapters.findByMediaIdOrdered(id))).orElse(null));
    }

    /** {@code /library/<uuid>/} folders; staging, {@code .deleted-*} and anything else are skipped. */
    private List<Path> mediaFolders() {
        Path root = paths.libraryDir();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(Files::isDirectory).filter(p -> isUuid(p.getFileName().toString())).sorted().toList();
        } catch (IOException e) {
            log.warn("Cannot list {}: {}", root, e.toString());
            return List.of();
        }
    }

    private static boolean isUuid(String name) {
        try {
            return UUID.fromString(name).toString().equals(name);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static SnapshotDocument readSnapshot(Path yml) {
        if (!Files.isRegularFile(yml)) {
            return null;
        }
        try {
            return KuratoonYaml.parse(Files.readString(yml, StandardCharsets.UTF_8)) instanceof SnapshotDocument s ? s : null;
        } catch (IOException | KuratoonYaml.InvalidDocumentException e) {
            return null;
        }
    }

    private static String sidecarTitle(Path yml) {
        SnapshotDocument s = readSnapshot(yml);
        return s == null ? null : s.fields().title();
    }
}
