package dev.kuratoon.image;

/** libvips failed, timed out or is not installed. */
public class ImageToolException extends Exception {

    public ImageToolException(String message) {
        super(message);
    }

    public ImageToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
