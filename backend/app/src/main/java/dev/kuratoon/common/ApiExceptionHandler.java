package dev.kuratoon.common;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps exceptions to RFC 9457 Problem Details. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // PostgreSQL SQLStates (class 23: integrity constraint violation).
    static final String UNIQUE_VIOLATION = "23505";
    static final String FOREIGN_KEY_VIOLATION = "23503";
    static final String CHECK_VIOLATION = "23514";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Fallback for constraint violations not caught by service pre-checks (e.g. concurrent
     * requests). Pre-checks produce the friendlier messages.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataIntegrity(DataIntegrityViolationException e) {
        String sqlState = sqlState(e);
        log.debug("Data integrity violation (SQLState {})", sqlState, e);
        if (UNIQUE_VIOLATION.equals(sqlState)) {
            return problem(HttpStatus.CONFLICT, "A record with the same unique value already exists.");
        }
        if (FOREIGN_KEY_VIOLATION.equals(sqlState)) {
            return problem(HttpStatus.CONFLICT, "The record is referenced by, or references, other data.");
        }
        if (CHECK_VIOLATION.equals(sqlState)) {
            return problem(HttpStatus.BAD_REQUEST, "A value is outside its allowed range or format.");
        }
        log.error("Unexpected data integrity violation (SQLState {})", sqlState, e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected data error.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed.");
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::fieldError)
                .toList();
        body.setProperty("errors", errors);
        return handleExceptionInternal(e, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static Map<String, String> fieldError(FieldError error) {
        String message = error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage();
        return Map.of("field", error.getField(), "message", message);
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }

    static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
