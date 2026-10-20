package dev.kuratoon.tag;

import dev.kuratoon.common.Slugs;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response bodies of the tag admin API. */
public final class TagDtos {

    private TagDtos() {}

    /** @param slug optional; derived from the name when absent */
    public record TagRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) @Pattern(regexp = Slugs.PATTERN, message = "must be lowercase letters, digits and single hyphens")
                    String slug,
            @NotNull TagKind kind) {}

    public record TagResponse(UUID id, String name, String slug, TagKind kind, Instant createdAt) {
        public static TagResponse of(Tag t) {
            return new TagResponse(t.getId(), t.getName(), t.getSlug(), t.getKind(), t.getCreatedAt());
        }
    }
}
