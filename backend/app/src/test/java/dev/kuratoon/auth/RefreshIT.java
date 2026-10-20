package dev.kuratoon.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.TestData;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refresh rotation with a ~10 s grace window (ADR 0013): reuse of the just-rotated token returns the
 * same successor pair; a cache miss inside the window is rejected without revoking; reuse outside
 * the window revokes the family.
 */
class RefreshIT extends AuthTestBase {

    @Autowired
    TransactionTemplate tx;

    @Test
    void rotationIssuesANewPairAndMarksTheOldTokenRotated() {
        String original = cookieValue(login("admin", TestData.PASSWORD), AuthCookies.REFRESH);

        MvcTestResult rotated = refresh(original);

        assertThat(rotated).hasStatusOk().bodyJson().extractingPath("$.user.username").isEqualTo("admin");
        String successor = cookieValue(rotated, AuthCookies.REFRESH);
        assertThat(successor).isNotEqualTo(original);
        assertThat(cookieValue(rotated, AuthCookies.ACCESS)).isNotBlank();
        assertThat(jdbc.queryForObject(
                "select rotated_at is not null from refresh_token where token_hash = ?", Boolean.class,
                RefreshTokenService.hash(original))).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(distinct family_id) from refresh_token", Integer.class)).isEqualTo(1);
    }

    @Test
    void reuseWithinTheGraceWindowReturnsTheSameSuccessorPair() {
        String original = cookieValue(login("admin", TestData.PASSWORD), AuthCookies.REFRESH);
        MvcTestResult first = refresh(original);
        clock.advance(Duration.ofSeconds(8));

        MvcTestResult second = refresh(original);

        assertThat(second).hasStatusOk();
        assertThat(cookieValue(second, AuthCookies.REFRESH)).isEqualTo(cookieValue(first, AuthCookies.REFRESH));
        assertThat(cookieValue(second, AuthCookies.ACCESS)).isEqualTo(cookieValue(first, AuthCookies.ACCESS));
        assertThat(refresh(cookieValue(first, AuthCookies.REFRESH))).hasStatusOk();
    }

    @Test
    void concurrentRefreshesWithTheSameTokenGetTheSamePair() throws Exception {
        String original = cookieValue(login("admin", TestData.PASSWORD), AuthCookies.REFRESH);
        int parallel = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        try {
            Callable<MvcTestResult> call = () -> {
                start.await();
                return refresh(original);
            };
            List<Future<MvcTestResult>> futures = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                futures.add(pool.submit(call));
            }
            start.countDown(); // release all threads at once
            List<String> successors = new ArrayList<>();
            for (Future<MvcTestResult> f : futures) {
                MvcTestResult result = f.get();
                assertThat(result).hasStatusOk();
                successors.add(cookieValue(result, AuthCookies.REFRESH));
            }
            assertThat(successors).containsOnly(successors.getFirst());
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is not null", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token", Integer.class)).isEqualTo(2);
    }

    @Test
    void cacheMissInsideTheWindowIsRejectedWithoutRevokingTheFamily() {
        String original = cookieValue(login("admin", TestData.PASSWORD), AuthCookies.REFRESH);
        String successor = cookieValue(refresh(original), AuthCookies.REFRESH);
        refreshTokens.clearGraceCache(); // e.g. a backend restart

        assertThat(refresh(original)).hasStatus(401);

        assertThat(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is not null", Integer.class))
                .isZero();
        assertThat(refresh(successor)).hasStatusOk();
    }

    @Test
    void reuseOutsideTheWindowRevokesTheWholeFamily() {
        String original = cookieValue(login("admin", TestData.PASSWORD), AuthCookies.REFRESH);
        String successor = cookieValue(refresh(original), AuthCookies.REFRESH);
        clock.advance(Duration.ofSeconds(11));

        MvcTestResult reuse = refresh(original);

        assertThat(reuse).hasStatus(401);
        assertThat(setCookie(reuse, AuthCookies.REFRESH)).hasValueSatisfying(c -> assertThat(c).contains("Max-Age=0"));
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is null", Integer.class))
                .isZero();
        assertThat(refresh(successor)).hasStatus(401);
    }

    @Test
    void otherFamiliesSurviveAReuseRevocation() {
        String deviceA = cookieValue(login("admin", TestData.PASSWORD), AuthCookies.REFRESH);
        String deviceB = cookieValue(login("admin", TestData.PASSWORD), AuthCookies.REFRESH);
        refresh(deviceA);
        clock.advance(Duration.ofSeconds(11));

        assertThat(refresh(deviceA)).hasStatus(401);
        assertThat(refresh(deviceB)).hasStatusOk();
    }

    @Test
    void rollbackDuringRotationEvictsTheCachedPair() {
        String original = cookieValue(login("admin", TestData.PASSWORD), AuthCookies.REFRESH);

        try {
            tx.executeWithoutResult(status -> {
                Optional<RefreshTokenService.TokenPair> pair = refreshTokens.rotate(original);
                assertThat(pair).isPresent();
                throw new IllegalStateException("simulated failure after rotation");
            });
        } catch (IllegalStateException expected) {
            // rolled back
        }

        // The rotation never happened: the original token still rotates normally (no cached pair served).
        MvcTestResult retry = refresh(original);
        assertThat(retry).hasStatusOk();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token", Integer.class)).isEqualTo(2);
    }

    @Test
    void expiredRevokedOrUnknownTokensAreRejected() {
        String expired = cookieValue(login("admin", TestData.PASSWORD), AuthCookies.REFRESH);
        clock.advance(Duration.ofDays(31));
        assertThat(refresh(expired)).hasStatus(401);
        clock.reset();

        assertThat(refresh("not-a-real-token")).hasStatus(401);
        assertThat(browser(mvc.post().uri("/api/v1/auth/refresh"), "127.0.0.1")).hasStatus(401);
    }

    @Test
    void logoutRevokesTheCurrentTokenAndClearsCookies() {
        MvcTestResult login = login("admin", TestData.PASSWORD);
        String refreshToken = cookieValue(login, AuthCookies.REFRESH);

        MvcTestResult logout = browser(mvc.post().uri("/api/v1/auth/logout"), "127.0.0.1")
                .cookie(new Cookie(AuthCookies.REFRESH, refreshToken))
                .exchange();

        assertThat(logout).hasStatus(204);
        assertThat(setCookie(logout, AuthCookies.ACCESS)).hasValueSatisfying(c -> assertThat(c).contains("Max-Age=0"));
        assertThat(setCookie(logout, AuthCookies.REFRESH)).hasValueSatisfying(c -> assertThat(c).contains("Max-Age=0"));
        assertThat(refresh(refreshToken)).hasStatus(401);
    }
}
