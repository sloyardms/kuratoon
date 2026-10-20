package dev.kuratoon.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** CSRF defence: non-GET requests must come from APP_BASE_URL's origin (ADR 0013). */
class OriginCheckIT extends AuthTestBase {

    private static final String BODY = """
            {"name": "Adventure", "kind": "GENRE"}
            """;

    @Test
    void foreignOriginIsRejected() {
        Cookie[] cookies = cookies(login("admin", TestData.PASSWORD));
        asAnonymous();

        assertThat(mvc.post().uri("/api/v1/admin/tags").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.ORIGIN, "https://evil.example").cookie(cookies))
                .hasStatus(403);
        assertThat(mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + TestData.PASSWORD + "\"}")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .hasStatus(403);
        assertThat(auditCount("TAG_CREATED")).isZero();
    }

    @Test
    void matchingOriginIsAllowed() {
        Cookie[] cookies = cookies(login("admin", TestData.PASSWORD));
        asAnonymous();

        assertThat(mvc.post().uri("/api/v1/admin/tags").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.ORIGIN, ORIGIN).cookie(cookies))
                .hasStatus(201);
    }

    @Test
    void missingOriginWithAuthCookiesIsRejected() {
        Cookie[] cookies = cookies(login("admin", TestData.PASSWORD));
        asAnonymous();

        assertThat(mvc.post().uri("/api/v1/admin/tags").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .cookie(cookies))
                .hasStatus(403);
    }

    @Test
    void missingOriginWithBearerOnlyIsAllowedForTools() {
        // ApiTestBase sends a Bearer header and no cookies.
        assertThat(post("/api/v1/admin/tags", BODY)).hasStatus(201);
    }

    @Test
    void getRequestsAreNotChecked() {
        MvcTestResult login = login("admin", TestData.PASSWORD);
        asAnonymous();

        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.ORIGIN, "https://evil.example").cookie(cookies(login)))
                .hasStatusOk();
    }
}
