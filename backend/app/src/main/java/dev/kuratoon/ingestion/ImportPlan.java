package dev.kuratoon.ingestion;

import dev.kuratoon.ingestion.KuratoonYaml.SnapshotDocument;
import dev.kuratoon.media.CoverCrop;
import dev.kuratoon.media.MediaFields;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What PROCESSING will do, decided in RESOLVING from the import folder and the database. Kept in
 * memory only: the worker resolves and processes a job in one go.
 *
 * @param targetMediaId UPDATE target, or the snapshot id for RESTORE; null for NEW
 * @param fields metadata for NEW and RESTORE; unused for UPDATE
 * @param lockFields NEW: fields to lock (manifest and manual entries)
 * @param cover NEW and RESTORE; null for UPDATE
 * @param snapshot RESTORE only
 */
public record ImportPlan(
        ImportKind kind,
        UUID targetMediaId,
        MediaFields fields,
        Set<String> lockFields,
        String sourceUrl,
        String pluginId,
        List<PlannedChapter> chapters,
        PlannedCover cover,
        SnapshotDocument snapshot) {

    /**
     * @param replaces the existing chapter whose pages this replaces (UPDATE "Replace"), or null
     * @param createdAt the sidecar date on RESTORE; null means "now"
     */
    public record PlannedChapter(
            BigDecimal number, String title, String folder, List<PlannedPage> pages, UUID replaces, Instant createdAt) {}

    /** @param target library name ({@code 0001.jpg}) */
    public record PlannedPage(Path source, String target, int width, int height) {}

    /** @param target library name ({@code cover.<ext>}) */
    public record PlannedCover(Path source, String target, CoverCrop crop) {}
}
