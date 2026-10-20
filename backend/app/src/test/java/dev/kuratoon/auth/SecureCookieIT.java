package dev.kuratoon.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** With an https APP_BASE_URL the cookies are Secure and only that origin may send non-GET requests. */
@TestPropertySource(properties = "kuratoon.app-base-url=https://manga.example.com")
class SecureCookieIT extends AuthTestBase {

    private MvcTestResult loginFrom(String origin) {
        return mvc.post().uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, origin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"admin\", \"password\": \"" + TestData.PASSWORD + "\"}")
                .exchange();
    }

    @Test
    void cookiesAreSecureForHttpsBaseUrl() {
        MvcTestResult result = loginFrom("https://manga.example.com");

        assertThat(result).hasStatusOk();
        assertThat(setCookie(result, AuthCookies.ACCESS)).hasValueSatisfying(c -> assertThat(c).contains("Secure"));
        assertThat(setCookie(result, AuthCookies.REFRESH)).hasValueSatisfying(c -> assertThat(c).contains("Secure"));
    }

    @Test
    void otherAccessUrlsAreRejected() {
        assertThat(loginFrom("http://manga.example.com")).hasStatus(403);
        assertThat(loginFrom("http://192.168.1.10:8080")).hasStatus(403);
    }
}
