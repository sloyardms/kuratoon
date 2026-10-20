package dev.kuratoon.character;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryCharacterRepository extends JpaRepository<StoryCharacter, UUID> {

    /** Same normalized name with the same parody; {@code parodyId} null means "no parody". */
    @Query("""
            select count(c) > 0 from StoryCharacter c
            where c.normalizedName = :normalizedName
              and (c.parody.id = :parodyId or (:parodyId is null and c.parody is null))
              and (:excludeId is null or c.id <> :excludeId)
            """)
    boolean existsDuplicate(
            @Param("normalizedName") String normalizedName,
            @Param("parodyId") UUID parodyId,
            @Param("excludeId") UUID excludeId);

    /**
     * Names of the parody's characters that would collide with an existing parody-less character
     * of the same normalized name if the parody were deleted (which sets their parody to NULL).
     */
    @Query("""
            select c.name from StoryCharacter c
            where c.parody.id = :parodyId
              and exists (select 1 from StoryCharacter o
                          where o.parody is null and o.normalizedName = c.normalizedName)
            order by c.name
            """)
    List<String> namesCollidingWhenParodyRemoved(@Param("parodyId") UUID parodyId);

    @EntityGraph(attributePaths = "parody")
    @Query("""
            select c from StoryCharacter c left join c.parody p
            where (:parodyId is null or p.id = :parodyId)
              and (cast(:pattern as string) is null
                   or lower(c.name) like lower(cast(:pattern as string)) escape '\\')
            """)
    Page<StoryCharacter> search(
            @Param("parodyId") UUID parodyId, @Param("pattern") String pattern, Pageable pageable);
}
