package dev.kuratoon.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NamesAndSlugsTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Oda Eiichiro|oda eiichiro",
                "  Oda   Eiichiro  |oda eiichiro",
                "ODA\tEIICHIRO|oda eiichiro",
                // NFKC: full-width letters fold to ASCII
                "Ｏｄａ|oda",
                // Locale.ROOT: dotted capital I lowercases the same everywhere (not Turkish rules)
                "ICHIGO|ichigo",
                "尾田 栄一郎|尾田 栄一郎",
            })
    void normalizeTrimsCollapsesAndLowercases(String input, String expected) {
        assertThat(Names.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Manhwa|manhwa",
                "One-shot|one-shot",
                "Slice of Life|slice-of-life",
                "  Sci-Fi / Fantasy!! |sci-fi-fantasy",
                "Pokémon|pokemon",
                "Rescan needed|rescan-needed",
                "HQ|hq",
                "Ｍａｎｇａ|manga",
            })
    void slugifyProducesUrlSafeSlugs(String input, String expected) {
        assertThat(Slugs.slugify(input)).isEqualTo(expected).matches(Slugs.PATTERN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"漫画", "만화", "!!!", "   "})
    void slugifyYieldsEmptyWhenNothingLatinRemains(String input) {
        assertThat(Slugs.slugify(input)).isEmpty();
    }
}
