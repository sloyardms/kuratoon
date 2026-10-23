package dev.kuratoon.parody;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParodyRepository extends JpaRepository<Parody, UUID> {

    boolean existsByNormalizedName(String normalizedName);

    Optional<Parody> findByNormalizedName(String normalizedName);

    boolean existsByNormalizedNameAndIdNot(String normalizedName, UUID id);

    @Query("""
            select p from Parody p
            where cast(:pattern as string) is null
               or lower(p.name) like lower(cast(:pattern as string)) escape '\\'
            """)
    Page<Parody> search(@Param("pattern") String pattern, Pageable pageable);
}
