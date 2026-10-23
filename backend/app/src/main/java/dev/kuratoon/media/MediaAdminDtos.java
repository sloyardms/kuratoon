package dev.kuratoon.media;

import dev.kuratoon.media.MediaReadDtos.ChapterSummary;
import dev.kuratoon.media.MediaReadDtos.MediaTypeRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin media list and edit page (docs/spec/02-ingestion.md "Admin maintenance actions"). */
public final class MediaAdminDtos {

    private MediaAdminDtos() {}

    public record MediaRow(
            UUID id, String title, MediaTypeRef type, int chapterCount, Instant latestChapterAddedAt, Instant createdAt) {}

    /**
     * @param width/height of the original cover, for the crop tool; 0 when unreadable
     * @param originalUrl the original for the admin crop tool (versioned), null without a cover
     */
    public record CoverView(
            String file, CoverCrop crop, int version, int width, int height, boolean thumbnails, String originalUrl) {}

    /**
     * @param direction OUTGOING: stored on this media ({@code mediaId} is a {@code type} of this);
     *     INCOMING: stored on the other media (read-only here)
     */
    public record RelationView(UUID id, RelationType type, String direction, RelationLabel label, UUID mediaId, String title) {}

    public record MediaEditView(
            UUID id,
            MediaFields values,
            List<String> lockedFields,
            String sourceUrl,
            String pluginId,
            CoverView cover,
            List<RelationView> relations,
            List<ChapterSummary> chapters,
            Instant createdAt,
            Instant updatedAt) {}

    /**
     * @param values new values; only the fields named in {@code fields} are applied
     * @param fields edited field names (MediaFields.FIELD_NAMES); each one becomes locked
     */
    public record MediaUpdateRequest(@NotNull MediaFields values, @NotEmpty List<String> fields) {}

    public record LockedFieldsRequest(@NotNull List<String> fields) {}

    public record RelationRequest(@NotNull RelationType type, @NotNull UUID mediaId) {}

    /** The relations stored on this media (OUTGOING); replaces them all. */
    public record RelationsRequest(@NotNull List<@Valid RelationRequest> relations) {}

    /** @param crop normalized 0–1; null removes the crop (centered default) */
    public record CoverCropRequest(CoverCrop crop) {}

    /** @param warning set when the media was saved but thumbnails could not be regenerated */
    public record CoverResult(CoverView cover, String warning) {}
}
