package dev.kuratoon.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Missing chapters are the integer gaps between the lowest and highest chapter number; decimals are
 * never reported as missing (docs/spec/01-data-model.md), nor do they fill an integer gap.
 */
class MissingChaptersTest {

    @ParameterizedTest(name = "{0} -> [{1}]")
    @CsvSource(
            delimiter = '|',
            value = {
                "0 1 2 3 4 5|",
                "1 2 5|3 4",
                "1 2 2.5 3|",
                "1 2.5 3|2",
                "0.5 1 3|2",
                "0.5 0.7|",
                "5|",
                "|",
                "3 1 2 7|4 5 6",
                "10.000 12.500 13|11 12",
            })
    void computesIntegerGaps(String numbers, String expected) {
        List<BigDecimal> chapters = numbers == null
                ? List.of()
                : Arrays.stream(numbers.split(" ")).map(BigDecimal::new).toList();
        List<Integer> expectedGaps = expected == null
                ? List.of()
                : Arrays.stream(expected.split(" ")).map(Integer::valueOf).toList();

        assertThat(MissingChapters.compute(chapters)).isEqualTo(expectedGaps);
    }

    @ParameterizedTest
    @CsvSource({"10.000, 10", "12.500, 12.5", "0.000, 0", "1.250, 1.25"})
    void chapterNumbersAreRenderedWithoutTrailingZerosOrExponent(String stored, String shown) {
        assertThat(MissingChapters.displayNumber(new BigDecimal(stored)).toString()).isEqualTo(shown);
    }
}
