package dev.kuratoon.user;

import dev.kuratoon.auth.CurrentUser;
import dev.kuratoon.common.NotFoundException;
import dev.kuratoon.user.UserDtos.MeResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user. */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

    private final AppUserRepository users;

    MeController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping
    MeResponse me() {
        UUID id = CurrentUser.requireId();
        return users.findById(id).map(MeResponse::of).orElseThrow(() -> NotFoundException.of("User", id));
    }
}
