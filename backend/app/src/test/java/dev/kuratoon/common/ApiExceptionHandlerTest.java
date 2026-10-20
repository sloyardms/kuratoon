package dev.kuratoon.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;

/**
 * Constraint violations that slip past the service pre-checks (e.g. two concurrent requests) are
 * mapped by PostgreSQL SQLState, wherever the SQLException sits in the cause chain.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @ParameterizedTest
    @CsvSource({
        "23505, 409", // unique_violation
        "23503, 409", // foreign_key_violation
        "23514, 400", // check_violation
        "23502, 500", // not_null_violation: a bug, not a client error
    })
    void mapsDataIntegrityViolationsBySqlState(String sqlState, int expectedStatus) {
        SQLException sql = new SQLException("constraint failed", sqlState);
        // Hibernate wraps the driver exception; the handler must walk the cause chain.
        RuntimeException hibernate = new RuntimeException("could not execute statement", sql);
        DataIntegrityViolationException e = new DataIntegrityViolationException("integrity", hibernate);

        ProblemDetail problem = handler.dataIntegrity(e);

        assertThat(problem.getStatus()).isEqualTo(expectedStatus);
        assertThat(problem.getDetail()).doesNotContain("constraint failed");
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void missingSqlStateIsServerError(boolean withCause) {
        DataIntegrityViolationException e = withCause
                ? new DataIntegrityViolationException("x", new RuntimeException("no sql"))
                : new DataIntegrityViolationException("x");

        assertThat(handler.dataIntegrity(e).getStatus()).isEqualTo(500);
    }
}
