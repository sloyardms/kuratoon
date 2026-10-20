package dev.kuratoon.common;

/** An action the caller is not allowed to perform (e.g. registration disabled). Mapped to 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
