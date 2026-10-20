package dev.kuratoon.creator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response bodies of the creator admin API. */
public final class CreatorDtos {

    private CreatorDtos() {}

    public record CreatorRequest(@NotBlank @Size(max = 200) String name) {}

    public record CreatorResponse(UUID id, String name, Instant createdAt) {
        public static CreatorResponse of(Creator c) {
            return new CreatorResponse(c.getId(), c.getName(), c.getCreatedAt());
        }
    }
}
