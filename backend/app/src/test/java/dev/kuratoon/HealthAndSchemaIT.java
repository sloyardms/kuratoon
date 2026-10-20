package dev.kuratoon;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class HealthAndSchemaIT {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayBaselineCreatesRequiredExtensions() {
        List<String> extensions = jdbc.queryForList("select extname from pg_extension", String.class);
        assertThat(extensions).contains("pg_trgm", "citext");

        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void publicHealthEndpointReportsUpWithoutDetails() throws Exception {
        HttpResponse<String> response = get("/api/v1/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualToIgnoringWhitespace("{\"status\":\"UP\"}");
    }

    @Test
    void actuatorHealthReportsUpWithoutDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualToIgnoringWhitespace("{\"status\":\"UP\"}");
    }

    @Test
    void noOtherActuatorEndpointsAreExposed() throws Exception {
        for (String path : List.of(
                "/actuator",
                "/actuator/env",
                "/actuator/info",
                "/actuator/metrics",
                "/actuator/configprops",
                "/actuator/health/db")) {
            // Denied by security (401) or absent (404); never served. nginx does not route /actuator either.
            assertThat(get(path).statusCode()).as(path).isIn(401, 404);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
