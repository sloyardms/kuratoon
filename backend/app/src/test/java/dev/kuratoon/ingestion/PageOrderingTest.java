package dev.kuratoon.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** docs/spec/02-ingestion.md "Page ordering". */
class PageOrderingTest {

    @Test
    void paddedAndUnpaddedNumbersSortNumerically() {
        assertThat(PageOrdering.sort(List.of("010.jpg", "2.jpg", "001.jpg", "03.jpg", "100.jpg", "11.jpg")))
                .containsExactly("001.jpg", "2.jpg", "03.jpg", "010.jpg", "11.jpg", "100.jpg");
    }

    @Test
    void sameNumberWithDifferentPaddingIsTieBrokenByNaturalOrder() {
        // 1 == 01 == 001: natural order of the full name decides (digit runs by value, so the
        // extension breaks the tie), then plain order.
        assertThat(PageOrdering.sort(List.of("01.webp", "001.png", "1.jpg")))
                .containsExactly("1.jpg", "001.png", "01.webp");
        assertThat(PageOrdering.sort(List.of("01.jpg", "1.jpg"))).containsExactly("01.jpg", "1.jpg");
    }

    @Test
    void pageNWithWordsUsesTheLastNumber() {
        assertThat(PageOrdering.sort(List.of("page 10.jpg", "page 2.jpg", "page 1.jpg")))
                .containsExactly("page 1.jpg", "page 2.jpg", "page 10.jpg");
        // "Chapter 3 page 12": the last number (12) is the page, not the chapter.
        assertThat(PageOrdering.sort(List.of("ch3 page 12.jpg", "ch3 page 9.jpg")))
                .containsExactly("ch3 page 9.jpg", "ch3 page 12.jpg");
    }

    @Test
    void decimalsSortBetweenIntegers() {
        assertThat(PageOrdering.sort(List.of("page 11.png", "page 10.5.png", "page 10.png")))
                .containsExactly("page 10.png", "page 10.5.png", "page 11.png");
    }

    @Test
    void filesWithoutANumberSortAfterNumberedOnesInNaturalOrder() {
        assertThat(PageOrdering.sort(List.of("zeta.jpg", "2.jpg", "Alpha.jpg", "1.jpg", "beta.jpg")))
                .containsExactly("1.jpg", "2.jpg", "Alpha.jpg", "beta.jpg", "zeta.jpg");
    }

    @Test
    void messyFixtureOrder() {
        // fixtures/import/series-messy page names, shuffled.
        assertThat(PageOrdering.sort(
                        List.of("extra.jpg", "page 10.5.png", "2.png", "01.webp", "page 10.jpg", "1.jpg")))
                .containsExactly("1.jpg", "01.webp", "2.png", "page 10.jpg", "page 10.5.png", "extra.jpg");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.jpg", "a.JPG", "a.jpeg", "a.png", "a.webp", "a.avif", "a.gif", "a.Png"})
    void acceptedExtensionsAreCaseInsensitive(String name) {
        assertThat(PageOrdering.isPage(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Thumbs.db", "thumbs.db", "desktop.ini", ".DS_Store", ".hidden.jpg", "notes.txt", "page.html",
        "kuratoon.yml", "link.url", "archive.zip", "page.bmp", "page.tiff", "noextension", "._001.jpg"
    })
    void junkIsIgnored(String name) {
        assertThat(PageOrdering.isPage(name)).isFalse();
    }

    @Test
    void sortSkipsJunk() {
        assertThat(PageOrdering.sort(List.of("Thumbs.db", "2.jpg", "desktop.ini", "1.jpg", "notes.txt")))
                .containsExactly("1.jpg", "2.jpg");
    }

    @Test
    void targetNamesAreFourDigitsKeepingTheLowercasedExtension() {
        assertThat(PageOrdering.targetName(0, "1.JPG")).isEqualTo("0001.jpg");
        assertThat(PageOrdering.targetName(9, "page 10.5.png")).isEqualTo("0010.png");
        assertThat(PageOrdering.targetName(9998, "x.webp")).isEqualTo("9999.webp");
        assertThat(PageOrdering.targetName(10_000, "x.jpeg")).isEqualTo("10001.jpeg");
    }
}
