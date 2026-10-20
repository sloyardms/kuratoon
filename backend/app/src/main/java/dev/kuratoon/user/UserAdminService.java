package dev.kuratoon.user;

import dev.kuratoon.audit.AuditService;
import dev.kuratoon.auth.AuthService;
import dev.kuratoon.auth.CurrentUser;
import dev.kuratoon.auth.RefreshTokenService;
import dev.kuratoon.common.ConflictException;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.common.Queries;
import dev.kuratoon.user.UserDtos.CreateUserRequest;
import dev.kuratoon.user.UserDtos.UpdateUserRequest;
import dev.kuratoon.user.UserDtos.UserResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users page actions (docs/spec/04-auth-users.md "Users page"). An admin cannot delete or demote
 * themselves, and the last enabled admin cannot be removed, disabled or demoted. Admin-affecting
 * changes run under the shared admin lock so two admins cannot race past that rule.
 */
@Service
public class UserAdminService {

    private static final String TARGET = "user";

    private final AppUserRepository users;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;
    private final JdbcClient jdbc;

    public UserAdminService(
            AppUserRepository users,
            AuthService auth,
            PasswordEncoder passwords,
            RefreshTokenService refreshTokens,
            AuditService audit,
            JdbcClient jdbc) {
        this.users = users;
        this.auth = auth;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public OffsetPage<UserResponse> list(String q, PageParams paging) {
        Sort sort = Sort.by("username", "id");
        return OffsetPage.of(users.search(Queries.containsPattern(q), paging.toPageRequest(sort)), UserResponse::of);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.of(find(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        AppUser user = auth.createUser(request.username(), request.email(), request.password(), request.role());
        audit.record("USER_CREATED", TARGET, user.getId(), Map.of("username", user.getUsername(), "role", user.getRole()));
        return UserResponse.of(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        lockAdmins();
        AppUser user = find(id);
        UUID self = CurrentUser.requireId();
        Map<String, Object> changes = new LinkedHashMap<>();

        if (request.role() != null && request.role() != user.getRole()) {
            if (user.getRole() == Role.ADMIN) {
                if (id.equals(self)) {
                    throw new ConflictException("You cannot demote yourself.");
                }
                requireAnotherEnabledAdmin(user, "demoted");
            }
            changes.put("role", request.role());
            user.changeRole(request.role());
            endSessions(user); // the role claim of existing access tokens is now stale
        }
        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            if (!request.enabled() && user.getRole() == Role.ADMIN) {
                requireAnotherEnabledAdmin(user, "disabled");
            }
            changes.put("enabled", request.enabled());
            user.setEnabled(request.enabled());
            if (!request.enabled()) {
                endSessions(user);
            }
        }
        if (request.email() != null) {
            String email = request.email().isBlank() ? null : request.email().strip();
            if (email != null && users.existsByEmailExcluding(email, id)) {
                throw new ConflictException("The email address is already in use.");
            }
            changes.put("email", email == null ? "(removed)" : "(changed)");
            user.changeEmail(email);
        }
        if (!changes.isEmpty()) {
            audit.record("USER_UPDATED", TARGET, id, withUsername(user, changes));
        }
        return UserResponse.of(user);
    }

    /** Admin sets a new password (the without-SMTP reset path); ends all of the user's sessions. */
    @Transactional
    public void setPassword(UUID id, String password) {
        AppUser user = find(id);
        user.changePassword(passwords.encode(password));
        endSessions(user);
        audit.record("USER_PASSWORD_SET", TARGET, id, Map.of("username", user.getUsername()));
    }

    @Transactional
    public void revokeSessions(UUID id) {
        AppUser user = find(id);
        endSessions(user);
        audit.record("USER_SESSIONS_REVOKED", TARGET, id, Map.of("username", user.getUsername()));
    }

    /** Deletes the user and, through FK cascades, all of their personal data. */
    @Transactional
    public void delete(UUID id) {
        lockAdmins();
        AppUser user = find(id);
        if (id.equals(CurrentUser.requireId())) {
            throw new ConflictException("You cannot delete yourself.");
        }
        if (user.getRole() == Role.ADMIN) {
            requireAnotherEnabledAdmin(user, "deleted");
        }
        users.delete(user);
        audit.record("USER_DELETED", TARGET, id, Map.of("username", user.getUsername()));
    }

    private void requireAnotherEnabledAdmin(AppUser user, String action) {
        long enabledAdmins = users.countByRoleAndEnabledTrue(Role.ADMIN);
        long others = user.isEnabled() ? enabledAdmins - 1 : enabledAdmins;
        if (others < 1) {
            throw new ConflictException("The last admin cannot be " + action + ".");
        }
    }

    private void endSessions(AppUser user) {
        user.bumpTokenVersion();
        refreshTokens.revokeAllForUser(user.getId());
    }

    private void lockAdmins() {
        jdbc.sql(AuthService.ADMIN_LOCK).query().listOfRows();
    }

    private AppUser find(UUID id) {
        return users.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    private static Map<String, Object> withUsername(AppUser user, Map<String, Object> changes) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", user.getUsername());
        details.putAll(changes);
        return details;
    }
}
