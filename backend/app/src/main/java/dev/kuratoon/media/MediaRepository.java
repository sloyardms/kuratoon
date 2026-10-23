package dev.kuratoon.media;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Admin list: title or alternative title contains the pattern (escaped, lowercased), optional type. */
    @Query(value = """
            select m from Media m join fetch m.mediaType
            where (:typeId is null or m.mediaType.id = :typeId)
              and (cast(:pattern as string) is null
                   or lower(m.title) like lower(cast(:pattern as string)) escape '\\'
                   or exists (select 1 from MediaTitle t where t.media = m
                              and lower(t.title) like lower(cast(:pattern as string)) escape '\\'))
            """,
            countQuery = """
            select count(m) from Media m
            where (:typeId is null or m.mediaType.id = :typeId)
              and (cast(:pattern as string) is null
                   or lower(m.title) like lower(cast(:pattern as string)) escape '\\'
                   or exists (select 1 from MediaTitle t where t.media = m
                              and lower(t.title) like lower(cast(:pattern as string)) escape '\\'))
            """)
    Page<Media> searchAdmin(@Param("pattern") String pattern, @Param("typeId") UUID typeId, Pageable pageable);

    @Query("select m.id from Media m")
    List<UUID> findAllIds();
}
