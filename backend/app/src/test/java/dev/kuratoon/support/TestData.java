package dev.kuratoon.support;

import dev.kuratoon.chapter.Chapter;
import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.chapter.PageEntry;
import dev.kuratoon.character.StoryCharacter;
import dev.kuratoon.character.StoryCharacterRepository;
import dev.kuratoon.creator.Creator;
import dev.kuratoon.creator.CreatorRepository;
import dev.kuratoon.media.CreatorRole;
import dev.kuratoon.media.Media;
import dev.kuratoon.media.MediaRelation;
import dev.kuratoon.media.MediaRelationRepository;
import dev.kuratoon.media.MediaRepository;
import dev.kuratoon.media.RelationType;
import dev.kuratoon.mediatype.MediaType;
import dev.kuratoon.mediatype.MediaTypeRepository;
import dev.kuratoon.parody.Parody;
import dev.kuratoon.parody.ParodyRepository;
import dev.kuratoon.tag.Tag;
import dev.kuratoon.tag.TagKind;
import dev.kuratoon.tag.TagRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.support.TransactionTemplate;

/** Test data builders that persist through the real repositories. */
@TestComponent
public class TestData {

    private final MediaTypeRepository mediaTypes;
    private final MediaRepository media;
    private final ChapterRepository chapters;
    private final CreatorRepository creators;
    private final TagRepository tags;
    private final ParodyRepository parodies;
    private final StoryCharacterRepository characters;
    private final MediaRelationRepository relations;
    private final TransactionTemplate tx;

    public TestData(
            MediaTypeRepository mediaTypes,
            MediaRepository media,
            ChapterRepository chapters,
            CreatorRepository creators,
            TagRepository tags,
            ParodyRepository parodies,
            StoryCharacterRepository characters,
            MediaRelationRepository relations,
            TransactionTemplate tx) {
        this.mediaTypes = mediaTypes;
        this.media = media;
        this.chapters = chapters;
        this.creators = creators;
        this.tags = tags;
        this.parodies = parodies;
        this.characters = characters;
        this.relations = relations;
        this.tx = tx;
    }

    public MediaType mediaType(String slug) {
        return mediaTypes.findBySlug(slug).orElseThrow();
    }

    public MediaType aMediaType(String name, String slug) {
        return mediaTypes.save(new MediaType(name, slug, "#123456", 99));
    }

    public Creator aCreator(String name) {
        return creators.save(new Creator(name));
    }

    public Tag aTag(String name, String slug, TagKind kind) {
        return tags.save(new Tag(name, slug, kind));
    }

    public Parody aParody(String name) {
        return parodies.save(new Parody(name));
    }

    public StoryCharacter aCharacter(String name, Parody parody) {
        return characters.save(new StoryCharacter(name, parody));
    }

    public MediaRelation aRelation(Media from, Media to, RelationType type) {
        return tx.execute(status -> relations.save(
                new MediaRelation(media.getReferenceById(from.getId()), media.getReferenceById(to.getId()), type)));
    }

    public MediaBuilder aMedia(String title) {
        return new MediaBuilder(title);
    }

    /**
     * Builds and persists a media. Adds one AUTHOR by default so the "at least one AUTHOR or ARTIST"
     * invariant (enforced by the phase 3 write services) holds; adding any creator or calling
     * {@link #withoutDefaultAuthor()} replaces the default.
     */
    public final class MediaBuilder {
        private final String title;
        private String typeSlug = "manga";
        private MediaType type;
        private String language = "en";
        private boolean defaultAuthor = true;
        private final List<Consumer<Media>> steps = new ArrayList<>();
        private final List<String> chapterNumbers = new ArrayList<>();

        private MediaBuilder(String title) {
            this.title = title;
        }

        public MediaBuilder type(String slug) {
            this.typeSlug = slug;
            return this;
        }

        public MediaBuilder type(MediaType mediaType) {
            this.type = mediaType;
            return this;
        }

        public MediaBuilder language(String language) {
            this.language = language;
            return this;
        }

        public MediaBuilder withoutDefaultAuthor() {
            this.defaultAuthor = false;
            return this;
        }

        public MediaBuilder altTitle(String altTitle) {
            steps.add(m -> m.addAlternativeTitle(altTitle, null));
            return this;
        }

        public MediaBuilder author(Creator creator) {
            defaultAuthor = false;
            steps.add(m -> m.addCreator(creator, CreatorRole.AUTHOR));
            return this;
        }

        public MediaBuilder artist(Creator creator) {
            defaultAuthor = false;
            steps.add(m -> m.addCreator(creator, CreatorRole.ARTIST));
            return this;
        }

        public MediaBuilder tag(Tag tag) {
            steps.add(m -> m.addTag(tag));
            return this;
        }

        public MediaBuilder parody(Parody parody) {
            steps.add(m -> m.addParody(parody));
            return this;
        }

        public MediaBuilder character(StoryCharacter character) {
            steps.add(m -> m.addCharacter(character));
            return this;
        }

        public MediaBuilder with(Consumer<Media> step) {
            steps.add(step);
            return this;
        }

        /** Chapter numbers such as "0", "1", "12.5"; each chapter gets 3 pages. */
        public MediaBuilder chapters(String... numbers) {
            chapterNumbers.addAll(List.of(numbers));
            return this;
        }

        public Media persist() {
            return tx.execute(status -> {
                Media m = new Media(title, type != null ? type : mediaType(typeSlug), language);
                if (defaultAuthor) {
                    m.addCreator(aCreator("Author of " + title), CreatorRole.AUTHOR);
                }
                steps.forEach(step -> step.accept(m));
                media.save(m);
                for (String number : chapterNumbers) {
                    List<PageEntry> pages = List.of(
                            new PageEntry("001.jpg", 800, 1200),
                            new PageEntry("002.jpg", 800, 1200),
                            new PageEntry("003.jpg", 800, 1200));
                    chapters.save(new Chapter(m, new BigDecimal(number), null, "Chapter " + number, pages));
                }
                return m;
            });
        }
    }
}
