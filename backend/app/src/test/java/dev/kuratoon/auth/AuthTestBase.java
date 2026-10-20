package dev.kuratoon.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.ApiTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Browser-style auth requests: cookies and the Origin header instead of a Bearer token. */
abstract class AuthTestBase extends ApiTestBase {

    /** APP_BASE_URL in application-test.yml. */
    static final String ORIGIN = "http://localhost:8080";

    @Autowired
    RefreshTokenService refreshTokens;

    MvcTestResult login(String username, String password) {
        return login(username, password, "127.0.0.1");
    }

    MvcTestResult login(String username, String password, String ip) {
        return browser(mvc.post().uri("/api/v1/auth/login"), ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password))
                .exchange();
    }

    MvcTestResult refresh(String refreshToken) {
        return browser(mvc.post().uri("/api/v1/auth/refresh"), "127.0.0.1")
                .cookie(new Cookie(AuthCookies.REFRESH, refreshToken))
                .exchange();
    }

    /** A same-origin browser request from {@code ip}. */
    MockMvcRequestBuilder browser(MockMvcRequestBuilder request, String ip) {
        return request.header(HttpHeaders.ORIGIN, ORIGIN).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    static List<String> setCookies(MvcTestResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    }

    static Optional<String> setCookie(MvcTestResult result, String name) {
        return setCookies(result).stream().filter(c -> c.startsWith(name + "=")).findFirst();
    }

    /** The value of a cookie set by the response; fails if it was not set. */
    static String cookieValue(MvcTestResult result, String name) {
        String header = setCookie(result, name).orElseThrow(() -> new AssertionError(name + " not set"));
        String value = header.substring(name.length() + 1, header.indexOf(';'));
        assertThat(value).as(name).isNotEmpty();
        return value;
    }

    static Cookie[] cookies(MvcTestResult result) {
        return Arrays.stream(new String[] {AuthCookies.ACCESS, AuthCookies.REFRESH})
                .map(name -> new Cookie(name, cookieValue(result, name)))
                .toArray(Cookie[]::new);
    }
}
