package dev.kuratoon.auth;

import dev.kuratoon.user.AppUserRepository;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects access tokens of disabled or deleted users and tokens whose {@code tv} claim no longer
 * matches {@code app_user.token_version} ("log out everywhere", password reset, admin revoke, role
 * change). One primary-key lookup per request.
 */
class TokenVersionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The session has been revoked.", null);

    private final AppUserRepository users;

    TokenVersionValidator(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID userId;
        Number tokenVersion = jwt.getClaim(JwtService.CLAIM_TOKEN_VERSION);
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        if (tokenVersion == null) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        boolean valid = users.findById(userId)
                .filter(user -> user.isEnabled() && user.getTokenVersion() == tokenVersion.intValue())
                .isPresent();
        return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(REVOKED);
    }
}
