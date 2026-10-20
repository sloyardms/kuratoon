package dev.kuratoon.auth;

import dev.kuratoon.common.BadRequestException;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.common.UuidV7;
import dev.kuratoon.config.KuratoonProperties;
import dev.kuratoon.user.AppUser;
import dev.kuratoon.user.AppUserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Self-service password reset by email (docs/spec/04-auth-users.md). Only available when the SMTP
 * startup check passed; otherwise the endpoints return 404. The request response never reveals
 * whether the email exists, and the mail is sent after commit on another thread.
 */
@Service
public class PasswordResetService {

    public static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MailStatus mail;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final RefreshTokenService refreshTokens;
    private final JdbcClient jdbc;
    private final TaskExecutor executor;
    private final Clock clock;
    private final String baseUrl;

    public PasswordResetService(
            MailStatus mail,
            AppUserRepository users,
            PasswordEncoder passwords,
            RefreshTokenService refreshTokens,
            JdbcClient jdbc,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor,
            Clock clock,
            KuratoonProperties properties) {
        this.mail = mail;
        this.users = users;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
        this.jdbc = jdbc;
        this.executor = executor;
        this.clock = clock;
        String url = properties.appBaseUrl().toString();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Creates a token and mails a link if an enabled user has this email; silently does nothing otherwise. */
    @Transactional
    public void request(String email) {
        requireAvailable();
        Optional<AppUser> user = users.findByEmail(email.strip()).filter(AppUser::isEnabled);
        if (user.isEmpty()) {
            return;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        jdbc.sql("""
                        insert into password_reset_token (id, user_id, token_hash, expires_at, created_at)
                        values (:id, :userId, :hash, :expiresAt, :now)
                        """)
                .param("id", UuidV7.generate())
                .param("userId", user.get().getId())
                .param("hash", RefreshTokenService.hash(raw))
                .param("expiresAt", Timestamp.from(now.plus(TOKEN_LIFETIME)))
                .param("now", Timestamp.from(now))
                .update();
        String to = user.get().getEmail();
        String link = baseUrl + "/reset-password?token=" + URLEncoder.encode(raw, StandardCharsets.UTF_8);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.execute(() -> send(to, link));
            }
        });
    }

    /** Sets the new password with a valid, unused, unexpired token, and ends every session of the user. */
    @Transactional
    public void confirm(String rawToken, String newPassword) {
        requireAvailable();
        Instant now = clock.instant();
        Optional<UUID> userId = jdbc.sql("""
                        select user_id from password_reset_token
                        where token_hash = :hash and used_at is null and expires_at > :now
                        for update
                        """)
                .param("hash", RefreshTokenService.hash(rawToken))
                .param("now", Timestamp.from(now))
                .query(UUID.class)
                .optional();
        AppUser user = userId.flatMap(users::findById)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new BadRequestException("This reset link is invalid or has expired."));
        user.changePassword(passwords.encode(newPassword));
        user.bumpTokenVersion();
        refreshTokens.revokeAllForUser(user.getId());
        // Single use: this and any other outstanding link for the user stop working.
        jdbc.sql("update password_reset_token set used_at = :now where user_id = :userId and used_at is null")
                .param("now", Timestamp.from(now))
                .param("userId", user.getId())
                .update();
    }

    private void requireAvailable() {
        if (!mail.available()) {
            throw new NotFoundException("Password reset by email is not available.");
        }
    }

    private void send(String to, String link) {
        try {
            mail.send(to, "Reset your Kuratoon password", """
                    Someone asked to reset the password of your Kuratoon account.

                    Open this link within 30 minutes to choose a new password:
                    %s

                    If this wasn't you, ignore this email; your password stays the same.
                    """.formatted(link));
        } catch (RuntimeException e) {
            log.warn("Could not send password reset email: {}", e.getMessage());
        }
    }
}
