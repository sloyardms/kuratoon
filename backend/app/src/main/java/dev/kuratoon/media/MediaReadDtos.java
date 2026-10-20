package dev.kuratoon.media;

import dev.kuratoon.chapter.PageEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-model responses for media and chapters. Image URLs (phase 5) and per-user state (phase 6)
 * are added by later phases.
 */
public final class MediaReadDtos {

    private MediaReadDtos() {}

    public record MediaTypeRef(UUID id, String name, String slug, String color) {}

    public record NamedRef(UUID id, String name) {}

    public record TagRef(UUID id, String name, String slug) {}

    public record AltTitle(String title, String language) {}

    /** @param parody null when the character has no parody */
    public record CharacterRef(UUID id, String name, NamedRef parody) {}

    public record RelatedMedia(UUID id, String title, String typeSlug) {}

    public record Relation(RelationLabel label, RelatedMedia media) {}

    public record MediaDetail(
            UUID id,
            String title,
            List<AltTitle> alternativeTitles,
            MediaTypeRef type,
            PublicationStatus publicationStatus,
            Short publicationYear,
            String language,
            boolean adult,
            String description,
            List<NamedRef> authors,
            List<NamedRef> artists,
            List<TagRef> genres,
            List<TagRef> libraryTags,
            List<NamedRef> parodies,
            List<CharacterRef> characters,
            List<Relation> relations,
            int chapterCount,
            List<Integer> missingChapters,
            BigDecimal avgRating,
            int ratingCount,
            int favoriteCount,
            Instant createdAt,
            Instant latestChapterAddedAt) {}

    /** One row of the chapter list; pages are only in {@link ChapterDetail}. */
    public record ChapterSummary(UUID id, BigDecimal number, String title, int pageCount, Instant createdAt) {
        public ChapterSummary {
            number = MissingChapters.displayNumber(number);
        }
    }

    public record ChapterDetail(
            UUID id, UUID mediaId, BigDecimal number, String title, int pageCount, List<PageEntry> pages) {
        public ChapterDetail {
            number = MissingChapters.displayNumber(number);
        }
    }
}
