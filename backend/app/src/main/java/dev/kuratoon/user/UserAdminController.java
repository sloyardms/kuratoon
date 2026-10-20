package dev.kuratoon.user;

import dev.kuratoon.common.OffsetPage;
import dev.kuratoon.common.PageParams;
import dev.kuratoon.user.UserDtos.CreateUserRequest;
import dev.kuratoon.user.UserDtos.SetPasswordRequest;
import dev.kuratoon.user.UserDtos.UpdateUserRequest;
import dev.kuratoon.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Users page API (docs/spec/04-auth-users.md). */
@RestController
@RequestMapping("/api/v1/admin/users")
class UserAdminController {

    private final UserAdminService service;

    UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    OffsetPage<UserResponse> list(@RequestParam(required = false) String q, @Valid PageParams paging) {
        return service.list(q, paging);
    }

    @GetMapping("/{id}")
    UserResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/password")
    ResponseEntity<Void> setPassword(@PathVariable UUID id, @Valid @RequestBody SetPasswordRequest request) {
        service.setPassword(id, request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/revoke-sessions")
    ResponseEntity<Void> revokeSessions(@PathVariable UUID id) {
        service.revokeSessions(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
