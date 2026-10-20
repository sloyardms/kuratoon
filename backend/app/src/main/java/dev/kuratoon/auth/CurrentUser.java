package dev.kuratoon.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The authenticated user of the current request, from the verified access token. */
public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<UUID> id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return Optional.of(UUID.fromString(jwt.getToken().getSubject()));
        }
        return Optional.empty();
    }

    public static UUID requireId() {
        return id().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
