package dev.kuratoon.media;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaRepository extends JpaRepository<Media, UUID> {

    boolean existsByMediaTypeId(UUID mediaTypeId);

    Optional<Media> findBySourceUrl(String sourceUrl);

    /**
     * Media of this type whose title or any alternative title equals one of {@code lowerTitles}
     * (lowercased): duplicate detection before a NEW import.
     */
    @Query("""
            select m from Media m
            where m.mediaType.id = :typeId
              and (lower(m.title) in :lowerTitles
                   or exists (select 1 from MediaTitle t where t.media = m and lower(t.title) in :lowerTitles))
            order by m.createdAt
            """)
    List<Media> findSameTitle(@Param("typeId") UUID typeId, @Param("lowerTitles") Collection<String> lowerTitles);

    @Query("select m.id from Media m")
    List<UUID> findAllIds();
}
