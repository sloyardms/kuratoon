package dev.kuratoon.user;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.ApiTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Users page API (docs/spec/04-auth-users.md "Users page"). */
class UsersAdminIT extends ApiTestBase {

    private static final String BASE = "/api/v1/admin/users";

    @Autowired
    AppUserRepository users;

    @Test
    void listSearchAndPaging() {
        data.aUser("alice", Role.USER);
        data.aUser("bob", Role.USER);

        assertThat(get(BASE)).hasStatusOk().bodyJson().extractingPath("$.items[*].username").asArray()
                .containsExactly("admin", "alice", "bob");
        assertThat(get(BASE + "?q=ALI")).bodyJson().extractingPath("$.items[*].username").asArray()
                .containsExactly("alice");
        assertThat(get(BASE + "?q=bob@example")).bodyJson().extractingPath("$.items[*].username").asArray()
                .containsExactly("bob");
        assertThat(get(BASE + "?size=2&page=1")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[*].username").asArray().containsExactly("bob");
            assertThat(json).extractingPath("$.totalItems").isEqualTo(3);
        });
        assertThat(get(BASE)).bodyJson().doesNotHavePath("$.items[0].passwordHash");
    }

    @Test
    void createUserAndDuplicates() throws Exception {
        UUID id = create(BASE, """
                {"username": "carol", "email": "carol@example.test", "password": "carol-password", "role": "USER"}
                """);

        assertThat(get(BASE + "/{id}", id)).bodyJson().extractingPath("$.role").isEqualTo("USER");
        assertThat(post(BASE, """
                {"username": "CAROL", "password": "carol-password", "role": "USER"}
                """)).hasStatus(409);
        assertThat(post(BASE, """
                {"username": "carol2", "email": "Carol@Example.test", "password": "carol-password", "role": "USER"}
                """)).hasStatus(409);
        assertThat(auditActor("USER_CREATED")).isEqualTo(admin.getId());
    }

    @Test
    void roleChangeAndDisableEndSessions() {
        AppUser alice = data.aUser("alice", Role.USER);
        String aliceToken = jwtService.issue(alice).value();

        assertThat(patch(BASE + "/" + alice.getId(), """
                {"role": "ADMIN"}
                """)).hasStatusOk().bodyJson().extractingPath("$.role").isEqualTo("ADMIN");
        assertThat(meWith(aliceToken)).hasStatus(401);

        assertThat(patch(BASE + "/" + alice.getId(), """
                {"enabled": false}
                """)).hasStatusOk().bodyJson().extractingPath("$.enabled").isEqualTo(false);
        assertThat(auditCount("USER_UPDATED")).isEqualTo(2);
    }

    @Test
    void setPasswordAndRevokeSessionsBumpTheTokenVersion() {
        AppUser alice = data.aUser("alice", Role.USER);
        String token = jwtService.issue(alice).value();

        assertThat(post(BASE + "/" + alice.getId() + "/password", """
                {"password": "admin-chosen-pw"}
                """)).hasStatus(204);
        assertThat(meWith(token)).hasStatus(401);
        String second = jwtService.issue(reload(alice)).value();
        assertThat(meWith(second)).hasStatusOk();

        assertThat(post(BASE + "/" + alice.getId() + "/revoke-sessions", "{}")).hasStatus(204);
        assertThat(meWith(second)).hasStatus(401);
        assertThat(post(BASE + "/" + alice.getId() + "/password", """
                {"password": "short"}
                """)).hasStatus(400);
    }

    @Test
    void deleteCascadesPersonalData() {
        AppUser alice = data.aUser("alice", Role.USER);
        jdbc.update("insert into user_preference (user_id) values (?)", alice.getId());

        assertThat(delete(BASE + "/{id}", alice.getId())).hasStatus(204);

        assertThat(jdbc.queryForObject("select count(*) from user_preference", Integer.class)).isZero();
        assertThat(get(BASE + "/{id}", alice.getId())).hasStatus(404);
        assertThat(auditCount("USER_DELETED")).isEqualTo(1);
    }

    @Test
    void adminCannotDeleteOrDemoteThemselves() {
        data.aUser("second-admin", Role.ADMIN);

        assertThat(delete(BASE + "/{id}", admin.getId())).hasStatus(409).bodyJson().extractingPath("$.detail")
                .isEqualTo("You cannot delete yourself.");
        assertThat(patch(BASE + "/" + admin.getId(), """
                {"role": "USER"}
                """)).hasStatus(409).bodyJson().extractingPath("$.detail").isEqualTo("You cannot demote yourself.");
    }

    @Test
    void lastEnabledAdminCannotBeDisabled() {
        // The default admin is the only admin: disabling itself would leave none.
        assertThat(patch(BASE + "/" + admin.getId(), """
                {"enabled": false}
                """)).hasStatus(409).bodyJson().extractingPath("$.detail").isEqualTo("The last admin cannot be disabled.");

        AppUser other = data.aUser("other-admin", Role.ADMIN);
        assertThat(patch(BASE + "/" + other.getId(), """
                {"enabled": false}
                """)).hasStatusOk();
        // "other" is disabled now, so the default admin is again the last enabled one.
        assertThat(patch(BASE + "/" + admin.getId(), """
                {"enabled": false}
                """)).hasStatus(409);
        assertThat(get(BASE + "/{id}", admin.getId())).bodyJson().extractingPath("$.enabled").isEqualTo(true);
    }

    @Test
    void twoAdminsDemotingEachOtherConcurrentlyLeaveExactlyOneAdmin() throws Exception {
        AppUser other = data.aUser("other-admin", Role.ADMIN);
        String adminToken = jwtService.issue(admin).value();
        String otherToken = jwtService.issue(other).value();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            Future<MvcTestResult> a = pool.submit(() -> {
                start.await();
                return demote(adminToken, other.getId());
            });
            Future<MvcTestResult> b = pool.submit(() -> {
                start.await();
                return demote(otherToken, admin.getId());
            });
            start.countDown();
            statuses.add(a.get().getResponse().getStatus());
            statuses.add(b.get().getResponse().getStatus());
        } finally {
            pool.shutdownNow();
        }
        // Serialized by the admin lock: the second demotion would remove the last admin. (A 401 is also
        // acceptable for the loser if its own token was revoked before it authenticated.)
        assertThat(statuses).contains(200).hasSize(2);
        assertThat(statuses).allMatch(status -> status == 200 || status == 409 || status == 401);
        assertThat(jdbc.queryForObject("select count(*) from app_user where role = 'ADMIN'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void usersCannotUseTheAdminApi() {
        asUser();
        assertThat(get(BASE)).hasStatus(403);
        assertThat(post(BASE, """
                {"username": "x-user", "password": "long-enough-pw", "role": "ADMIN"}
                """)).hasStatus(403);
        assertThat(delete(BASE + "/{id}", admin.getId())).hasStatus(403);
    }

    private MvcTestResult demote(String token, UUID target) {
        return mvc.patch().uri(BASE + "/" + target)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"USER\"}")
                .exchange();
    }

    private MvcTestResult meWith(String token) {
        return mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    private AppUser reload(AppUser user) {
        return users.findById(user.getId()).orElseThrow();
    }

    private UUID auditActor(String action) {
        return jdbc.queryForObject("select actor_user_id from audit_log where action = ? limit 1", UUID.class, action);
    }
}
