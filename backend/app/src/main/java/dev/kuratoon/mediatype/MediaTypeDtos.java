package dev.kuratoon.mediatype;

import dev.kuratoon.common.Slugs;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response bodies of the media type admin API. */
public final class MediaTypeDtos {

    private MediaTypeDtos() {}

    /**
     * @param slug optional; derived from the name when absent
     * @param sortOrder optional; 0 when absent
     */
    public record MediaTypeRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) @Pattern(regexp = Slugs.PATTERN, message = "must be lowercase letters, digits and single hyphens")
                    String slug,
            @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex color like #1a2b3c") String color,
            Integer sortOrder) {}

    public record MediaTypeResponse(UUID id, String name, String slug, String color, int sortOrder, Instant createdAt) {
        static MediaTypeResponse of(MediaType t) {
            return new MediaTypeResponse(t.getId(), t.getName(), t.getSlug(), t.getColor(), t.getSortOrder(),
                    t.getCreatedAt());
        }
    }
}
