package dev.kuratoon.ingestion;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chapter number and title from a chapter folder name (docs/spec/02-ingestion.md). */
public final class ChapterNameParser {

    /** {@code chapter} / {@code chap} / {@code ch}, not inside a longer word. */
    private static final Pattern KEYWORD = Pattern.compile("(?i)(?<![\\p{L}])(chapter|chap|ch)(?![\\p{L}])");

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    /** Leading whitespace, at most one separator, then whitespace again. */
    private static final Pattern TITLE_PREFIX = Pattern.compile("^\\s*[-–—:_]?\\s*");

    /** {@code chapter.number} is numeric(9,3). */
    private static final int MAX_SCALE = 3;
    private static final int MAX_PRECISION = 9;

    private ChapterNameParser() {}

    /** @param title null when the name has no text after the number */
    public record ParsedChapter(BigDecimal number, String title) {}

    /** Empty when the name has no usable number (the job then needs attention). */
    public static Optional<ParsedChapter> parse(String folderName) {
        Matcher keyword = KEYWORD.matcher(folderName);
        int searchFrom = keyword.find() ? keyword.end() : 0;
        Matcher number = NUMBER.matcher(folderName);
        if (!number.find(searchFrom)) {
            return Optional.empty();
        }
        BigDecimal value = new BigDecimal(number.group().replace(',', '.')).stripTrailingZeros();
        if (value.scale() < 0) {
            value = value.setScale(0);
        }
        if (value.scale() > MAX_SCALE || value.precision() - value.scale() > MAX_PRECISION - MAX_SCALE) {
            return Optional.empty();
        }
        String rest = folderName.substring(number.end());
        String title = TITLE_PREFIX.matcher(rest).replaceFirst("").strip();
        return Optional.of(new ParsedChapter(value, title.isEmpty() ? null : title));
    }

    /** Library folder for a chapter number: no trailing zeros ({@code 12}, {@code 12.5}, {@code 0}). */
    public static String canonicalFolder(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString();
    }
}
