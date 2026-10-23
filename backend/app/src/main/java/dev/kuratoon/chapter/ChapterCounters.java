package dev.kuratoon.chapter;

import dev.kuratoon.media.Media;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains {@code media.chapter_count} and {@code media.latest_chapter_added_at} (denorm, spec 01).
 * Every write path that adds, deletes or replaces chapters calls {@link #recalculate} in its own
 * transaction. The values are derived from the chapter rows, so they are correct whatever the
 * change was (add, delete, replace, restore with sidecar dates).
 */
@Service
public class ChapterCounters {

    private final EntityManager em;

    public ChapterCounters(EntityManager em) {
        this.em = em;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recalculate(Media media) {
        // Pending chapter inserts/deletes must reach the database before counting.
        em.flush();
        Object[] row = em.createQuery(
                        "select count(c), max(c.createdAt) from Chapter c where c.media.id = :mediaId", Object[].class)
                .setParameter("mediaId", media.getId())
                .getSingleResult();
        media.setChapterCounters(Math.toIntExact((Long) row[0]), (Instant) row[1]);
    }
}
