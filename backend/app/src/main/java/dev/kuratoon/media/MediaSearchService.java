package dev.kuratoon.media;

import dev.kuratoon.common.Queries;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Title search over {@code media.title} and every {@code media_title.title} (docs/spec/01-data-model.md),
 * backed by the GIN trigram indexes. Used by the command palette and browse (phase 5).
 *
 * <ul>
 *   <li>Queries of 3+ characters: substring match ({@code ILIKE}) or trigram word similarity
 *       ({@code q <% title}) at or above {@link #WORD_SIMILARITY_THRESHOLD}.
 *   <li>Queries of 1–2 characters produce no trigrams, so they use the substring match only.
 * </ul>
 *
 * Results are ranked by the best-matching title (word similarity, then overall similarity) and
 * each media appears once.
 */
@Service
public class MediaSearchService {

    /** Pinned here instead of relying on the pg_trgm default (0.6) or server configuration. */
    public static final double WORD_SIMILARITY_THRESHOLD = 0.4;

    public static final int MAX_LIMIT = 50;

    /** Shortest query that yields a trigram match. */
    static final int MIN_FUZZY_LENGTH = 3;

    private static final String CANDIDATES_FUZZY = """
            select m.id as media_id, m.title as matched
            from media m
            where m.title ilike :pattern escape '\\' or :q <% m.title
            union all
            select t.media_id, t.title
            from media_title t
            where t.title ilike :pattern escape '\\' or :q <% t.title
            """;

    private static final String CANDIDATES_SUBSTRING = """
            select m.id as media_id, m.title as matched
            from media m
            where m.title ilike :pattern escape '\\'
            union all
            select t.media_id, t.title
            from media_title t
            where t.title ilike :pattern escape '\\'
            """;

    private static final String RANKED = """
            with candidates as (%s),
            scored as (
                select media_id, matched,
                       word_similarity(:q, matched) as word_score,
                       similarity(:q, matched) as score
                from candidates
            ),
            best as (
                select distinct on (media_id) media_id, matched, word_score, score
                from scored
                order by media_id, word_score desc, score desc, matched
            )
            select m.id, m.title, b.matched
            from best b join media m on m.id = b.media_id
            order by b.word_score desc, b.score desc, m.title, m.id
            limit :limit
            """;

    /** @param matchedTitle the main or alternative title that matched best */
    public record MediaSearchHit(UUID id, String title, String matchedTitle) {}

    private final JdbcClient jdbc;

    public MediaSearchService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<MediaSearchHit> searchByTitle(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String q = query.strip();
        boolean fuzzy = q.codePointCount(0, q.length()) >= MIN_FUZZY_LENGTH;
        if (fuzzy) {
            // SET LOCAL: applies to this transaction only, so pooled connections keep their defaults.
            jdbc.sql("select set_config('pg_trgm.word_similarity_threshold', :threshold, true)")
                    .param("threshold", Double.toString(WORD_SIMILARITY_THRESHOLD))
                    .query()
                    .singleValue();
        }
        return jdbc.sql(RANKED.formatted(fuzzy ? CANDIDATES_FUZZY : CANDIDATES_SUBSTRING))
                .param("q", q)
                .param("pattern", "%" + Queries.escapeLike(q) + "%")
                .param("limit", Math.clamp(limit, 1, MAX_LIMIT))
                .query((rs, row) -> new MediaSearchHit(
                        rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("matched")))
                .list();
    }
}
