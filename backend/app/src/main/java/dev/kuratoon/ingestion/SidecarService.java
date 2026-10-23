package dev.kuratoon.ingestion;

import dev.kuratoon.chapter.Chapter;
import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.ingestion.KuratoonYaml.Cover;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotChapter;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotDocument;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotRelation;
import dev.kuratoon.media.Media;
import dev.kuratoon.media.MediaMetadataWriter;
import dev.kuratoon.media.MediaRelationRepository;
import dev.kuratoon.media.MediaRepository;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes {@code /library/<mediaId>/kuratoon.yml} (kind: snapshot) from the database
 * (docs/spec/02-ingestion.md "Sidecar snapshot"). Imports write it directly; metadata edits
 * schedule a write debounced by {@link #DEBOUNCE}. Pending writes live in memory, so startup
 * rewrites every sidecar older than its media row ({@link #rewriteStale()}).
 */
@Service
public class SidecarService {

    private static final Logger log = LoggerFactory.getLogger(SidecarService.class);

    static final Duration DEBOUNCE = Duration.ofSeconds(5);

    private final MediaRepository media;
    private final ChapterRepository chapters;
    private final MediaRelationRepository relations;
    private final MediaMetadataWriter metadata;
    private final LibraryPaths paths;
    private final TransactionTemplate readOnly;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("sidecar-writer").daemon().factory());
    private final Map<UUID, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public SidecarService(
            MediaRepository media,
            ChapterRepository chapters,
            MediaRelationRepository relations,
            MediaMetadataWriter metadata,
            LibraryPaths paths,
            TransactionTemplate tx) {
        this.media = media;
        this.chapters = chapters;
        this.relations = relations;
        this.metadata = metadata;
        this.paths = paths;
        this.readOnly = new TransactionTemplate(tx.getTransactionManager());
        this.readOnly.setReadOnly(true);
    }

    public Path sidecar(UUID mediaId) {
        return paths.mediaDir(mediaId).resolve(KuratoonYaml.FILE_NAME);
    }

    /** Writes the sidecar now (atomic: tmp file, fsync, rename). No-op for a deleted media. */
    public void write(UUID mediaId) throws IOException {
        Optional<String> yaml = readOnly.execute(s -> media.findById(mediaId).map(this::snapshot).map(KuratoonYaml::write));
        if (yaml == null || yaml.isEmpty()) {
            return;
        }
        Path dir = paths.mediaDir(mediaId);
        if (!Files.isDirectory(dir)) {
            throw new NoSuchFileException(dir.toString(), null, "library folder missing");
        }
        Path tmp = dir.resolve(KuratoonYaml.FILE_NAME + ".tmp");
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            ByteBuffer bytes = ByteBuffer.wrap(yaml.get().getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) {
                ch.write(bytes);
            }
            ch.force(true);
        }
        Files.move(tmp, sidecar(mediaId), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Writes the sidecar {@link #DEBOUNCE} after the current transaction commits; further calls in
     * that window restart it.
     */
    public void scheduleWrite(UUID mediaId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule(mediaId);
                }
            });
        } else {
            schedule(mediaId);
        }
    }

    private void schedule(UUID mediaId) {
        pending.compute(mediaId, (id, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            return scheduler.schedule(() -> writePending(id), DEBOUNCE.toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    private void writePending(UUID mediaId) {
        pending.remove(mediaId);
        try {
            write(mediaId);
        } catch (Exception e) {
            log.error("Could not write the sidecar of media {}", mediaId, e);
        }
    }

    /** Writes every pending sidecar now (shutdown, tests). */
    public void flushPending() {
        for (UUID id : List.copyOf(pending.keySet())) {
            ScheduledFuture<?> future = pending.remove(id);
            if (future != null && future.cancel(false)) {
                writePending(id);
            }
        }
    }

    public boolean hasPending(UUID mediaId) {
        return pending.containsKey(mediaId);
    }

    @PreDestroy
    void shutdown() {
        flushPending();
        scheduler.shutdownNow();
    }

    /** Rewrites sidecars that are missing or older than their media's {@code updated_at}. */
    public int rewriteStale() {
        int rewritten = 0;
        for (UUID id : media.findAllIds()) {
            Instant updatedAt = readOnly.execute(s -> media.findById(id).map(Media::getUpdatedAt).orElse(null));
            if (updatedAt == null || !Files.isDirectory(paths.mediaDir(id))) {
                continue;
            }
            Path file = sidecar(id);
            try {
                if (!Files.isRegularFile(file) || Files.getLastModifiedTime(file).toInstant().isBefore(updatedAt)) {
                    write(id);
                    rewritten++;
                }
            } catch (IOException e) {
                log.error("Could not rewrite the sidecar of media {}", id, e);
            }
        }
        if (rewritten > 0) {
            log.info("Rewrote {} stale sidecar(s)", rewritten);
        }
        return rewritten;
    }

    /** The snapshot for a media; no per-user data. */
    SnapshotDocument snapshot(Media m) {
        List<SnapshotChapter> chapterList = chapters.findByMediaIdOrdered(m.getId()).stream()
                .sorted(Comparator.comparing(Chapter::getNumber))
                .map(c -> new SnapshotChapter(c.getNumber().stripTrailingZeros(), c.getTitle(), c.getFolder(),
                        c.getCreatedAt(), List.copyOf(c.getPages())))
                .toList();
        List<SnapshotRelation> relationList = relations.findInvolving(m.getId()).stream()
                .filter(r -> r.getFrom().getId().equals(m.getId()))
                .map(r -> new SnapshotRelation(r.getType(), r.getTo().getId()))
                .sorted(Comparator.comparing((SnapshotRelation r) -> r.mediaId()))
                .toList();
        Cover cover = m.getCoverFile() == null ? null : new Cover(m.getCoverFile(), m.getCoverCrop());
        return new SnapshotDocument(m.getId(), m.getSourceUrl(), m.getPluginId(), metadata.read(m), m.getLockedFields(),
                cover, m.getCreatedAt(), chapterList, relationList, List.of());
    }
}
