package dev.kuratoon.chapter;

import dev.kuratoon.media.MediaReadDtos.ChapterSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    /** Chapter list rows without the pages column. */
    @Query("""
            select new dev.kuratoon.media.MediaReadDtos$ChapterSummary(c.id, c.number, c.title, c.pageCount, c.createdAt)
            from Chapter c
            where c.media.id = :mediaId
            order by c.number
            """)
    List<ChapterSummary> findSummariesByMediaId(@Param("mediaId") UUID mediaId);

    /** Full chapters (with pages) of a media, ascending. */
    @Query("select c from Chapter c where c.media.id = :mediaId order by c.number")
    List<Chapter> findByMediaIdOrdered(@Param("mediaId") UUID mediaId);

    @Query("select c.number from Chapter c where c.media.id = :mediaId")
    List<BigDecimal> findNumbersByMediaId(@Param("mediaId") UUID mediaId);
}
