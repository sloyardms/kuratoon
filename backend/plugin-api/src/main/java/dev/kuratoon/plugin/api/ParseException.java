package dev.kuratoon.plugin.api;

/** Thrown by a plugin when a page cannot be parsed. The message is shown to the admin. */
public class ParseException extends Exception {
    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
