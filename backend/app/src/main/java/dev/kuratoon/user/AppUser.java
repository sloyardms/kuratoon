package dev.kuratoon.user;

import dev.kuratoon.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

    /** Allowed username format (also a CHECK constraint): 3–32 of letters, digits, underscore, dot, hyphen. */
    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_.-]{3,32}$";

    public static final int MIN_PASSWORD_LENGTH = 10;

    @Column(nullable = false, columnDefinition = "citext")
    private String username;

    @Column(columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AppUser() {}

    public AppUser(String username, String email, String passwordHash, Role role) {
        this.username = Objects.requireNonNull(username);
        this.email = blankToNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** Invalidates every access token and is paired with refresh token revocation by callers. */
    public void bumpTokenVersion() {
        tokenVersion++;
    }

    public void changePassword(String newHash) {
        this.passwordHash = Objects.requireNonNull(newHash);
    }

    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void changeEmail(String email) {
        this.email = blankToNull(email);
    }

    public void loggedIn(Instant at) {
        this.lastLoginAt = at;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
