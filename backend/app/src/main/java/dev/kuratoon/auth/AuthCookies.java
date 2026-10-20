package dev.kuratoon.auth;

import dev.kuratoon.config.KuratoonProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The two auth cookies (ADR 0013): both httpOnly, SameSite=Lax, Path=/, Secure when APP_BASE_URL is
 * https. The refresh cookie is only read by the {@code /api/v1/auth} endpoints.
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "kuratoon_access";
    public static final String REFRESH = "kuratoon_refresh";

    private final boolean secure;

    public AuthCookies(KuratoonProperties properties) {
        this.secure = properties.secureCookies();
    }

    public void set(HttpServletResponse response, String accessToken, String refreshToken) {
        add(response, ACCESS, accessToken, JwtService.ACCESS_TOKEN_LIFETIME);
        add(response, REFRESH, refreshToken, RefreshTokenService.REFRESH_TOKEN_LIFETIME);
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS, "", Duration.ZERO);
        add(response, REFRESH, "", Duration.ZERO);
    }

    public static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    public static boolean hasAuthCookie(HttpServletRequest request) {
        return read(request, ACCESS).isPresent() || read(request, REFRESH).isPresent();
    }

    private void add(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
