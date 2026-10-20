package dev.kuratoon.parody;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response bodies of the parody admin API. */
public final class ParodyDtos {

    private ParodyDtos() {}

    public record ParodyRequest(@NotBlank @Size(max = 200) String name) {}

    public record ParodyResponse(UUID id, String name, Instant createdAt) {
        public static ParodyResponse of(Parody p) {
            return new ParodyResponse(p.getId(), p.getName(), p.getCreatedAt());
        }
    }
}
