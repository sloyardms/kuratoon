package dev.kuratoon.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.kuratoon.common.UuidV7;
import dev.kuratoon.user.AppUser;
import dev.kuratoon.user.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Opaque refresh tokens with rotation and reuse detection (docs/spec/04-auth-users.md, ADR 0013).
 *
 * <ul>
 *   <li>Tokens are 256 random bits; only their SHA-256 is stored.
 *   <li>Every refresh rotates: the old row gets {@code rotated_at}, a successor joins the family.
 *   <li>Reusing a rotated token within {@link #GRACE_WINDOW} returns the same successor pair (parallel
 *       requests and prefetches refreshing at once). The pair is cached in memory before commit and
 *       evicted if the transaction rolls back; a concurrent refresh waits on the row lock and then
 *       finds it.
 *   <li>A rotated token reused within the window but missing from the cache (e.g. after a restart)
 *       is rejected without revoking the family.
 *   <li>Reuse outside the window revokes the whole family.
 * </ul>
 *
 * <p>The cache is per process, which assumes a single backend instance.
 */
@Service
public class RefreshTokenService {

    public static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);
    public static final Duration GRACE_WINDOW = Duration.ofSeconds(10);

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final JwtService jwtService;
    private final Clock clock;
    private final Cache<String, TokenPair> recentRotations = Caffeine.newBuilder()
            .expireAfterWrite(GRACE_WINDOW.plusSeconds(5))
            .maximumSize(100_000)
            .build();

    public RefreshTokenService(JdbcClient jdbc, AppUserRepository users, JwtService jwtService, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /** Access + refresh token pair, as set in the cookies. */
    public record TokenPair(UUID userId, String accessToken, Instant accessExpiresAt, String refreshToken) {}

    /** Starts a new family (login). Must run inside the caller's transaction. */
    @Transactional
    public TokenPair issueForLogin(AppUser user) {
        return issue(user, UuidV7.generate());
    }

    /** Rotates a refresh token; empty when it is unknown, expired, revoked or reused. */
    @Transactional
    public Optional<TokenPair> rotate(String rawToken) {
        String hash = hash(rawToken);
        Optional<Row> found = jdbc.sql("""
                        select id, user_id, family_id, expires_at, rotated_at, revoked_at
                        from refresh_token where token_hash = :hash for update
                        """)
                .param("hash", hash)
                .query((rs, n) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("family_id", UUID.class),
                        rs.getTimestamp("expires_at").toInstant(),
                        instant(rs.getTimestamp("rotated_at")),
                        instant(rs.getTimestamp("revoked_at"))))
                .optional();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Row row = found.get();
        Instant now = clock.instant();
        if (row.revokedAt() != null || !row.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        if (row.rotatedAt() != null) {
            if (!now.isAfter(row.rotatedAt().plus(GRACE_WINDOW))) {
                // Within the grace window: same successor pair, or a plain rejection on a cache miss.
                return Optional.ofNullable(recentRotations.getIfPresent(hash));
            }
            revokeFamily(row.familyId(), now);
            log.warn("Refresh token reuse detected; revoked token family {} of user {}", row.familyId(), row.userId());
            return Optional.empty();
        }
        Optional<AppUser> user = users.findById(row.userId()).filter(AppUser::isEnabled);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        jdbc.sql("update refresh_token set rotated_at = :now where id = :id")
                .param("now", Timestamp.from(now))
                .param("id", row.id())
                .update();
        TokenPair successor = issue(user.get(), row.familyId());
        cacheUntilRollback(hash, successor);
        return Optional.of(successor);
    }

    /** Logout: revokes the presented token (unknown tokens are ignored). */
    @Transactional
    public void revoke(String rawToken) {
        jdbc.sql("update refresh_token set revoked_at = :now where token_hash = :hash and revoked_at is null")
                .param("now", Timestamp.from(clock.instant()))
                .param("hash", hash(rawToken))
                .update();
    }

    /** Log out everywhere, password changes, admin revoke, disable, role change. */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        jdbc.sql("update refresh_token set revoked_at = :now where user_id = :userId and revoked_at is null")
                .param("now", Timestamp.from(clock.instant()))
                .param("userId", userId)
                .update();
    }

    /** For tests: simulates a restart that lost the in-memory grace cache. */
    void clearGraceCache() {
        recentRotations.invalidateAll();
    }

    private TokenPair issue(AppUser user, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        jdbc.sql("""
                        insert into refresh_token (id, user_id, family_id, token_hash, expires_at, created_at)
                        values (:id, :userId, :familyId, :hash, :expiresAt, :now)
                        """)
                .param("id", UuidV7.generate())
                .param("userId", user.getId())
                .param("familyId", familyId)
                .param("hash", hash(raw))
                .param("expiresAt", Timestamp.from(now.plus(REFRESH_TOKEN_LIFETIME)))
                .param("now", Timestamp.from(now))
                .update();
        JwtService.AccessToken access = jwtService.issue(user);
        return new TokenPair(user.getId(), access.value(), access.expiresAt(), raw);
    }

    /**
     * Caches before commit so a concurrent refresh blocked on the row lock finds the pair as soon
     * as the lock is released; evicts it again if this transaction rolls back.
     */
    private void cacheUntilRollback(String hash, TokenPair successor) {
        recentRotations.put(hash, successor);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    recentRotations.invalidate(hash);
                }
            }
        });
    }

    private void revokeFamily(UUID familyId, Instant now) {
        jdbc.sql("update refresh_token set revoked_at = :now where family_id = :familyId and revoked_at is null")
                .param("now", Timestamp.from(now))
                .param("familyId", familyId)
                .update();
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private record Row(UUID id, UUID userId, UUID familyId, Instant expiresAt, Instant rotatedAt, Instant revokedAt) {}
}
