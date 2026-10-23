package dev.kuratoon.media;

import dev.kuratoon.character.StoryCharacter;
import dev.kuratoon.character.StoryCharacterRepository;
import dev.kuratoon.common.BadRequestException;
import dev.kuratoon.common.Names;
import dev.kuratoon.common.Slugs;
import dev.kuratoon.creator.Creator;
import dev.kuratoon.creator.CreatorRepository;
import dev.kuratoon.media.MediaFields.CharacterEntry;
import dev.kuratoon.mediatype.MediaType;
import dev.kuratoon.mediatype.MediaTypeRepository;
import dev.kuratoon.parody.Parody;
import dev.kuratoon.parody.ParodyRepository;
import dev.kuratoon.tag.Tag;
import dev.kuratoon.tag.TagKind;
import dev.kuratoon.tag.TagRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies {@link MediaFields} to a media and reads them back. Shared by ingestion (NEW, RESTORE),
 * manual metadata entry and the media edit page, so every path resolves names the same way:
 * creators, parodies and characters by normalized name, tags by kind and slug, created if missing
 * (docs/spec/03-plugins-fetching.md "Mapping rules").
 */
@Service
public class MediaMetadataWriter {

    private final MediaTypeRepository mediaTypes;
    private final CreatorRepository creators;
    private final TagRepository tags;
    private final ParodyRepository parodies;
    private final StoryCharacterRepository characters;

    public MediaMetadataWriter(
            MediaTypeRepository mediaTypes,
            CreatorRepository creators,
            TagRepository tags,
            ParodyRepository parodies,
            StoryCharacterRepository characters) {
        this.mediaTypes = mediaTypes;
        this.creators = creators;
        this.tags = tags;
        this.parodies = parodies;
        this.characters = characters;
    }

    /** A media type by slug or name, case-insensitively. */
    public Optional<MediaType> findType(String slugOrName) {
        if (slugOrName == null || slugOrName.isBlank()) {
            return Optional.empty();
        }
        String wanted = slugOrName.strip().toLowerCase(Locale.ROOT);
        return mediaTypes.findBySlug(wanted).or(() -> mediaTypes.findAll().stream()
                .filter(t -> t.getName().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst());
    }

    /**
     * Writes the named fields from {@code values} onto the media. A listed field with a null or
     * empty value clears it, except title, type and language, which are required and keep their
     * value.
     *
     * @throws BadRequestException for an unknown media type
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Media media, MediaFields values, Collection<String> fields) {
        Set<String> f = new LinkedHashSet<>(fields);
        if (f.contains("title") && values.title() != null) {
            media.setTitle(values.title());
        }
        if (f.contains("type") && values.type() != null) {
            media.setMediaType(findType(values.type())
                    .orElseThrow(() -> new BadRequestException("Unknown media type '" + values.type() + "'.")));
        }
        if (f.contains("language") && values.language() != null) {
            media.setLanguage(values.language());
        }
        if (f.contains("description")) {
            media.setDescription(values.description());
        }
        if (f.contains("status")) {
            media.setPublicationStatus(values.status() == null ? PublicationStatus.UNKNOWN : values.status());
        }
        if (f.contains("year")) {
            media.setPublicationYear(values.year() == null ? null : values.year().shortValue());
        }
        if (f.contains("adult")) {
            media.setAdult(Boolean.TRUE.equals(values.adult()));
        }
        if (f.contains("titles") || f.contains("title")) {
            media.replaceAlternativeTitles(f.contains("titles") ? values.titles() : read(media).titles());
        }
        if (f.contains("authors")) {
            media.replaceCreators(CreatorRole.AUTHOR, values.authors().stream().map(this::creator).toList());
        }
        if (f.contains("artists")) {
            media.replaceCreators(CreatorRole.ARTIST, values.artists().stream().map(this::creator).toList());
        }
        if (f.contains("genres")) {
            media.replaceTags(TagKind.GENRE, values.genres().stream().map(n -> tag(TagKind.GENRE, n)).toList());
        }
        if (f.contains("tags")) {
            media.replaceTags(TagKind.LIBRARY, values.tags().stream().map(n -> tag(TagKind.LIBRARY, n)).toList());
        }
        if (f.contains("parodies")) {
            media.replaceParodies(values.parodies().stream().map(this::parody).toList());
        }
        if (f.contains("characters")) {
            media.replaceCharacters(values.characters().stream().map(this::character).toList());
        }
    }

    /** The media's current values; lists sorted by name for a stable sidecar. */
    public MediaFields read(Media media) {
        return new MediaFields(
                media.getTitle(),
                media.getAlternativeTitles().stream().map(MediaTitle::getTitle).sorted().toList(),
                media.getDescription(),
                media.getMediaType().getSlug(),
                media.getLanguage(),
                media.getPublicationStatus(),
                media.getPublicationYear() == null ? null : media.getPublicationYear().intValue(),
                media.isAdult(),
                creatorNames(media, CreatorRole.AUTHOR),
                creatorNames(media, CreatorRole.ARTIST),
                tagNames(media, TagKind.GENRE),
                tagNames(media, TagKind.LIBRARY),
                media.getParodies().stream().map(Parody::getName).sorted().toList(),
                media.getCharacters().stream()
                        .map(c -> new CharacterEntry(c.getName(), c.getParody() == null ? null : c.getParody().getName()))
                        .sorted(Comparator.comparing(CharacterEntry::name))
                        .toList());
    }

    private static List<String> creatorNames(Media media, CreatorRole role) {
        List<String> names = new ArrayList<>();
        media.getCreators().stream().filter(c -> c.getRole() == role).forEach(c -> names.add(c.getCreator().getName()));
        names.sort(null);
        return names;
    }

    private static List<String> tagNames(Media media, TagKind kind) {
        return media.getTags().stream().filter(t -> t.getKind() == kind).map(Tag::getName).sorted().toList();
    }

    private Creator creator(String name) {
        return creators.findByNormalizedName(Names.normalize(name)).orElseGet(() -> creators.save(new Creator(name)));
    }

    private Parody parody(String name) {
        return parodies.findByNormalizedName(Names.normalize(name)).orElseGet(() -> parodies.save(new Parody(name)));
    }

    private StoryCharacter character(CharacterEntry entry) {
        Parody parody = entry.parody() == null ? null : parody(entry.parody());
        return characters.findByNameAndParody(Names.normalize(entry.name()), parody == null ? null : parody.getId())
                .orElseGet(() -> characters.save(new StoryCharacter(entry.name(), parody)));
    }

    private Tag tag(TagKind kind, String name) {
        String slug = tagSlug(name);
        return tags.findByKindAndSlug(kind, slug).orElseGet(() -> tags.save(new Tag(name.strip(), slug, kind)));
    }

    /** Slug for a scraped/manifest tag name; non-Latin names get a stable hash-based slug. */
    static String tagSlug(String name) {
        String slug = Slugs.slugify(name);
        return slug.isEmpty() ? "t-" + Integer.toHexString(Names.normalize(name).hashCode()) : slug;
    }
}
