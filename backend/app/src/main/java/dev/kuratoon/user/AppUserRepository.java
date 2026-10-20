package dev.kuratoon.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Username and email are {@code citext}. A plain varchar parameter would be compared as text
 * (case-sensitive), so those lookups cast the parameter to citext explicitly.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @Query(value = "select * from app_user where username = cast(:username as citext)", nativeQuery = true)
    Optional<AppUser> findByUsername(@Param("username") String username);

    @Query(value = "select * from app_user where email = cast(:email as citext)", nativeQuery = true)
    Optional<AppUser> findByEmail(@Param("email") String email);

    @Query(value = "select exists (select 1 from app_user where username = cast(:username as citext))",
            nativeQuery = true)
    boolean existsByUsername(@Param("username") String username);

    @Query(value = """
            select exists (select 1 from app_user
                           where email = cast(:email as citext) and (cast(:excludeId as uuid) is null or id <> :excludeId))
            """, nativeQuery = true)
    boolean existsByEmailExcluding(@Param("email") String email, @Param("excludeId") UUID excludeId);

    boolean existsByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    /** @param pattern escaped {@code %text%} or null for all */
    @Query(value = """
            select * from app_user
            where cast(:pattern as text) is null
               or username ilike cast(:pattern as text) escape '\\'
               or email ilike cast(:pattern as text) escape '\\'
            """,
            countQuery = """
            select count(*) from app_user
            where cast(:pattern as text) is null
               or username ilike cast(:pattern as text) escape '\\'
               or email ilike cast(:pattern as text) escape '\\'
            """,
            nativeQuery = true)
    Page<AppUser> search(@Param("pattern") String pattern, Pageable pageable);
}
