package dev.kuratoon.settings;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.ApiTestBase;
import dev.kuratoon.user.AppUser;
import dev.kuratoon.user.Role;
import org.junit.jupiter.api.Test;

/** Settings page and audit log APIs (docs/spec/04-auth-users.md). */
class SettingsAndAuditIT extends ApiTestBase {

    @Test
    void settingsDefaultOffAndUpdateIsAudited() {
        assertThat(get("/api/v1/admin/settings")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.registrationEnabled").isEqualTo(false);
            assertThat(json).extractingPath("$.momentSubmissionsEnabled").isEqualTo(false);
        });

        assertThat(put("/api/v1/admin/settings", """
                {"registrationEnabled": true, "momentSubmissionsEnabled": false}
                """)).hasStatusOk().bodyJson().extractingPath("$.registrationEnabled").isEqualTo(true);

        assertThat(get("/api/v1/auth/status")).bodyJson().extractingPath("$.registrationEnabled").isEqualTo(true);
        assertThat(auditCount("SETTINGS_UPDATED")).isEqualTo(1);
        // No change, no audit entry.
        put("/api/v1/admin/settings", """
                {"registrationEnabled": true, "momentSubmissionsEnabled": false}
                """);
        assertThat(auditCount("SETTINGS_UPDATED")).isEqualTo(1);
        assertThat(put("/api/v1/admin/settings", "{}")).hasStatus(400);
    }

    @Test
    void statusPanel() {
        assertThat(get("/api/v1/admin/status")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.smtp.configured").isEqualTo(false);
            assertThat(json).extractingPath("$.smtp.ok").isEqualTo(false);
            assertThat(json).extractingPath("$.solver.configured").isEqualTo(true);
            assertThat(json).extractingPath("$.solver.reachable").isEqualTo(false);
            assertThat(json).extractingPath("$.plugins").asArray().isEmpty();
            assertThat(json).extractingPath("$.imageAccess").isEqualTo("SIGNED");
            assertThat(json).extractingPath("$.versions.java").asString().matches("\\d+.*");
            assertThat(json).extractingPath("$.versions.springBoot").asString().startsWith("4.");
            assertThat(json).extractingPath("$.versions.postgres").asString().startsWith("18.");
        });
    }

    @Test
    void auditLogIsNewestFirstWithFiltersAndActor() throws Exception {
        create("/api/v1/admin/tags", """
                {"name": "Action", "kind": "GENRE"}
                """);
        create("/api/v1/admin/creators", """
                {"name": "Oda Eiichiro"}
                """);
        AppUser other = data.aUser("second", Role.ADMIN);
        as(other);
        create("/api/v1/admin/tags", """
                {"name": "HQ", "kind": "LIBRARY"}
                """);
        as(admin);

        assertThat(get("/api/v1/admin/audit-log")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[*].action").asArray()
                    .containsExactly("TAG_CREATED", "CREATOR_CREATED", "TAG_CREATED");
            assertThat(json).extractingPath("$.items[0].actor.username").isEqualTo("second");
            assertThat(json).extractingPath("$.items[0].details.name").isEqualTo("HQ");
            assertThat(json).extractingPath("$.totalItems").isEqualTo(3);
        });
        assertThat(get("/api/v1/admin/audit-log?action=TAG_CREATED")).bodyJson().extractingPath("$.totalItems")
                .isEqualTo(2);
        assertThat(get("/api/v1/admin/audit-log?targetType=creator")).bodyJson()
                .extractingPath("$.items[*].action").asArray().containsExactly("CREATOR_CREATED");
        assertThat(get("/api/v1/admin/audit-log?actorId=" + other.getId())).bodyJson().extractingPath("$.totalItems")
                .isEqualTo(1);
        assertThat(get("/api/v1/admin/audit-log?size=1&page=1")).bodyJson().extractingPath("$.items[0].action")
                .isEqualTo("CREATOR_CREATED");
    }

    @Test
    void usersCannotReadSettingsOrAudit() {
        asUser();
        assertThat(get("/api/v1/admin/settings")).hasStatus(403);
        assertThat(get("/api/v1/admin/status")).hasStatus(403);
        assertThat(get("/api/v1/admin/audit-log")).hasStatus(403);
    }
}
