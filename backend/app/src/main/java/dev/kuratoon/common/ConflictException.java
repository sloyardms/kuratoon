package dev.kuratoon.common;

/** A request that conflicts with existing data (duplicate, still in use). Mapped to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
