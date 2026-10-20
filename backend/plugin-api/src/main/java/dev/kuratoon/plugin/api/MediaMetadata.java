package dev.kuratoon.plugin.api;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Metadata parsed from one page. Every field except {@code title} is nullable; list fields are
 * never null (null becomes an empty list).
 *
 * @param type free-text hint (e.g. "Manhwa") matched against media types by the core
 */
public record MediaMetadata(
        String title,
        List<String> alternativeTitles,
        String description,
        List<String> authors,
        List<String> artists,
        List<String> genres,
        String type,
        String language,
        PublicationStatus status,
        Integer year,
        Boolean adult,
        URI coverUrl,
        List<String> parodies,
        List<CharacterRef> characters) {

    public MediaMetadata {
        Objects.requireNonNull(title, "title is required");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        alternativeTitles = copy(alternativeTitles);
        authors = copy(authors);
        artists = copy(artists);
        genres = copy(genres);
        parodies = copy(parodies);
        characters = copy(characters);
    }

    private static <T> List<T> copy(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
