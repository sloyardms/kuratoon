package dev.kuratoon.media;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.media.MediaSearchService.MediaSearchHit;
import dev.kuratoon.support.ApiTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Trigram title search over {@code media.title} and {@code media_title.title}. The word-similarity
 * threshold is pinned by the service ({@link MediaSearchService#WORD_SIMILARITY_THRESHOLD} = 0.4),
 * not left to the pg_trgm default of 0.6.
 */
class MediaSearchIT extends ApiTestBase {

    @Autowired
    MediaSearchService search;

    @BeforeEach
    void library() {
        data.aMedia("Naruto").altTitle("ナルト").persist();
        data.aMedia("Boruto: Naruto Next Generations").persist();
        data.aMedia("Bleach").persist();
        data.aMedia("Kimetsu no Yaiba").altTitle("Demon Slayer").persist();
        data.aMedia("100% Perfect Girl").persist();
        data.aMedia("1000 Days").persist();
    }

    @Test
    void matchesMainTitle() {
        assertThat(titles("bleach")).containsExactly("Bleach");
    }

    @Test
    void matchesAlternativeTitleOnlyAndReportsWhichTitleMatched() {
        List<MediaSearchHit> hits = search.searchByTitle("demon slayer", 20);

        assertThat(hits).extracting(MediaSearchHit::title).containsExactly("Kimetsu no Yaiba");
        assertThat(hits.getFirst().matchedTitle()).isEqualTo("Demon Slayer");
        assertThat(titles("ナルト")).containsExactly("Naruto");
    }

    @Test
    void toleratesTyposWithinThePinnedThreshold() {
        // 0.4 <= similarity < 0.6: matches only because the service pins the threshold to 0.4.
        assertThat(wordSimilarity("naxuto", "Naruto")).isBetween(0.4, 0.6);
        assertThat(titles("naxuto")).contains("Naruto");
        assertThat(titles("narto")).contains("Naruto");
    }

    @Test
    void rejectsMatchesBelowThePinnedThreshold() {
        assertThat(wordSimilarity("nzzzto", "Naruto")).isLessThan(0.4);
        assertThat(titles("nzzzto")).isEmpty();
        assertThat(titles("zebra")).isEmpty();
    }

    @Test
    void ranksTheCloserTitleFirstAndReturnsEachMediaOnce() {
        assertThat(titles("naruto")).containsExactly("Naruto", "Boruto: Naruto Next Generations");
    }

    @Test
    void shortQueriesUseSubstringMatchingOnly() {
        assertThat(titles("Na")).containsExactlyInAnyOrder("Naruto", "Boruto: Naruto Next Generations");
        assertThat(titles("zq")).isEmpty();
        assertThat(titles("bl")).containsExactly("Bleach");
    }

    @Test
    void likeWildcardsInTheQueryAreLiteral() {
        // Unescaped, "0%" would be the pattern %0%% and also match "1000 Days".
        assertThat(titles("0%")).containsExactly("100% Perfect Girl");
        assertThat(titles("%")).containsExactly("100% Perfect Girl");
        assertThat(titles("_")).isEmpty();
    }

    @Test
    void blankQueryAndLimit() {
        assertThat(titles("   ")).isEmpty();
        assertThat(search.searchByTitle("a", 2)).hasSize(2);
    }

    private List<String> titles(String q) {
        return search.searchByTitle(q, 20).stream().map(MediaSearchHit::title).toList();
    }

    private double wordSimilarity(String query, String title) {
        return jdbc.queryForObject("select word_similarity(?, ?)", Double.class, query, title);
    }
}
