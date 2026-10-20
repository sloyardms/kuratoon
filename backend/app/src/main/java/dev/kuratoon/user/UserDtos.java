package dev.kuratoon.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response bodies of the users admin API and {@code /api/v1/me}. */
public final class UserDtos {

    private UserDtos() {}

    public record UserResponse(
            UUID id,
            String username,
            String email,
            Role role,
            boolean enabled,
            Instant createdAt,
            Instant lastLoginAt) {
        public static UserResponse of(AppUser u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRole(), u.isEnabled(),
                    u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record MeResponse(UUID id, String username, String email, Role role) {
        public static MeResponse of(AppUser u) {
            return new MeResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRole());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = AppUser.USERNAME_PATTERN, message = "must be 3-32 letters, digits, '_', '.' or '-'")
                    String username,
            @Email @Size(max = 254) String email,
            @NotBlank @Size(min = AppUser.MIN_PASSWORD_LENGTH, max = 256) String password,
            @NotNull Role role) {}

    /** Partial update: null fields stay unchanged. {@code email: ""} removes the email. */
    public record UpdateUserRequest(Role role, Boolean enabled, @Email @Size(max = 254) String email) {}

    public record SetPasswordRequest(@NotBlank @Size(min = AppUser.MIN_PASSWORD_LENGTH, max = 256) String password) {}
}
