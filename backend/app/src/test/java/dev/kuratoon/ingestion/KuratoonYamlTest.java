package dev.kuratoon.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kuratoon.chapter.PageEntry;
import dev.kuratoon.ingestion.KuratoonYaml.Cover;
import dev.kuratoon.ingestion.KuratoonYaml.InvalidDocumentException;
import dev.kuratoon.ingestion.KuratoonYaml.ManifestDocument;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotChapter;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotDocument;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotRelation;
import dev.kuratoon.ingestion.MediaFields.CharacterEntry;
import dev.kuratoon.media.CoverCrop;
import dev.kuratoon.media.PublicationStatus;
import dev.kuratoon.media.RelationType;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KuratoonYamlTest {

    @Test
    void fullManifest() throws Exception {
        ManifestDocument m = (ManifestDocument) KuratoonYaml.parse("""
                kuratoon: 1
                kind: manifest
                url: https://example.site/series/foo
                type: manhwa
                language: en
                adult: false
                title: Foo
                titles: [ "Foo (Alt)" ]
                status: ONGOING
                year: 2023
                description: A story.
                authors: [ "Name" ]
                artists: [ "Name", "Other" ]
                genres: [ "Action", "Fantasy" ]
                tags: [ "hq" ]
                parodies: [ "P" ]
                characters:
                  - { name: "C", parody: "P" }
                """);
        assertThat(m.url()).isEqualTo("https://example.site/series/foo");
        MediaFields f = m.fields();
        assertThat(f.title()).isEqualTo("Foo");
        assertThat(f.titles()).containsExactly("Foo (Alt)");
        assertThat(f.type()).isEqualTo("manhwa");
        assertThat(f.status()).isEqualTo(PublicationStatus.ONGOING);
        assertThat(f.year()).isEqualTo(2023);
        assertThat(f.adult()).isFalse();
        assertThat(f.artists()).containsExactly("Name", "Other");
        assertThat(f.characters()).containsExactly(new CharacterEntry("C", "P"));
        assertThat(f.isComplete()).isTrue();
        // Every set field is locked; url, kuratoon and kind are not fields.
        assertThat(m.setFields()).containsExactlyInAnyOrderElementsOf(MediaFields.FIELD_NAMES);
        assertThat(m.warnings()).isEmpty();
    }

    @Test
    void onlySetFieldsAreLocked() throws Exception {
        ManifestDocument m = (ManifestDocument) KuratoonYaml.parse("""
                kuratoon: 1
                url: http://fake-manga.test/series/basic
                title: Override
                year: null
                """);
        assertThat(m.setFields()).containsExactly("title");
        assertThat(m.fields().isComplete()).isFalse();
        assertThat(m.fields().missingForCreate()).containsExactly("type", "authors or artists");
    }

    @Test
    void kindDefaultsToManifestAndStatusIsCaseInsensitive() throws Exception {
        ManifestDocument m = (ManifestDocument) KuratoonYaml.parse("kuratoon: 1\nstatus: completed\n");
        assertThat(m.fields().status()).isEqualTo(PublicationStatus.COMPLETED);
    }

    @Test
    void unknownKeysAreWarnings() throws Exception {
        ManifestDocument m = (ManifestDocument) KuratoonYaml.parse("kuratoon: 1\nvolume: 3\n");
        assertThat(m.warnings()).containsExactly("Unknown key 'volume' ignored.");
    }

    @Test
    void schemaVersionIsRequiredAndChecked() {
        assertInvalid("url: http://x.test/a\n", "kuratoon: 1");
        assertInvalid("kuratoon: 2\n", "schema version 2");
        assertInvalid("kuratoon: one\n", "kuratoon: 1");
    }

    @Test
    void invalidDocumentsAreReported() {
        assertInvalid("kuratoon: 1\nkind: sidecar\n", "Unknown kind");
        assertInvalid("- a\n- b\n", "mapping");
        assertInvalid("kuratoon: 1\nyear: soon\n", "invalid value");
        assertInvalid("kuratoon: 1\nstatus: SOMETIMES\n", "invalid value");
        assertInvalid("kuratoon: 1\n  bad: [indent\n", "not valid YAML");
    }

    @Test
    void sourceTxtIsAUrlOnlyManifest() throws Exception {
        ManifestDocument m = KuratoonYaml.fromSourceTxt("\n  http://fake-manga.test/oneshot/one  \n");
        assertThat(m.url()).isEqualTo("http://fake-manga.test/oneshot/one");
        assertThat(m.setFields()).isEmpty();
        assertThat(m.fields()).isEqualTo(MediaFields.EMPTY);
        assertThatThrownBy(() -> KuratoonYaml.fromSourceTxt("  \n"))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void restoreFixtureSnapshotParses() throws Exception {
        Path file = Path.of("../../fixtures/import/restore-snapshot/kuratoon.yml");
        SnapshotDocument s = (SnapshotDocument) KuratoonYaml.parse(Files.readString(file));
        assertThat(s.id()).isEqualTo(UUID.fromString("01a20000-0000-7000-8000-000000000001"));
        assertThat(s.plugin()).isEqualTo("example");
        assertThat(s.locked()).containsExactly("title");
        assertThat(s.cover()).isEqualTo(new Cover("cover.jpg", new CoverCrop(0.1, 0, 0.8, 0.8)));
        assertThat(s.chapters()).extracting(SnapshotChapter::folder).containsExactly("1", "2", "2.5");
        assertThat(s.chapters().get(1).title()).isNull();
        assertThat(s.chapters().get(2).createdAt()).isEqualTo(Instant.parse("2026-02-11T10:00:00Z"));
        assertThat(s.chapters().getFirst().pages()).first().isEqualTo(new PageEntry("0001.jpg", 800, 1200));
        assertThat(s.warnings()).isEmpty();
    }

    @Test
    void snapshotRoundTrips() throws Exception {
        SnapshotDocument original = new SnapshotDocument(
                UUID.fromString("01a20000-0000-7000-8000-0000000000aa"),
                "http://fake-manga.test/series/x",
                null,
                new MediaFields("Title: with colon", List.of("Alt"), "Line one.\nLine two.", "manga", "ja",
                        PublicationStatus.HIATUS, 1999, true, List.of("A"), List.of(), List.of("Drama"), List.of("hq"),
                        List.of("P"), List.of(new CharacterEntry("C", null))),
                List.of("title", "type"),
                new Cover("cover.png", null),
                Instant.parse("2026-10-19T00:00:00Z"),
                List.of(
                        new SnapshotChapter(new BigDecimal("0.000"), "Prologue", "0", Instant.parse("2026-10-19T01:00:00Z"),
                                List.of(new PageEntry("0001.jpg", 800, 12000))),
                        new SnapshotChapter(new BigDecimal("10"), null, "10", Instant.parse("2026-10-20T01:00:00Z"),
                                List.of(new PageEntry("0001.webp", 640, 960), new PageEntry("0002.webp", 640, 960)))),
                List.of(new SnapshotRelation(RelationType.SEQUEL, UUID.fromString("01a20000-0000-7000-8000-0000000000bb"))),
                List.of());

        String yaml = KuratoonYaml.write(original);
        assertThat(yaml).startsWith("kuratoon: 1\nkind: snapshot\n");
        assertThat(yaml).doesNotContain("1E+1");
        SnapshotDocument parsed = (SnapshotDocument) KuratoonYaml.parse(yaml);
        assertThat(parsed).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(original);
    }

    @Test
    void invalidSnapshotsAreReported() {
        assertInvalid("kuratoon: 1\nkind: snapshot\ntitle: T\ntype: manga\n", "no id");
        assertInvalid("""
                kuratoon: 1
                kind: snapshot
                id: 01a20000-0000-7000-8000-000000000001
                title: T
                type: manga
                created_at: 2026-01-01T00:00:00Z
                chapters:
                  - { number: 1, folder: "1", created_at: 2026-01-01T00:00:00Z, pages: [] }
                """, "has no pages");
    }

    private static void assertInvalid(String yaml, String message) {
        assertThatThrownBy(() -> KuratoonYaml.parse(yaml))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining(message);
    }
}
