package dev.kuratoon.ingestion;

import dev.kuratoon.media.PublicationStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Media metadata as it appears in a manifest, a snapshot or the manual metadata form. Every field
 * is nullable (unknown); lists are never null. Field names are the manifest keys, which are also
 * the names stored in {@code media.locked_fields}.
 *
 * @param type media type slug
 * @param tags LIBRARY tag names
 */
public record MediaFields(
        String title,
        List<String> titles,
        String description,
        String type,
        String language,
        PublicationStatus status,
        Integer year,
        Boolean adult,
        List<String> authors,
        List<String> artists,
        List<String> genres,
        List<String> tags,
        List<String> parodies,
        List<CharacterEntry> characters) {

    /** Lockable field names, in manifest order. */
    public static final List<String> FIELD_NAMES = List.of(
            "title", "titles", "description", "type", "language", "status", "year", "adult",
            "authors", "artists", "genres", "tags", "parodies", "characters");

    public static final MediaFields EMPTY =
            new MediaFields(null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    /** @param parody parody name, or null */
    public record CharacterEntry(String name, String parody) {
        public CharacterEntry {
            name = clean(name);
            parody = clean(parody);
        }
    }

    public MediaFields {
        title = clean(title);
        description = description == null || description.isBlank() ? null : description.strip();
        type = clean(type);
        language = clean(language);
        titles = names(titles);
        authors = names(authors);
        artists = names(artists);
        genres = names(genres);
        tags = names(tags);
        parodies = names(parodies);
        characters = characters == null
                ? List.of()
                : characters.stream().filter(Objects::nonNull).filter(c -> c.name() != null).distinct().toList();
    }

    /** True when a NEW media can be created without a plugin (docs/spec/02-ingestion.md "Manifest"). */
    public boolean isComplete() {
        return title != null && type != null && (!authors.isEmpty() || !artists.isEmpty());
    }

    /** Field names that are missing for {@link #isComplete()}. */
    public List<String> missingForCreate() {
        List<String> missing = new ArrayList<>();
        if (title == null) {
            missing.add("title");
        }
        if (type == null) {
            missing.add("type");
        }
        if (authors.isEmpty() && artists.isEmpty()) {
            missing.add("authors or artists");
        }
        return missing;
    }

    /** These fields, with each null/empty one taken from {@code fallback}. */
    public MediaFields orElse(MediaFields fallback) {
        return new MediaFields(
                title != null ? title : fallback.title,
                !titles.isEmpty() ? titles : fallback.titles,
                description != null ? description : fallback.description,
                type != null ? type : fallback.type,
                language != null ? language : fallback.language,
                status != null ? status : fallback.status,
                year != null ? year : fallback.year,
                adult != null ? adult : fallback.adult,
                !authors.isEmpty() ? authors : fallback.authors,
                !artists.isEmpty() ? artists : fallback.artists,
                !genres.isEmpty() ? genres : fallback.genres,
                !tags.isEmpty() ? tags : fallback.tags,
                !parodies.isEmpty() ? parodies : fallback.parodies,
                !characters.isEmpty() ? characters : fallback.characters);
    }

    public MediaFields withTitle(String newTitle) {
        return new MediaFields(newTitle, titles, description, type, language, status, year, adult, authors, artists,
                genres, tags, parodies, characters);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Stripped, blanks dropped, duplicates removed (first one wins), order kept. */
    private static List<String> names(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String v : values) {
            String c = clean(v);
            if (c != null) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }
}
