package dev.kuratoon.tag;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    boolean existsByKindAndSlug(TagKind kind, String slug);

    Optional<Tag> findByKindAndSlug(TagKind kind, String slug);

    boolean existsByKindAndSlugAndIdNot(TagKind kind, String slug, UUID id);

    /** @param kind null for both kinds; @param pattern escaped {@code %text%} or null */
    @Query("""
            select t from Tag t
            where (:kind is null or t.kind = :kind)
              and (cast(:pattern as string) is null
                   or lower(t.name) like lower(cast(:pattern as string)) escape '\\')
            """)
    Page<Tag> search(@Param("kind") TagKind kind, @Param("pattern") String pattern, Pageable pageable);
}
