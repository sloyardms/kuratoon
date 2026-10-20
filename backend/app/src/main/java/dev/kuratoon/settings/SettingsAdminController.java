package dev.kuratoon.settings;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.auth.MailStatus;
import dev.kuratoon.config.KuratoonProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settings page API (docs/spec/04-auth-users.md "Settings page"). */
@RestController
@RequestMapping("/api/v1/admin")
class SettingsAdminController {

    private static final Duration SOLVER_TIMEOUT = Duration.ofSeconds(2);

    private final AppSettings settings;
    private final AuditService audit;
    private final MailStatus mail;
    private final KuratoonProperties properties;
    private final JdbcClient jdbc;
    private final ObjectProvider<BuildProperties> build;

    SettingsAdminController(
            AppSettings settings,
            AuditService audit,
            MailStatus mail,
            KuratoonProperties properties,
            JdbcClient jdbc,
            ObjectProvider<BuildProperties> build) {
        this.settings = settings;
        this.audit = audit;
        this.mail = mail;
        this.properties = properties;
        this.jdbc = jdbc;
        this.build = build;
    }

    record SettingsResponse(boolean registrationEnabled, boolean momentSubmissionsEnabled) {}

    record SettingsRequest(@NotNull Boolean registrationEnabled, @NotNull Boolean momentSubmissionsEnabled) {}

    record SmtpStatus(boolean configured, boolean ok, String error) {}

    record SolverStatus(boolean configured, boolean reachable) {}

    record Versions(String app, String java, String springBoot, String postgres) {}

    /** @param plugins loaded metadata plugins; empty until plugin loading exists (phase 4) */
    record StatusResponse(
            SmtpStatus smtp, SolverStatus solver, List<Object> plugins, String imageAccess, Versions versions) {}

    @GetMapping("/settings")
    SettingsResponse get() {
        return current();
    }

    @PutMapping("/settings")
    @Transactional
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        SettingsResponse before = current();
        settings.setBoolean(AppSettings.REGISTRATION_ENABLED, request.registrationEnabled());
        settings.setBoolean(AppSettings.MOMENT_SUBMISSIONS_ENABLED, request.momentSubmissionsEnabled());
        SettingsResponse after = current();
        Map<String, Object> changes = new LinkedHashMap<>();
        if (before.registrationEnabled() != after.registrationEnabled()) {
            changes.put(AppSettings.REGISTRATION_ENABLED, after.registrationEnabled());
        }
        if (before.momentSubmissionsEnabled() != after.momentSubmissionsEnabled()) {
            changes.put(AppSettings.MOMENT_SUBMISSIONS_ENABLED, after.momentSubmissionsEnabled());
        }
        if (!changes.isEmpty()) {
            audit.record("SETTINGS_UPDATED", "settings", null, changes);
        }
        return after;
    }

    @GetMapping("/status")
    StatusResponse status() {
        Optional<URI> solver = Optional.ofNullable(properties.solver().url());
        return new StatusResponse(
                new SmtpStatus(mail.configured(), mail.available(), mail.error()),
                new SolverStatus(solver.isPresent(), solver.map(SettingsAdminController::reachable).orElse(false)),
                List.of(),
                properties.imageAccess().name(),
                new Versions(
                        Optional.ofNullable(build.getIfAvailable()).map(BuildProperties::getVersion).orElse("dev"),
                        Runtime.version().toString(),
                        SpringBootVersion.getVersion(),
                        jdbc.sql("select current_setting('server_version')").query(String.class).single()));
    }

    private SettingsResponse current() {
        return new SettingsResponse(settings.registrationEnabled(), settings.momentSubmissionsEnabled());
    }

    /** Any HTTP response counts as reachable; only connection errors and timeouts do not. */
    private static boolean reachable(URI url) {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(SOLVER_TIMEOUT).build()) {
            HttpRequest request = HttpRequest.newBuilder(url).timeout(SOLVER_TIMEOUT).GET().build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
