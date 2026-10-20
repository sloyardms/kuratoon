package dev.kuratoon.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Name normalization for matching creators, parodies and characters (docs/spec/01-data-model.md). */
public final class Names {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Names() {}

    /**
     * Trimmed, whitespace-collapsed, lowercased ({@link Locale#ROOT}). Also Unicode NFKC, so
     * full-width and compatibility characters match their plain forms.
     */
    public static String normalize(String name) {
        String nfkc = Normalizer.normalize(name, Normalizer.Form.NFKC);
        return WHITESPACE.matcher(nfkc.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
