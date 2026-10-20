package dev.kuratoon.mediatype;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaTypeRepository extends JpaRepository<MediaType, UUID> {

    Optional<MediaType> findBySlug(String slug);

    boolean existsByName(String name);

    boolean existsBySlug(String slug);

    boolean existsByNameAndIdNot(String name, UUID id);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    /** @param pattern escaped {@code %text%} or null for all */
    @Query("""
            select t from MediaType t
            where cast(:pattern as string) is null
               or lower(t.name) like lower(cast(:pattern as string)) escape '\\'
               or t.slug like lower(cast(:pattern as string)) escape '\\'
            """)
    Page<MediaType> search(@Param("pattern") String pattern, Pageable pageable);
}
