package dev.kuratoon.common;

/** Helpers for text filters in queries. */
public final class Queries {

    /** Escape character used with {@code like ... escape '\'}. */
    public static final char LIKE_ESCAPE = '\\';

    private Queries() {}

    /** Escapes LIKE wildcards ({@code %}, {@code _}) and the escape character itself. */
    public static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** {@code %text%} with LIKE wildcards escaped, or null for blank input (no filter). */
    public static String containsPattern(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return "%" + escapeLike(text.strip()) + "%";
    }
}
