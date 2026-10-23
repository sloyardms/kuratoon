package dev.kuratoon;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.ApiTestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every phase 1 endpoint needs a login; admin endpoints need ADMIN (docs/spec/04-auth-users.md). */
class AccessRulesIT extends ApiTestBase {

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/admin/media-types",
        "/api/v1/admin/tags",
        "/api/v1/admin/creators",
        "/api/v1/admin/parodies",
        "/api/v1/admin/characters",
        "/api/v1/admin/users",
        "/api/v1/admin/settings",
        "/api/v1/admin/status",
        "/api/v1/admin/audit-log",
        "/api/v1/admin/imports",
        "/api/v1/admin/media"
    })
    void adminEndpointsNeedAnAdmin(String path) {
        asAnonymous();
        assertThat(get(path)).hasStatus(401);
        asUser();
        assertThat(get(path)).hasStatus(403);
        as(admin);
        assertThat(get(path)).hasStatusOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/media/00000000-0000-7000-8000-000000000000",
        "/api/v1/media/00000000-0000-7000-8000-000000000000/chapters",
        "/api/v1/chapters/00000000-0000-7000-8000-000000000000",
        "/api/v1/me"
    })
    void readEndpointsNeedALogin(String path) {
        asAnonymous();
        assertThat(get(path)).hasStatus(401);
        asUser();
        assertThat(get(path).getResponse().getStatus()).isIn(200, 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/actuator/env", "/something-else"})
    void everythingOutsideTheApiIsDenied(String path) {
        asAnonymous();
        assertThat(get(path)).hasStatus(401);
        asUser();
        assertThat(get(path)).hasStatus(403);
    }
}
