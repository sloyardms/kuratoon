package dev.kuratoon.auth;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.auth.RefreshTokenService.TokenPair;
import dev.kuratoon.common.ConflictException;
import dev.kuratoon.common.ForbiddenException;
import dev.kuratoon.settings.AppSettings;
import dev.kuratoon.user.AppUser;
import dev.kuratoon.user.AppUserRepository;
import dev.kuratoon.user.Role;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login, sessions, first-run setup and self-registration (docs/spec/04-auth-users.md). */
@Service
public class AuthService {

    /** Serializes everything that creates, removes or demotes admins (setup, bootstrap, users admin). */
    public static final String ADMIN_LOCK = "select pg_advisory_xact_lock(hashtext('kuratoon.admins'))";

    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final RefreshTokenService refreshTokens;
    private final AppSettings settings;
    private final MailStatus mail;
    private final AuditService audit;
    private final JdbcClient jdbc;
    private final Clock clock;
    /** Verified when the username is unknown, so unknown users cost the same as wrong passwords. */
    private final String dummyHash;

    public AuthService(
            AppUserRepository users,
            PasswordEncoder passwords,
            RefreshTokenService refreshTokens,
            AppSettings settings,
            MailStatus mail,
            AuditService audit,
            JdbcClient jdbc,
            Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
        this.settings = settings;
        this.mail = mail;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    /** Thrown for every failed login, whatever the reason, so responses never differ. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid username or password.");
        }
    }

    public record AuthStatus(boolean setupRequired, boolean registrationEnabled, boolean passwordResetEnabled) {}

    @Transactional(readOnly = true)
    public AuthStatus status() {
        return new AuthStatus(!users.existsByRole(Role.ADMIN), settings.registrationEnabled(), mail.available());
    }

    /**
     * Always verifies a password hash first (the user's, or a dummy one), then checks that the user
     * exists and is enabled, so failures look and cost the same.
     */
    @Transactional
    public LoginResult login(String username, String password, String ip, AuthRateLimiter limiter) {
        limiter.checkIp(ip);
        limiter.checkUsername(username, ip);
        Optional<AppUser> found = users.findByUsername(username == null ? "" : username.strip());
        String hash = found.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwords.matches(password == null ? "" : password, hash);
        if (!passwordMatches || found.isEmpty() || !found.get().isEnabled()) {
            limiter.loginFailed(username, ip);
            throw new InvalidCredentialsException();
        }
        AppUser user = found.get();
        user.loggedIn(clock.instant());
        return new LoginResult(user, refreshTokens.issueForLogin(user));
    }

    public record LoginResult(AppUser user, TokenPair tokens) {}

    /** "Log out everywhere": invalidates every access token and refresh token of the user. */
    @Transactional
    public void logoutEverywhere(UUID userId) {
        users.findById(userId).ifPresent(user -> {
            user.bumpTokenVersion();
            refreshTokens.revokeAllForUser(userId);
        });
    }

    /** First-run setup: creates the first ADMIN. Allowed only while no ADMIN exists. */
    @Transactional
    public AppUser setup(String username, String email, String password) {
        jdbc.sql(ADMIN_LOCK).query().listOfRows();
        if (users.existsByRole(Role.ADMIN)) {
            throw new ConflictException("Setup has already been completed.");
        }
        AppUser admin = createUser(username, email, password, Role.ADMIN);
        settings.setBoolean(AppSettings.SETUP_COMPLETED, true);
        audit.record("SETUP_COMPLETED", "user", admin.getId(), Map.of("username", admin.getUsername()));
        return admin;
    }

    /** Self-registration; enforced server-side by {@code registration.enabled}. */
    @Transactional
    public AppUser register(String username, String email, String password) {
        if (!settings.registrationEnabled()) {
            throw new ForbiddenException("Registration is disabled.");
        }
        return createUser(username, email, password, Role.USER);
    }

    /** Creates a user after uniqueness checks. Password rules are validated on the request DTO. */
    @Transactional
    public AppUser createUser(String username, String email, String password, Role role) {
        String name = username.strip();
        if (users.existsByUsername(name)) {
            throw new ConflictException("The username '" + name + "' is taken.");
        }
        if (email != null && !email.isBlank() && users.existsByEmailExcluding(email.strip(), null)) {
            throw new ConflictException("The email address is already in use.");
        }
        return users.save(new AppUser(name, email, passwords.encode(password), role));
    }
}
