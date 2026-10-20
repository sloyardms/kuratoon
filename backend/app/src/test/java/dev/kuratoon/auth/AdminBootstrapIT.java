package dev.kuratoon.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.config.KuratoonProperties.Admin;
import dev.kuratoon.settings.AppSettings;
import dev.kuratoon.support.TestData;
import dev.kuratoon.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** ADMIN_* env bootstrap rules (docs/spec/04-auth-users.md "First run"). */
@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapIT extends AuthTestBase {

    @Autowired
    AdminBootstrap bootstrap;

    @Autowired
    AppSettings settings;

    @Test
    void createsTheAdminWhenNoUserHasThatName() {
        jdbc.update("delete from app_user");

        bootstrap.run(new Admin("boss", "bootstrap-pw-1", "boss@example.test", false));

        assertThat(jdbc.queryForObject("select role from app_user where username = 'boss'", String.class))
                .isEqualTo("ADMIN");
        assertThat(settings.getBoolean(AppSettings.SETUP_COMPLETED, false)).isTrue();
        assertThat(get("/api/v1/auth/status")).bodyJson().extractingPath("$.setupRequired").isEqualTo(false);
        assertThat(login("boss", "bootstrap-pw-1")).hasStatusOk();
    }

    @Test
    void existingUserWithoutForceResetIsLeftAlone() {
        data.aUser("boss", Role.USER);

        bootstrap.run(new Admin("boss", "bootstrap-pw-1", "new@example.test", false));

        assertThat(jdbc.queryForObject("select role from app_user where username = 'boss'", String.class))
                .isEqualTo("USER");
        assertThat(login("boss", TestData.PASSWORD)).hasStatusOk();
        assertThat(login("boss", "bootstrap-pw-1")).hasStatus(401);
    }

    @Test
    void forceResetRestoresTheAdminAndEndsItsSessions(CapturedOutput output) {
        var boss = data.aUser("boss", Role.USER);
        String oldRefresh = cookieValue(login("boss", TestData.PASSWORD), AuthCookies.REFRESH);
        jdbc.update("update app_user set enabled = false where id = ?", boss.getId());

        bootstrap.run(new Admin("boss", "bootstrap-pw-1", "reset@example.test", true));

        assertThat(jdbc.queryForMap("select role, enabled, email::text as email, token_version from app_user where username = 'boss'"))
                .containsEntry("role", "ADMIN")
                .containsEntry("enabled", true)
                .containsEntry("email", "reset@example.test")
                .containsEntry("token_version", 1);
        assertThat(login("boss", "bootstrap-pw-1")).hasStatusOk();
        assertThat(refresh(oldRefresh)).hasStatus(401);
        assertThat(output).contains("ADMIN_FORCE_RESET is true").contains("Remove ADMIN_FORCE_RESET after use");
    }

    @Test
    void warnsOnEveryBootWhileForceResetIsOn(CapturedOutput output) {
        bootstrap.run(new Admin("admin", "bootstrap-pw-1", null, true));
        bootstrap.run(new Admin("admin", "bootstrap-pw-1", null, true));

        assertThat(output.getOut().split("ADMIN_FORCE_RESET is true", -1)).hasSize(3);
    }

    @Test
    void notConfiguredDoesNothing() {
        bootstrap.run(new Admin(null, null, null, false));

        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(1);
    }
}
