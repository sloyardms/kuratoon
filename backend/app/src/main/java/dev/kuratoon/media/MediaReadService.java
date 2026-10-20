package dev.kuratoon.media;

import dev.kuratoon.chapter.Chapter;
import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.character.StoryCharacter;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.media.MediaReadDtos.AltTitle;
import dev.kuratoon.media.MediaReadDtos.ChapterDetail;
import dev.kuratoon.media.MediaReadDtos.ChapterSummary;
import dev.kuratoon.media.MediaReadDtos.CharacterRef;
import dev.kuratoon.media.MediaReadDtos.MediaDetail;
import dev.kuratoon.media.MediaReadDtos.MediaTypeRef;
import dev.kuratoon.media.MediaReadDtos.NamedRef;
import dev.kuratoon.media.MediaReadDtos.RelatedMedia;
import dev.kuratoon.media.MediaReadDtos.Relation;
import dev.kuratoon.media.MediaReadDtos.TagRef;
import dev.kuratoon.mediatype.MediaType;
import dev.kuratoon.tag.Tag;
import dev.kuratoon.tag.TagKind;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Media detail, chapter list and chapter detail. Each collection of the media is loaded with its
 * own query (lazy, batch-fetched) rather than one cartesian join.
 */
@Service
public class MediaReadService {

    private static final Comparator<NamedRef> BY_NAME =
            Comparator.comparing(NamedRef::name, String.CASE_INSENSITIVE_ORDER).thenComparing(NamedRef::id);
    private static final Comparator<TagRef> TAG_BY_NAME =
            Comparator.comparing(TagRef::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TagRef::id);

    private final MediaRepository media;
    private final MediaRelationRepository relations;
    private final ChapterRepository chapters;

    public MediaReadService(MediaRepository media, MediaRelationRepository relations, ChapterRepository chapters) {
        this.media = media;
        this.relations = relations;
        this.chapters = chapters;
    }

    @Transactional(readOnly = true)
    public MediaDetail detail(UUID id) {
        Media m = media.findById(id).orElseThrow(() -> NotFoundException.of("Media", id));
        MediaType type = m.getMediaType();
        return new MediaDetail(
                m.getId(),
                m.getTitle(),
                m.getAlternativeTitles().stream()
                        .map(t -> new AltTitle(t.getTitle(), t.getLanguage()))
                        .sorted(Comparator.comparing(AltTitle::title))
                        .toList(),
                new MediaTypeRef(type.getId(), type.getName(), type.getSlug(), type.getColor()),
                m.getPublicationStatus(),
                m.getPublicationYear(),
                m.getLanguage(),
                m.isAdult(),
                m.getDescription(),
                creators(m, CreatorRole.AUTHOR),
                creators(m, CreatorRole.ARTIST),
                tags(m, TagKind.GENRE),
                tags(m, TagKind.LIBRARY),
                m.getParodies().stream().map(p -> new NamedRef(p.getId(), p.getName())).sorted(BY_NAME).toList(),
                m.getCharacters().stream()
                        .map(MediaReadService::character)
                        .sorted(Comparator.comparing(CharacterRef::name, String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(CharacterRef::id))
                        .toList(),
                relations(m.getId()),
                m.getChapterCount(),
                MissingChapters.compute(chapters.findNumbersByMediaId(m.getId())),
                m.getAvgRating(),
                m.getRatingCount(),
                m.getFavoriteCount(),
                m.getCreatedAt(),
                m.getLatestChapterAddedAt());
    }

    @Transactional(readOnly = true)
    public List<ChapterSummary> chapters(UUID mediaId) {
        if (!media.existsById(mediaId)) {
            throw NotFoundException.of("Media", mediaId);
        }
        return chapters.findSummariesByMediaId(mediaId);
    }

    @Transactional(readOnly = true)
    public ChapterDetail chapter(UUID chapterId) {
        Chapter c = chapters.findById(chapterId).orElseThrow(() -> NotFoundException.of("Chapter", chapterId));
        return new ChapterDetail(c.getId(), c.getMedia().getId(), c.getNumber(), c.getTitle(), c.getPageCount(),
                c.getPages());
    }

    private static List<NamedRef> creators(Media m, CreatorRole role) {
        return m.getCreators().stream()
                .filter(mc -> mc.getRole() == role)
                .map(mc -> new NamedRef(mc.getCreator().getId(), mc.getCreator().getName()))
                .sorted(BY_NAME)
                .toList();
    }

    private static List<TagRef> tags(Media m, TagKind kind) {
        return m.getTags().stream()
                .filter(t -> t.getKind() == kind)
                .map(MediaReadService::tag)
                .sorted(TAG_BY_NAME)
                .toList();
    }

    private static TagRef tag(Tag t) {
        return new TagRef(t.getId(), t.getName(), t.getSlug());
    }

    private static CharacterRef character(StoryCharacter c) {
        NamedRef parody = c.getParody() == null ? null : new NamedRef(c.getParody().getId(), c.getParody().getName());
        return new CharacterRef(c.getId(), c.getName(), parody);
    }

    /** Both directions, labelled from this media's point of view; ordered by label, then title. */
    private List<Relation> relations(UUID mediaId) {
        return relations.findInvolving(mediaId).stream()
                .map(r -> r.getFrom().getId().equals(mediaId)
                        ? new Relation(RelationLabel.forward(r.getType()), related(r.getTo()))
                        : new Relation(RelationLabel.inverse(r.getType()), related(r.getFrom())))
                .sorted(Comparator.comparing(Relation::label)
                        .thenComparing(r -> r.media().title(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static RelatedMedia related(Media m) {
        return new RelatedMedia(m.getId(), m.getTitle(), m.getMediaType().getSlug());
    }
}
