package dev.kuratoon.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Finds the access token in the {@code kuratoon_access} cookie, else in {@code Authorization: Bearer}
 * (tests and tools). Returns none on the public auth and health endpoints, so an expired access
 * cookie never blocks login, refresh or logout. The refresh cookie is never read here.
 */
class AccessTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        if (SecurityConfig.isPublicEndpoint(request)) {
            return null;
        }
        return AuthCookies.read(request, AuthCookies.ACCESS).orElseGet(() -> header.resolve(request));
    }
}
