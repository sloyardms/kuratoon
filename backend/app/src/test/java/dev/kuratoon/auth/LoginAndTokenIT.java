package dev.kuratoon.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.TestData;
import dev.kuratoon.user.AppUser;
import dev.kuratoon.user.Role;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Login, cookie attributes, access token validation and log out everywhere (spec 04, ADR 0013). */
class LoginAndTokenIT extends AuthTestBase {

    @Test
    void loginSetsBothHttpOnlyCookies() throws Exception {
        MvcTestResult result = login("admin", TestData.PASSWORD);

        assertThat(result).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.user.username").isEqualTo("admin");
            assertThat(json).extractingPath("$.user.role").isEqualTo("ADMIN");
            assertThat(json).extractingPath("$.accessExpiresAt").isNotNull();
        });
        assertThat(setCookie(result, AuthCookies.ACCESS)).hasValueSatisfying(c -> assertThat(c)
                .contains("Path=/;", "Max-Age=900", "HttpOnly", "SameSite=Lax").doesNotContain("Secure"));
        assertThat(setCookie(result, AuthCookies.REFRESH)).hasValueSatisfying(c -> assertThat(c)
                .contains("Path=/;", "Max-Age=2592000", "HttpOnly", "SameSite=Lax").doesNotContain("Secure"));
        assertThat(jdbc.queryForObject("select last_login_at is not null from app_user where username = 'admin'",
                Boolean.class)).isTrue();
    }

    @Test
    void accessCookieAuthenticatesRequests() {
        MvcTestResult login = login("admin", TestData.PASSWORD);
        asAnonymous();

        assertThat(mvc.get().uri("/api/v1/me").cookie(new Cookie(AuthCookies.ACCESS, cookieValue(login, AuthCookies.ACCESS))))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.username")
                .isEqualTo("admin");
        assertThat(mvc.get().uri("/api/v1/me")).hasStatus(401);
    }

    @Test
    void wrongPasswordUnknownUserAndDisabledUserGetIdenticalResponses() throws Exception {
        AppUser disabled = data.aUser("sleepy", Role.USER);
        jdbc.update("update app_user set enabled = false where id = ?", disabled.getId());

        MvcTestResult wrongPassword = login("admin", "not-the-password");
        MvcTestResult unknownUser = login("nobody-here", "not-the-password");
        MvcTestResult disabledUser = login("sleepy", TestData.PASSWORD);

        for (MvcTestResult result : new MvcTestResult[] {wrongPassword, unknownUser, disabledUser}) {
            assertThat(result).hasStatus(401);
            assertThat(setCookies(result)).isEmpty();
        }
        String body = wrongPassword.getResponse().getContentAsString();
        assertThat(unknownUser.getResponse().getContentAsString()).isEqualTo(body);
        assertThat(disabledUser.getResponse().getContentAsString()).isEqualTo(body);
        assertThat(body).contains("Invalid username or password.");
    }

    @Test
    void usernameIsCaseInsensitive() {
        assertThat(login("ADMIN", TestData.PASSWORD)).hasStatusOk();
    }

    @Test
    void expiredOrForgedAccessTokensAreRejected() {
        as(admin);
        assertThat(get("/api/v1/me")).hasStatusOk();

        clock.advance(Duration.ofMinutes(16));
        assertThat(get("/api/v1/me")).hasStatus(401);

        clock.reset();
        String forged = jwtService.issue(admin).value();
        forged = forged.substring(0, forged.length() - 4) + (forged.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)).hasStatus(401);
    }

    @Test
    void bumpedTokenVersionRevokesAccessTokens() {
        MvcTestResult login = login("admin", TestData.PASSWORD);
        Cookie[] cookies = cookies(login);
        asAnonymous();

        assertThat(mvc.post().uri("/api/v1/auth/logout-all").header(HttpHeaders.ORIGIN, ORIGIN).cookie(cookies))
                .hasStatus(204);

        assertThat(mvc.get().uri("/api/v1/me").cookie(cookies)).hasStatus(401);
        assertThat(refresh(cookieValue(login, AuthCookies.REFRESH))).hasStatus(401);
    }

    @Test
    void disabledUsersStillValidTokenIsRejected() {
        AppUser user = asUser();
        assertThat(get("/api/v1/me")).hasStatusOk();

        jdbc.update("update app_user set enabled = false where id = ?", user.getId());

        assertThat(get("/api/v1/me")).hasStatus(401);
    }

    @Test
    void refreshCookieIsIgnoredOutsideAuthEndpoints() {
        MvcTestResult login = login("admin", TestData.PASSWORD);
        asAnonymous();

        assertThat(mvc.get().uri("/api/v1/me").cookie(new Cookie(AuthCookies.REFRESH, cookieValue(login, AuthCookies.REFRESH))))
                .hasStatus(401);
    }

    @Test
    void expiredAccessCookieDoesNotBlockLoginOrRefresh() {
        MvcTestResult first = login("admin", TestData.PASSWORD);
        clock.advance(Duration.ofMinutes(20));

        assertThat(browser(mvc.post().uri("/api/v1/auth/refresh"), "127.0.0.1").cookie(cookies(first)))
                .hasStatusOk();
    }

    @Test
    void anonymousAndUserAccessRules() {
        asAnonymous();
        assertThat(get("/api/v1/me")).hasStatus(401);
        assertThat(get("/api/v1/admin/users")).hasStatus(401);
        assertThat(get("/api/v1/auth/status")).hasStatusOk();
        assertThat(get("/api/v1/health")).hasStatusOk();

        asUser();
        assertThat(get("/api/v1/me")).hasStatusOk();
        assertThat(get("/api/v1/admin/users")).hasStatus(403);
    }
}
