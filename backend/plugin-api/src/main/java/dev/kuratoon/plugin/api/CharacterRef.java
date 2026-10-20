package dev.kuratoon.plugin.api;

import java.util.Objects;

/**
 * A character with the parody (source work) it belongs to.
 *
 * @param parody nullable
 */
public record CharacterRef(String name, String parody) {
    public CharacterRef {
        Objects.requireNonNull(name, "name is required");
    }
}
