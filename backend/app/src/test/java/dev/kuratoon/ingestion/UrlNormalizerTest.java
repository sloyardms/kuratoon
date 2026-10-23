package dev.kuratoon.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlNormalizerTest {

    @ParameterizedTest
    @CsvSource({
        "http://Fake-Manga.TEST/series/basic, http://fake-manga.test/series/basic",
        "HTTPS://fake-manga.test/series/basic/, https://fake-manga.test/series/basic",
        "http://fake-manga.test/series/basic#chapters, http://fake-manga.test/series/basic",
        "http://fake-manga.test:80/a, http://fake-manga.test/a",
        "https://fake-manga.test:443/a, https://fake-manga.test/a",
        "http://fake-manga.test:8080/a, http://fake-manga.test:8080/a",
        "http://fake-manga.test/, http://fake-manga.test",
        "http://fake-manga.test/a?utm_source=x&id=5&fbclid=y, http://fake-manga.test/a?id=5",
        "http://fake-manga.test/a?UTM_Medium=x&gclid=1, http://fake-manga.test/a",
        "http://fake-manga.test/Series/CaseKept, http://fake-manga.test/Series/CaseKept",
        "'  http://fake-manga.test/a  ', http://fake-manga.test/a"
    })
    void normalizes(String input, String expected) {
        assertThat(UrlNormalizer.normalize(input)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not a url", "ftp://fake-manga.test/a", "/relative/path", "http:///nohost"})
    void rejectsNonHttpUrls(String input) {
        assertThat(UrlNormalizer.normalize(input)).isEmpty();
    }
}
