package dev.kuratoon.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.settings.AppSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** First-run setup and self-registration (docs/spec/04-auth-users.md). */
class SetupAndRegistrationIT extends AuthTestBase {

    @Autowired
    AppSettings settings;

    private MvcTestResult setup(String username, String ip) {
        return browser(mvc.post().uri("/api/v1/auth/setup"), ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "email": "%s@example.test", "password": "first-admin-pw"}
                        """.formatted(username, username))
                .exchange();
    }

    private MvcTestResult register(String body) {
        return browser(mvc.post().uri("/api/v1/auth/register"), "127.0.0.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private void withoutAdmins() {
        jdbc.update("delete from app_user");
        asAnonymous();
    }

    @Test
    void statusReportsSetupRequiredUntilAnAdminExists() {
        withoutAdmins();
        assertThat(get("/api/v1/auth/status")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.setupRequired").isEqualTo(true);
            assertThat(json).extractingPath("$.registrationEnabled").isEqualTo(false);
            assertThat(json).extractingPath("$.passwordResetEnabled").isEqualTo(false);
        });

        assertThat(setup("owner", "127.0.0.1")).hasStatus(201).bodyJson().extractingPath("$.role").isEqualTo("ADMIN");

        assertThat(get("/api/v1/auth/status")).bodyJson().extractingPath("$.setupRequired").isEqualTo(false);
        assertThat(settings.getBoolean(AppSettings.SETUP_COMPLETED, false)).isTrue();
        assertThat(login("owner", "first-admin-pw")).hasStatusOk();
        assertThat(auditCount("SETUP_COMPLETED")).isEqualTo(1);
    }

    @Test
    void setupIsRefusedOnceAnAdminExists() {
        // ApiTestBase created "admin" (as an env bootstrap would have).
        assertThat(setup("intruder", "127.0.0.1")).hasStatus(409);
        assertThat(jdbc.queryForObject("select count(*) from app_user where username = 'intruder'", Integer.class))
                .isZero();
    }

    @Test
    void concurrentSetupsCreateExactlyOneAdmin() throws Exception {
        withoutAdmins();
        int parallel = 5;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<MvcTestResult>> futures = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                String name = "owner" + i;
                String ip = "10.9.0." + i;
                Callable<MvcTestResult> call = () -> {
                    start.await();
                    return setup(name, ip);
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            for (Future<MvcTestResult> f : futures) {
                statuses.add(f.get().getResponse().getStatus());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 409);
        assertThat(jdbc.queryForObject("select count(*) from app_user where role = 'ADMIN'", Integer.class)).isEqualTo(1);
    }

    @Test
    void registrationIsDisabledByDefaultEvenWhenCalledDirectly() {
        asAnonymous();
        assertThat(register("""
                {"username": "newbie", "password": "long-enough-pw"}
                """)).hasStatus(403).bodyJson().extractingPath("$.detail").isEqualTo("Registration is disabled.");
        assertThat(jdbc.queryForObject("select count(*) from app_user where username = 'newbie'", Integer.class)).isZero();
    }

    @Test
    void enabledRegistrationCreatesAUserWithRoleUser() {
        settings.setBoolean(AppSettings.REGISTRATION_ENABLED, true);
        asAnonymous();

        assertThat(register("""
                {"username": "newbie", "email": "newbie@example.test", "password": "long-enough-pw"}
                """)).hasStatus(201).bodyJson().extractingPath("$.role").isEqualTo("USER");
        assertThat(login("NEWBIE", "long-enough-pw")).hasStatusOk();
        // Usernames are unique case-insensitively.
        assertThat(register("""
                {"username": "NewBie", "password": "long-enough-pw"}
                """)).hasStatus(409);
    }

    @Test
    void passwordResetEndpointsAreNotFoundWithoutSmtp() {
        asAnonymous();
        assertThat(browser(mvc.post().uri("/api/v1/auth/password-reset/request"), "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@example.test\"}"))
                .hasStatus(404);
        assertThat(browser(mvc.post().uri("/api/v1/auth/password-reset/confirm"), "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"whatever\", \"password\": \"long-enough-pw\"}"))
                .hasStatus(404);
    }

    @Test
    void registrationValidatesUsernameAndPassword() {
        settings.setBoolean(AppSettings.REGISTRATION_ENABLED, true);
        asAnonymous();

        assertThat(register("""
                {"username": "no spaces!", "password": "short"}
                """)).hasStatus(400).bodyJson().extractingPath("$.errors[*].field").asArray()
                .contains("username", "password");
        assertThat(register("""
                {"username": "ab", "password": "long-enough-pw"}
                """)).hasStatus(400);
        assertThat(register("""
                {"username": "valid_name", "email": "not-an-email", "password": "long-enough-pw"}
                """)).hasStatus(400);
    }
}
