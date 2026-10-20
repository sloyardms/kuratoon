package dev.kuratoon.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings bound from the environment variables in {@code docs/spec/08-deployment.md}
 * (mapped in {@code application.yml}). Invalid or missing required values fail startup.
 */
@Validated
@ConfigurationProperties("kuratoon")
public record KuratoonProperties(
        @NotNull(message = "APP_BASE_URL is required (public URL of the app, e.g. https://manga.example.com)")
        URI appBaseUrl,

        @NotBlank(message = "JWT_SECRET is required (at least 32 bytes)")
        String jwtSecret,

        @DefaultValue("signed")
        ImageAccess imageAccess,

        String imageSigningSecret,

        @DefaultValue("en")
        @NotBlank
        String defaultLanguage,

        @DefaultValue
        @Valid
        Paths paths,

        @DefaultValue
        @Valid
        Solver solver,

        @DefaultValue
        @Valid
        ImportPolling importPolling,

        @DefaultValue
        @Valid
        Admin admin,

        @DefaultValue
        @Valid
        Smtp smtp) {

    static final int MIN_JWT_SECRET_BYTES = 32;

    @AssertTrue(message = "JWT_SECRET must be at least 32 bytes")
    public boolean isJwtSecretLongEnough() {
        return jwtSecret == null
                || jwtSecret.isBlank()
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length >= MIN_JWT_SECRET_BYTES;
    }

    @AssertTrue(message = "IMAGE_SIGNING_SECRET is required when IMAGE_ACCESS=signed")
    public boolean isImageSigningSecretPresentWhenSigned() {
        return imageAccess != ImageAccess.SIGNED || (imageSigningSecret != null && !imageSigningSecret.isBlank());
    }

    /** Container paths of the mounted volumes. */
    public record Paths(
            @DefaultValue("/import") @NotNull Path importDir,
            @DefaultValue("/library") @NotNull Path libraryDir,
            @DefaultValue("/data") @NotNull Path dataDir,
            @DefaultValue("/plugins") @NotNull Path pluginsDir) {}

    /**
     * FlareSolverr-compatible challenge solver.
     *
     * @param endpoint empty disables the solver. Bound as a string because an empty URI would fall
     *     back to the default instead of disabling.
     */
    public record Solver(
            @DefaultValue("http://byparr:8191") String endpoint,
            @DefaultValue("60000ms") @NotNull Duration timeout) {

        public boolean enabled() {
            return endpoint != null && !endpoint.isBlank();
        }

        /** The solver URL, or null when disabled. */
        public URI url() {
            return enabled() ? URI.create(endpoint.strip()) : null;
        }
    }

    /** Import folder polling and stability detection. */
    public record ImportPolling(
            @DefaultValue("30s") @NotNull Duration pollInterval,
            @DefaultValue("60s") @NotNull Duration stableAfter) {}

    /** Origin ({@code scheme://host[:port]}) of {@link #appBaseUrl()}: the only supported access URL. */
    public String appOrigin() {
        String scheme = appBaseUrl.getScheme().toLowerCase(java.util.Locale.ROOT);
        int port = appBaseUrl.getPort();
        // Browsers omit default ports in the Origin header.
        boolean defaultPort = port == -1 || ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
        return scheme + "://" + appBaseUrl.getHost().toLowerCase(java.util.Locale.ROOT) + (defaultPort ? "" : ":" + port);
    }

    /** Cookies are {@code Secure} when the public URL is https. */
    public boolean secureCookies() {
        return "https".equalsIgnoreCase(appBaseUrl.getScheme());
    }

    /**
     * Optional admin bootstrap ({@code ADMIN_*}, docs/spec/04-auth-users.md "First run").
     *
     * @param forceReset reset the existing admin on every boot while true
     */
    public record Admin(String username, String password, String email, @DefaultValue("false") boolean forceReset) {

        public boolean configured() {
            return username != null && !username.isBlank();
        }

        @AssertTrue(message = "ADMIN_USERNAME must be 3-32 characters of letters, digits, '_', '.' or '-'")
        public boolean isUsernameValid() {
            return !configured() || username.strip().matches("^[a-zA-Z0-9_.-]{3,32}$");
        }

        @AssertTrue(message = "ADMIN_PASSWORD is required (at least 10 characters) when ADMIN_USERNAME is set")
        public boolean isPasswordValid() {
            return !configured() || (password != null && password.length() >= 10);
        }

        @AssertTrue(message = "ADMIN_FORCE_RESET=true requires ADMIN_USERNAME and ADMIN_PASSWORD")
        public boolean isForceResetValid() {
            return !forceReset || configured();
        }
    }

    /**
     * SMTP for password reset ({@code SMTP_*}). Unset host disables mail.
     *
     * @param tls {@code true}: STARTTLS required; anything else: plain connection
     */
    public record Smtp(
            String host,
            @DefaultValue("587") int port,
            String username,
            String password,
            String from,
            @DefaultValue("false") boolean tls) {

        public boolean configured() {
            return host != null && !host.isBlank();
        }

        @AssertTrue(message = "SMTP_FROM is required when SMTP_HOST is set")
        public boolean isFromPresentWhenConfigured() {
            return !configured() || (from != null && !from.isBlank());
        }
    }
}
