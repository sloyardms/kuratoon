package dev.kuratoon.character;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response bodies of the character admin API. */
public final class CharacterDtos {

    private CharacterDtos() {}

    /** @param parodyId optional */
    public record CharacterRequest(@NotBlank @Size(max = 200) String name, UUID parodyId) {}

    public record ParodyRef(UUID id, String name) {}

    /** @param parody null when the character has no parody */
    public record CharacterResponse(UUID id, String name, ParodyRef parody, Instant createdAt) {
        public static CharacterResponse of(StoryCharacter c) {
            ParodyRef parody = c.getParody() == null
                    ? null
                    : new ParodyRef(c.getParody().getId(), c.getParody().getName());
            return new CharacterResponse(c.getId(), c.getName(), parody, c.getCreatedAt());
        }
    }
}
