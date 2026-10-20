package dev.kuratoon.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.TestData;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Backend auth rate limits (AuthRateLimiter): per IP for every attempt; per username for failed
 * logins only, applied to IPs that already failed for that username.
 */
class RateLimitIT extends AuthTestBase {

    @Test
    void perIpLimitTriggersAfterTheBurst() {
        for (int i = 0; i < AuthRateLimiter.CAPACITY; i++) {
            assertThat(login("nobody", "wrong-password", "10.0.0.1")).hasStatus(401);
        }

        MvcTestResult limited = login("admin", TestData.PASSWORD, "10.0.0.1");

        assertThat(limited).hasStatus(429);
        assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(login("admin", TestData.PASSWORD, "10.0.0.2")).hasStatusOk();
    }

    @Test
    void perIpLimitCoversRegistrationAndPasswordReset() {
        for (int i = 0; i < AuthRateLimiter.CAPACITY; i++) {
            browser(mvc.post().uri("/api/v1/auth/register"), "10.0.0.3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"user" + i + "\",\"password\":\"long-enough-pw\"}")
                    .exchange();
        }
        assertThat(browser(mvc.post().uri("/api/v1/auth/password-reset/request"), "10.0.0.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.test\"}"))
                .hasStatus(429);
    }

    @Test
    void ipThatFailedForAUsernameIsLockedOutOnceTheUsernameBucketIsEmpty() {
        assertThat(login("admin", "wrong-password", "10.0.1.1")).hasStatus(401);
        // Other IPs drain the per-username bucket (10.0.1.1 keeps most of its per-IP budget).
        for (int i = 0; i < AuthRateLimiter.CAPACITY; i++) {
            login("admin", "wrong-password", "10.0.1." + (100 + i));
        }

        // Empty username bucket + this IP failed for "admin": refused before verification, even
        // with the right password.
        assertThat(login("admin", TestData.PASSWORD, "10.0.1.1")).hasStatus(429);

        // Once the username bucket refills (5/min), the IP may try again.
        clock.advance(Duration.ofSeconds(13));
        assertThat(login("admin", TestData.PASSWORD, "10.0.1.1")).hasStatusOk();
    }

    @Test
    void floodFromOtherIpsDoesNotBlockTheRealUserFromAFreshIp() {
        // A distributed flood of bad passwords for "admin", 2 attempts per IP.
        for (int i = 0; i < 30; i++) {
            login("admin", "wrong-password", "10.0.2." + (i / 2));
        }
        // The per-username bucket is now empty, but the admin's IP never failed.
        assertThat(login("admin", TestData.PASSWORD, "192.168.50.7")).hasStatusOk();
        // An attacker IP that already failed is refused before verification.
        assertThat(login("admin", TestData.PASSWORD, "10.0.2.0")).hasStatus(429);
    }

    @Test
    void successfulLoginsDoNotConsumeTheUsernameBucket() {
        for (int i = 0; i < AuthRateLimiter.CAPACITY * 2; i++) {
            // Different IPs so the per-IP limit is not the one being tested.
            assertThat(login("admin", TestData.PASSWORD, "10.0.3." + i)).hasStatusOk();
        }
        login("admin", "wrong-password", "10.0.4.1");
        // One failure later the same IP may still try: the username bucket was untouched by successes.
        assertThat(login("admin", TestData.PASSWORD, "10.0.4.1")).hasStatusOk();
    }
}
