package dev.kuratoon.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.ingestion.ChapterNameParser.ParsedChapter;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Chapter folder name parsing, docs/spec/02-ingestion.md "Chapter number and title parsing". */
class ChapterNameParserTest {

    // --- one test per row of the spec table ----------------------------------------------------

    @Test
    void chapter0Prologue() {
        assertParsed("Chapter 0 - Prologue", "0", "Prologue");
    }

    @Test
    void chapter25TheEnd() {
        assertParsed("Chapter 25 - The End", "25", "The End");
    }

    @Test
    void chapter012() {
        assertParsed("chapter 012", "12", null);
    }

    @Test
    void ch12Point5() {
        assertParsed("Ch.12.5", "12.5", null);
    }

    @Test
    void vol2Chapter10() {
        assertParsed("Vol 2 Chapter 10", "10", null);
    }

    @Test
    void s2Ch5Return() {
        assertParsed("S2 Ch 5 - Return", "5", "Return");
    }

    @Test
    void bare045() {
        assertParsed("045", "45", null);
    }

    @Test
    void extraHasNoNumber() {
        assertThat(ChapterNameParser.parse("Extra")).isEmpty();
    }

    // --- rules beyond the table ----------------------------------------------------------------

    @Test
    void commaDecimal() {
        assertParsed("Chapter 12,5", "12.5", null);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Chapter 3 – Dash|Dash",
        "Chapter 3 — Em|Em",
        "Chapter 3: Colon|Colon",
        "Chapter 3_Underscore|Underscore",
        "Chapter 3   Spaces  |Spaces",
        // One leading separator is stripped; a second one is part of the title.
        "Chapter 3 - - Twice|- Twice"
    })
    void leadingSeparatorAndWhitespaceAreStripped(String folder, String title) {
        assertParsed(folder, "3", title);
    }

    @Test
    void onlyASeparatorMeansNoTitle() {
        assertParsed("Chapter 3 - ", "3", null);
    }

    @Test
    void keywordIsCaseInsensitiveAndWholeWord() {
        assertParsed("CHAP 7", "7", null);
        assertParsed("ch. 8 - Dot", "8", "Dot");
        // "ch" inside a word is not the keyword: the first number in the name wins.
        assertParsed("Watch 3 Chapter 9", "9", null);
        assertParsed("Chaos 4", "4", null);
    }

    @Test
    void numberDirectlyAfterKeyword() {
        assertParsed("Chapter10", "10", null);
        assertParsed("ch12", "12", null);
    }

    @Test
    void zeroAndLeadingZeros() {
        assertParsed("000", "0", null);
        assertParsed("Chapter 007.50", "7.5", null);
    }

    @Test
    void keywordWithoutNumberFallsBackToNothing() {
        assertThat(ChapterNameParser.parse("Chapter Special")).isEmpty();
    }

    @Test
    void tooManyDecimalsIsNotANumber() {
        // numeric(9,3): more precision cannot be stored.
        assertThat(ChapterNameParser.parse("Chapter 1.2345")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"12, 12", "12.5, 12.5", "0, 0", "12.50, 12.5", "10, 10", "100.000, 100"})
    void canonicalFolderHasNoTrailingZeros(String number, String folder) {
        assertThat(ChapterNameParser.canonicalFolder(new BigDecimal(number))).isEqualTo(folder);
    }

    private static void assertParsed(String folder, String number, String title) {
        Optional<ParsedChapter> parsed = ChapterNameParser.parse(folder);
        assertThat(parsed).as(folder).isPresent();
        assertThat(parsed.get().number()).as(folder).isEqualByComparingTo(number);
        assertThat(parsed.get().title()).as(folder).isEqualTo(title);
    }
}
