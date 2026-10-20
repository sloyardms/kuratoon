package dev.kuratoon.media;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaRelationRepository extends JpaRepository<MediaRelation, UUID> {

    /** Relations in either direction, with both media loaded. */
    @Query("""
            select r from MediaRelation r join fetch r.from join fetch r.to
            where r.from.id = :mediaId or r.to.id = :mediaId
            """)
    List<MediaRelation> findInvolving(@Param("mediaId") UUID mediaId);
}
