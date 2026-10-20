package dev.kuratoon.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.kuratoon.config.KuratoonProperties;
import dev.kuratoon.user.AppUser;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** Issues HS256 access tokens (docs/spec/04-auth-users.md "Tokens"). Verification is in SecurityConfig. */
@Service
public class JwtService {

    public static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecretKey key;
    private final Clock clock;

    public JwtService(KuratoonProperties properties, Clock clock) {
        this.key = new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.clock = clock;
    }

    public SecretKey key() {
        return key;
    }

    public AccessToken issue(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ACCESS_TOKEN_LIFETIME);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(key));
            return new AccessToken(jwt.serialize(), expiresAt);
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign access token", e);
        }
    }

    public record AccessToken(String value, Instant expiresAt) {}
}
