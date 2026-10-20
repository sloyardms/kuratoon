package dev.kuratoon.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Backend rate limits for {@code /api/v1/auth/*} (docs/spec/04-auth-users.md "Passwords"); nginx adds a
 * coarse per-IP limit in front.
 *
 * <ul>
 *   <li><b>Per IP</b>: every auth attempt (login, register, setup, password reset) consumes a token;
 *       burst {@value #CAPACITY}, refill {@value #REFILL_PER_MINUTE}/min.
 *   <li><b>Per username</b>: only <em>failed</em> logins consume a token (same capacity/refill). While
 *       a username's bucket is empty, attempts for it are refused before password verification from
 *       IPs that failed for that username in the last {@link #FAILED_IP_MEMORY}. IPs without recent
 *       failures still get their attempt verified, so a flood of bad logins from elsewhere cannot lock
 *       the real user out. Cost: a distributed attacker gets one guess per fresh IP per window, on top
 *       of the per-IP limit and the Argon2 cost.
 * </ul>
 *
 * <p>State is in memory (single backend instance).
 */
@Component
public class AuthRateLimiter {

    static final int CAPACITY = 10;
    static final int REFILL_PER_MINUTE = 5;
    static final Duration FAILED_IP_MEMORY = Duration.ofMinutes(15);

    private final Clock clock;
    private final Cache<String, TokenBucket> perIp = buckets();
    private final Cache<String, TokenBucket> perUsername = buckets();
    private final Cache<String, Instant> failedUsernameIp = Caffeine.newBuilder()
            .expireAfterWrite(FAILED_IP_MEMORY)
            .maximumSize(200_000)
            .build();

    public AuthRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Thrown when a limit is hit; mapped to 429 with Retry-After. */
    public static class RateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;

        public RateLimitedException(long retryAfterSeconds) {
            super("Too many attempts. Try again later.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** Consumes one per-IP token for any auth attempt. */
    public void checkIp(String ip) {
        TokenBucket bucket = perIp.get(ip, k -> new TokenBucket(clock.instant()));
        long wait = bucket.tryConsume(clock.instant());
        if (wait > 0) {
            throw new RateLimitedException(wait);
        }
    }

    /** Refuses a login attempt from an IP that recently failed for a username whose bucket is empty. */
    public void checkUsername(String username, String ip) {
        String user = normalize(username);
        TokenBucket bucket = perUsername.getIfPresent(user);
        if (bucket == null) {
            return;
        }
        long wait = bucket.secondsUntilAvailable(clock.instant());
        if (wait > 0 && failedUsernameIp.getIfPresent(user + "|" + ip) != null) {
            throw new RateLimitedException(wait);
        }
    }

    /** Records a failed login: consumes a per-username token and remembers the IP. */
    public void loginFailed(String username, String ip) {
        String user = normalize(username);
        perUsername.get(user, k -> new TokenBucket(clock.instant())).tryConsume(clock.instant());
        failedUsernameIp.put(user + "|" + ip, clock.instant());
    }

    /** For tests. */
    public void reset() {
        perIp.invalidateAll();
        perUsername.invalidateAll();
        failedUsernameIp.invalidateAll();
    }

    private static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    private static Cache<String, TokenBucket> buckets() {
        return Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(1)).maximumSize(200_000).build();
    }

    /** Token bucket with continuous refill. */
    static final class TokenBucket {
        private static final double REFILL_PER_SECOND = REFILL_PER_MINUTE / 60.0;

        private double tokens = CAPACITY;
        private Instant last;

        TokenBucket(Instant now) {
            this.last = now;
        }

        /** @return 0 if a token was consumed, otherwise seconds until one is available */
        synchronized long tryConsume(Instant now) {
            refill(now);
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return waitSeconds();
        }

        synchronized long secondsUntilAvailable(Instant now) {
            refill(now);
            return tokens >= 1 ? 0 : waitSeconds();
        }

        private void refill(Instant now) {
            double elapsed = Math.max(0, Duration.between(last, now).toMillis() / 1000.0);
            tokens = Math.min(CAPACITY, tokens + elapsed * REFILL_PER_SECOND);
            last = now;
        }

        private long waitSeconds() {
            return Math.max(1, (long) Math.ceil((1 - tokens) / REFILL_PER_SECOND));
        }
    }
}
