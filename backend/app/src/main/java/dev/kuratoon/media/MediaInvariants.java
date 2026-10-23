package dev.kuratoon.media;

import dev.kuratoon.common.BadRequestException;

/**
 * Rules on a media that the schema cannot express (docs/spec/01-data-model.md). Checked by every
 * media write path: ingestion commit, manual metadata entry and media edit.
 */
public final class MediaInvariants {

    public static final String AUTHOR_OR_ARTIST_REQUIRED = "At least one author or artist is required.";

    private MediaInvariants() {}

    public static boolean hasAuthorOrArtist(Media media) {
        return media.getCreators().stream()
                .anyMatch(c -> c.getRole() == CreatorRole.AUTHOR || c.getRole() == CreatorRole.ARTIST);
    }

    /** @throws BadRequestException (400) when the media has no AUTHOR or ARTIST */
    public static void requireAuthorOrArtist(Media media) {
        if (!hasAuthorOrArtist(media)) {
            throw new BadRequestException(AUTHOR_OR_ARTIST_REQUIRED);
        }
    }
}
