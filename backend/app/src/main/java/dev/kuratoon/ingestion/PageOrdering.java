package dev.kuratoon.ingestion;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which files are pages, their order and their library names (docs/spec/02-ingestion.md). */
public final class PageOrdering {

    public static final Set<String> PAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "avif", "gif");

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern CHUNK = Pattern.compile("\\d+|\\D+");

    /** Sort key: last number in the base name, then natural order, then plain order. */
    public static final Comparator<String> ORDER = Comparator.comparing(
                    PageOrdering::lastNumber, Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()))
            .thenComparing(PageOrdering::compareNatural)
            .thenComparing(Comparator.naturalOrder());

    private PageOrdering() {}

    /** True for an accepted image extension. Hidden files ({@code .*}) never count. */
    public static boolean isPage(String fileName) {
        return !fileName.startsWith(".") && PAGE_EXTENSIONS.contains(extension(fileName));
    }

    /** The page files of a folder listing in reading order; everything else is dropped. */
    public static List<String> sort(List<String> fileNames) {
        List<String> pages = new ArrayList<>();
        for (String name : fileNames) {
            if (isPage(name)) {
                pages.add(name);
            }
        }
        pages.sort(ORDER);
        return pages;
    }

    /** {@code 0001.<ext>} for the first page (index 0); the original extension, lowercased. */
    public static String targetName(int index, String originalName) {
        return String.format(Locale.ROOT, "%04d.%s", index + 1, extension(originalName));
    }

    /** Lowercased extension without the dot, or "" when there is none. */
    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private static BigDecimal lastNumber(String fileName) {
        Matcher m = NUMBER.matcher(baseName(fileName));
        String last = null;
        while (m.find()) {
            last = m.group();
        }
        return last == null ? null : new BigDecimal(last);
    }

    /** Digit runs compare by value, other runs case-insensitively. */
    static int compareNatural(String a, String b) {
        Matcher ma = CHUNK.matcher(a);
        Matcher mb = CHUNK.matcher(b);
        while (ma.find()) {
            if (!mb.find()) {
                return 1;
            }
            String ca = ma.group();
            String cb = mb.group();
            boolean da = Character.isDigit(ca.charAt(0));
            boolean db = Character.isDigit(cb.charAt(0));
            int c;
            if (da && db) {
                c = new BigInteger(ca).compareTo(new BigInteger(cb));
            } else {
                c = ca.compareToIgnoreCase(cb);
            }
            if (c != 0) {
                return c;
            }
        }
        return mb.find() ? -1 : 0;
    }
}
