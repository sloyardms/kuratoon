package dev.kuratoon.common;

/** A semantically invalid request that bean validation cannot express. Mapped to 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
