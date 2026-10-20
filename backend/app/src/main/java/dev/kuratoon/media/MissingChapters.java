package dev.kuratoon.media;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Chapter number helpers: computed missing-chapter warning and display form. */
public final class MissingChapters {

    private MissingChapters() {}

    /**
     * Integer chapter numbers between the lowest and highest existing chapter that have no chapter.
     * Decimal chapters (12.5) are never reported as missing and do not fill an integer gap.
     */
    public static List<Integer> compute(Collection<BigDecimal> numbers) {
        if (numbers.isEmpty()) {
            return List.of();
        }
        BigDecimal min = numbers.stream().min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = numbers.stream().max(BigDecimal::compareTo).orElseThrow();
        int from = min.setScale(0, RoundingMode.CEILING).intValueExact();
        int to = max.setScale(0, RoundingMode.FLOOR).intValueExact();
        Set<Integer> present = numbers.stream()
                .filter(n -> n.stripTrailingZeros().scale() <= 0)
                .map(BigDecimal::intValueExact)
                .collect(Collectors.toSet());
        List<Integer> missing = new ArrayList<>();
        for (int n = from; n <= to; n++) {
            if (!present.contains(n)) {
                missing.add(n);
            }
        }
        return missing;
    }

    /** {@code 10.000} → {@code 10}, {@code 12.500} → {@code 12.5}; never exponent notation. */
    public static BigDecimal displayNumber(BigDecimal number) {
        BigDecimal stripped = number.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
