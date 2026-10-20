package dev.kuratoon.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** URL/manifest slugs for media types and tags: {@code [a-z0-9]+(-[a-z0-9]+)*}. */
public final class Slugs {

    /** Allowed slug format; matches the CHECK constraints in the schema. */
    public static final String PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private Slugs() {}

    /**
     * ASCII-folds (accents dropped), lowercases and joins alphanumeric runs with {@code -}. Returns an
     * empty string when no Latin letters or digits remain (e.g. "漫画"); callers then require an
     * explicit slug.
     */
    public static String slugify(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFKD);
        String ascii = COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
        String joined = NON_ALNUM.matcher(ascii).replaceAll("-");
        int start = 0;
        int end = joined.length();
        while (start < end && joined.charAt(start) == '-') {
            start++;
        }
        while (end > start && joined.charAt(end - 1) == '-') {
            end--;
        }
        return joined.substring(start, end);
    }

    public static boolean isValid(String slug) {
        return slug != null && slug.matches(PATTERN);
    }

    /**
     * The explicit slug if given (already validated against {@link #PATTERN}), otherwise one derived
     * from the name.
     *
     * @throws BadRequestException if no slug is given and none can be derived (non-Latin name)
     */
    public static String resolve(String explicitSlug, String name) {
        if (explicitSlug != null && !explicitSlug.isBlank()) {
            return explicitSlug;
        }
        String derived = slugify(name);
        if (derived.isEmpty()) {
            throw new BadRequestException(
                    "A slug is required when the name has no Latin letters or digits (slug is required).");
        }
        return derived;
    }
}
