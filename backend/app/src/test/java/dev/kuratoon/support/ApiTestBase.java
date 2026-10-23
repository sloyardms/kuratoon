package dev.kuratoon.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.kuratoon.TestcontainersConfiguration;
import dev.kuratoon.auth.AuthRateLimiter;
import dev.kuratoon.auth.JwtService;
import dev.kuratoon.user.AppUser;
import dev.kuratoon.user.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Integration tests through the HTTP API against a real PostgreSQL (Testcontainers). Requests are
 * sent as a fresh ADMIN by default (Bearer header, so the Origin check does not apply); switch with
 * {@link #asUser()}, {@link #as(AppUser)} or {@link #asAnonymous()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
    TestcontainersConfiguration.class,
    TestData.class,
    DatabaseCleaner.class,
    ApiTestBase.ClockConfig.class
})
@ActiveProfiles("test")
public abstract class ApiTestBase {

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }

        @Bean
        @Primary
        TestFaults testFaults() {
            return new TestFaults();
        }
    }

    @DynamicPropertySource
    static void folders(DynamicPropertyRegistry registry) {
        TestFolders.register(registry);
    }

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TestData data;

    @Autowired
    protected TestClock clock;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected AuthRateLimiter rateLimiter;

    @Autowired
    protected TestFaults faults;

    @Autowired
    private DatabaseCleaner cleaner;

    /** The admin created for each test; the default caller. */
    protected AppUser admin;

    private String bearer;

    @BeforeEach
    void resetState() {
        cleaner.clean();
        rateLimiter.reset();
        clock.reset();
        faults.reset();
        TestFolders.reset();
        admin = data.aUser("admin", Role.ADMIN);
        as(admin);
    }

    /** Sends the following requests as this user. */
    protected void as(AppUser user) {
        bearer = jwtService.issue(user).value();
    }

    /** Sends the following requests as a new USER. */
    protected AppUser asUser() {
        AppUser user = data.aUser("reader" + UUID.randomUUID().toString().substring(0, 6), Role.USER);
        as(user);
        return user;
    }

    protected void asAnonymous() {
        bearer = null;
    }

    protected MvcTestResult get(String uri, Object... vars) {
        return auth(mvc.get().uri(uri, vars)).exchange();
    }

    protected MvcTestResult post(String uri, String json) {
        return auth(mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json)).exchange();
    }

    protected MvcTestResult put(String uri, String json) {
        return auth(mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json)).exchange();
    }

    protected MvcTestResult patch(String uri, String json) {
        return auth(mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json)).exchange();
    }

    protected MvcTestResult delete(String uri, Object... vars) {
        return auth(mvc.delete().uri(uri, vars)).exchange();
    }

    /** POSTs, asserts 201 and returns the created resource id. */
    protected UUID create(String uri, String json) throws Exception {
        MvcTestResult result = post(uri, json);
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).as("POST %s -> %s", uri, body).isEqualTo(201);
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected int auditCount(String action) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ?", Integer.class, action);
    }

    private MockMvcRequestBuilder auth(MockMvcRequestBuilder request) {
        return bearer == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
    }
}
