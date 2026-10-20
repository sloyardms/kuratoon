package dev.kuratoon.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaMetadataTest {

    @Test
    void titleIsRequired() {
        assertThatThrownBy(() -> titleOnly(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> titleOnly("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullListsBecomeEmptyAndListsAreImmutableCopies() {
        List<String> genres = new ArrayList<>(List.of("Action"));
        MediaMetadata metadata = new MediaMetadata(
                "Title", null, null, null, null, genres, null, null, null, null, null, null, null, null);
        genres.add("Drama");

        assertThat(metadata.alternativeTitles()).isEmpty();
        assertThat(metadata.characters()).isEmpty();
        assertThat(metadata.genres()).containsExactly("Action");
        assertThatThrownBy(() -> metadata.genres().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static MediaMetadata titleOnly(String title) {
        return new MediaMetadata(title, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
