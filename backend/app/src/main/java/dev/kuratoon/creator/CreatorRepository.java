package dev.kuratoon.creator;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreatorRepository extends JpaRepository<Creator, UUID> {

    boolean existsByNormalizedName(String normalizedName);

    Optional<Creator> findByNormalizedName(String normalizedName);

    boolean existsByNormalizedNameAndIdNot(String normalizedName, UUID id);

    @Query("""
            select c from Creator c
            where cast(:pattern as string) is null
               or lower(c.name) like lower(cast(:pattern as string)) escape '\\'
            """)
    Page<Creator> search(@Param("pattern") String pattern, Pageable pageable);

    @Query("select count(mc) > 0 from MediaCreator mc where mc.creator.id = :creatorId")
    boolean isCredited(@Param("creatorId") UUID creatorId);
}
