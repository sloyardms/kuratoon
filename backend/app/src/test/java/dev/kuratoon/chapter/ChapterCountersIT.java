package dev.kuratoon.chapter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.media.Media;
import dev.kuratoon.media.MediaRepository;
import dev.kuratoon.support.ApiTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** {@code media.chapter_count} / {@code latest_chapter_added_at} maintenance (roadmap phase 3 note). */
class ChapterCountersIT extends ApiTestBase {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final List<PageEntry> PAGES = List.of(new PageEntry("0001.jpg", 800, 1200));

    @Autowired
    ChapterCounters counters;

    @Autowired
    ChapterRepository chapters;

    @Autowired
    MediaRepository mediaRepository;

    @Autowired
    TransactionTemplate tx;

    @Test
    void constructorDoesNotTouchCounters() {
        UUID id = data.aMedia("Plain").persist().getId();
        tx.executeWithoutResult(s -> {
            Media m = mediaRepository.findById(id).orElseThrow();
            chapters.save(new Chapter(m, BigDecimal.ONE, null, "1", PAGES, T0));
        });
        assertCounters(id, 0, null);
    }

    @Test
    void addRaisesCountAndLatestDate() {
        UUID id = data.aMedia("Add").persist().getId();
        add(id, "1", T0);
        assertCounters(id, 1, T0);
        add(id, "2", T0.plus(1, ChronoUnit.DAYS));
        assertCounters(id, 2, T0.plus(1, ChronoUnit.DAYS));
        // An older chapter (e.g. restored from a sidecar) never moves the latest date back.
        add(id, "0", T0.minus(1, ChronoUnit.DAYS));
        assertCounters(id, 3, T0.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void deletingTheLatestFallsBackToThePreviousMax() {
        UUID id = data.aMedia("Delete").persist().getId();
        add(id, "1", T0);
        UUID latest = add(id, "2", T0.plus(2, ChronoUnit.HOURS));
        delete(id, latest);
        assertCounters(id, 1, T0);
    }

    @Test
    void deletingAllChaptersClearsTheCounters() {
        UUID id = data.aMedia("Empty").persist().getId();
        UUID only = add(id, "1", T0);
        delete(id, only);
        assertCounters(id, 0, null);
    }

    @Test
    void replaceKeepsCountAndDate() {
        UUID id = data.aMedia("Replace").persist().getId();
        UUID chapterId = add(id, "1", T0);
        tx.executeWithoutResult(s -> {
            Chapter c = chapters.findById(chapterId).orElseThrow();
            c.replacePages(List.of(new PageEntry("0001.png", 800, 1000), new PageEntry("0002.png", 800, 1000)));
            counters.recalculate(c.getMedia());
        });
        assertCounters(id, 1, T0);
        Chapter replaced = chapters.findById(chapterId).orElseThrow();
        assertThat(replaced.getPageCount()).isEqualTo(2);
        assertThat(replaced.getCreatedAt()).isEqualTo(T0);
        assertThat(replaced.getUpdatedAt()).isAfter(T0);
    }

    private UUID add(UUID mediaId, String number, Instant createdAt) {
        return tx.execute(s -> {
            Media m = mediaRepository.findById(mediaId).orElseThrow();
            Chapter c = chapters.save(new Chapter(m, new BigDecimal(number), null, number, PAGES, createdAt));
            counters.recalculate(m);
            return c.getId();
        });
    }

    private void delete(UUID mediaId, UUID chapterId) {
        tx.executeWithoutResult(s -> {
            chapters.deleteById(chapterId);
            counters.recalculate(mediaRepository.findById(mediaId).orElseThrow());
        });
    }

    private void assertCounters(UUID mediaId, int count, Instant latest) {
        Media m = mediaRepository.findById(mediaId).orElseThrow();
        assertThat(m.getChapterCount()).isEqualTo(count);
        assertThat(m.getLatestChapterAddedAt()).isEqualTo(latest);
    }
}
