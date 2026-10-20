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
        ImportPolling importPolling) {

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
}
