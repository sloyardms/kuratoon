package dev.kuratoon.auth;

import dev.kuratoon.auth.AuthRateLimiter.RateLimitedException;
import dev.kuratoon.auth.AuthService.AuthStatus;
import dev.kuratoon.auth.AuthService.InvalidCredentialsException;
import dev.kuratoon.auth.AuthService.LoginResult;
import dev.kuratoon.auth.RefreshTokenService.TokenPair;
import dev.kuratoon.user.AppUser;
import dev.kuratoon.user.AppUserRepository;
import dev.kuratoon.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/auth}: cookies are set and cleared here only (ADR 0013). */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService auth;
    private final RefreshTokenService refreshTokens;
    private final PasswordResetService passwordReset;
    private final AuthRateLimiter limiter;
    private final AuthCookies cookies;
    private final AppUserRepository users;

    AuthController(
            AuthService auth,
            RefreshTokenService refreshTokens,
            PasswordResetService passwordReset,
            AuthRateLimiter limiter,
            AuthCookies cookies,
            AppUserRepository users) {
        this.auth = auth;
        this.refreshTokens = refreshTokens;
        this.passwordReset = passwordReset;
        this.limiter = limiter;
        this.cookies = cookies;
        this.users = users;
    }

    record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 256) String password) {}

    /** Setup and registration share the account rules of spec 01/04. */
    record AccountRequest(
            @NotBlank @Pattern(regexp = AppUser.USERNAME_PATTERN, message = "must be 3-32 letters, digits, '_', '.' or '-'")
                    String username,
            @Email @Size(max = 254) String email,
            @NotBlank @Size(min = AppUser.MIN_PASSWORD_LENGTH, max = 256) String password) {}

    record ResetRequest(@NotBlank @Email @Size(max = 254) String email) {}

    record ResetConfirm(
            @NotBlank @Size(max = 256) String token,
            @NotBlank @Size(min = AppUser.MIN_PASSWORD_LENGTH, max = 256) String password) {}

    record SessionUser(UUID id, String username, Role role) {
        static SessionUser of(AppUser user) {
            return new SessionUser(user.getId(), user.getUsername(), user.getRole());
        }
    }

    record SessionResponse(SessionUser user, Instant accessExpiresAt) {}

    record CreatedAccount(UUID id, String username, Role role) {}

    @GetMapping("/status")
    AuthStatus status() {
        return auth.status();
    }

    @PostMapping("/login")
    SessionResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        LoginResult result = auth.login(request.username(), request.password(), http.getRemoteAddr(), limiter);
        cookies.set(response, result.tokens().accessToken(), result.tokens().refreshToken());
        return new SessionResponse(SessionUser.of(result.user()), result.tokens().accessExpiresAt());
    }

    @PostMapping("/refresh")
    ResponseEntity<?> refresh(HttpServletRequest http, HttpServletResponse response) {
        Optional<TokenPair> pair = AuthCookies.read(http, AuthCookies.REFRESH).flatMap(refreshTokens::rotate);
        if (pair.isEmpty()) {
            cookies.clear(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "The session has expired. Log in again."));
        }
        TokenPair tokens = pair.get();
        cookies.set(response, tokens.accessToken(), tokens.refreshToken());
        AppUser user = users.findById(tokens.userId()).orElseThrow();
        return ResponseEntity.ok(new SessionResponse(SessionUser.of(user), tokens.accessExpiresAt()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse response) {
        AuthCookies.read(http, AuthCookies.REFRESH).ifPresent(refreshTokens::revoke);
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(HttpServletResponse response) {
        auth.logoutEverywhere(CurrentUser.requireId());
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/setup")
    ResponseEntity<CreatedAccount> setup(@Valid @RequestBody AccountRequest request, HttpServletRequest http) {
        limiter.checkIp(http.getRemoteAddr());
        AppUser admin = auth.setup(request.username(), request.email(), request.password());
        return created(admin);
    }

    @PostMapping("/register")
    ResponseEntity<CreatedAccount> register(@Valid @RequestBody AccountRequest request, HttpServletRequest http) {
        limiter.checkIp(http.getRemoteAddr());
        AppUser user = auth.register(request.username(), request.email(), request.password());
        return created(user);
    }

    /** Always 202 with the same body, whether or not the email belongs to an account. */
    @PostMapping("/password-reset/request")
    ResponseEntity<ProblemDetail> requestReset(@Valid @RequestBody ResetRequest request, HttpServletRequest http) {
        limiter.checkIp(http.getRemoteAddr());
        passwordReset.request(request.email());
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.ACCEPTED,
                "If an account uses this email address, a reset link has been sent to it.");
        return ResponseEntity.accepted().body(body);
    }

    @PostMapping("/password-reset/confirm")
    ResponseEntity<Void> confirmReset(@Valid @RequestBody ResetConfirm request, HttpServletRequest http) {
        limiter.checkIp(http.getRemoteAddr());
        passwordReset.confirm(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ProblemDetail> rateLimited(RateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }

    private static ResponseEntity<CreatedAccount> created(AppUser user) {
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + user.getId()))
                .body(new CreatedAccount(user.getId(), user.getUsername(), user.getRole()));
    }
}
