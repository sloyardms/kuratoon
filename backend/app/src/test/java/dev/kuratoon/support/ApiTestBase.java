package dev.kuratoon.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.kuratoon.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Integration tests through the HTTP API against a real PostgreSQL (Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestData.class, DatabaseCleaner.class})
@ActiveProfiles("test")
public abstract class ApiTestBase {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TestData data;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void cleanDatabase() {
        cleaner.clean();
    }

    protected MvcTestResult get(String uri, Object... vars) {
        return mvc.get().uri(uri, vars).exchange();
    }

    protected MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected MvcTestResult put(String uri, String json) {
        return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected MvcTestResult delete(String uri, Object... vars) {
        return mvc.delete().uri(uri, vars).exchange();
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
}
