package dev.kuratoon.media;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kuratoon.common.BadRequestException;
import dev.kuratoon.creator.Creator;
import dev.kuratoon.mediatype.MediaType;
import org.junit.jupiter.api.Test;

class MediaInvariantsTest {

    private final MediaType manga = new MediaType("Manga", "manga", "#000000", 0);

    @Test
    void mediaWithoutCreatorsIsRejected() {
        Media media = new Media("T", manga, "en");
        assertThatThrownBy(() -> MediaInvariants.requireAuthorOrArtist(media))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(MediaInvariants.AUTHOR_OR_ARTIST_REQUIRED);
    }

    @Test
    void anAuthorAloneIsEnough() {
        Media media = new Media("T", manga, "en");
        media.addCreator(new Creator("A"), CreatorRole.AUTHOR);
        assertThatCode(() -> MediaInvariants.requireAuthorOrArtist(media)).doesNotThrowAnyException();
    }

    @Test
    void anArtistAloneIsEnough() {
        Media media = new Media("T", manga, "en");
        media.addCreator(new Creator("B"), CreatorRole.ARTIST);
        assertThatCode(() -> MediaInvariants.requireAuthorOrArtist(media)).doesNotThrowAnyException();
    }
}
