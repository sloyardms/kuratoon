package dev.kuratoon.auth;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.config.KuratoonProperties;
import dev.kuratoon.settings.AppSettings;
import dev.kuratoon.user.AppUser;
import dev.kuratoon.user.AppUserRepository;
import dev.kuratoon.user.Role;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Admin bootstrap from {@code ADMIN_USERNAME} / {@code ADMIN_PASSWORD} / {@code ADMIN_EMAIL} /
 * {@code ADMIN_FORCE_RESET} (docs/spec/04-auth-users.md "First run"):
 *
 * <ul>
 *   <li>no user with that name → create it as ADMIN (the setup wizard is skipped);
 *   <li>it exists and force reset is on → reset password, email (if given), role ADMIN, enabled, and
 *       end all its sessions;
 *   <li>it exists and force reset is off → nothing.
 * </ul>
 *
 * A WARN is logged on every boot while force reset is on.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final KuratoonProperties.Admin admin;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final RefreshTokenService refreshTokens;
    private final AppSettings settings;
    private final AuditService audit;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public AdminBootstrap(
            KuratoonProperties properties,
            AppUserRepository users,
            PasswordEncoder passwords,
            RefreshTokenService refreshTokens,
            AppSettings settings,
            AuditService audit,
            JdbcClient jdbc,
            TransactionTemplate tx) {
        this.admin = properties.admin();
        this.users = users;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
        this.settings = settings;
        this.audit = audit;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        run(admin);
    }

    /** Applies the bootstrap rules for the given settings (separate from {@link #run} for tests). */
    public void run(KuratoonProperties.Admin config) {
        if (config.forceReset()) {
            log.warn("ADMIN_FORCE_RESET is true: the admin '{}' is reset on every start. Remove ADMIN_FORCE_RESET after use.",
                    config.username());
        }
        if (!config.configured()) {
            return;
        }
        tx.executeWithoutResult(status -> {
            jdbc.sql(AuthService.ADMIN_LOCK).query().listOfRows();
            String username = config.username().strip();
            Optional<AppUser> existing = users.findByUsername(username);
            if (existing.isEmpty()) {
                AppUser created = users.save(new AppUser(username, config.email(), passwords.encode(config.password()), Role.ADMIN));
                settings.setBoolean(AppSettings.SETUP_COMPLETED, true);
                audit.record("ADMIN_BOOTSTRAPPED", "user", created.getId(), Map.of("username", username));
                log.info("Created admin '{}' from ADMIN_USERNAME.", username);
            } else if (config.forceReset()) {
                AppUser user = existing.get();
                user.changePassword(passwords.encode(config.password()));
                if (config.email() != null && !config.email().isBlank()) {
                    user.changeEmail(config.email());
                }
                user.changeRole(Role.ADMIN);
                user.setEnabled(true);
                user.bumpTokenVersion();
                refreshTokens.revokeAllForUser(user.getId());
                settings.setBoolean(AppSettings.SETUP_COMPLETED, true);
                audit.record("ADMIN_FORCE_RESET", "user", user.getId(), Map.of("username", username));
                log.warn("Reset admin '{}' because ADMIN_FORCE_RESET=true.", username);
            }
        });
    }
}
