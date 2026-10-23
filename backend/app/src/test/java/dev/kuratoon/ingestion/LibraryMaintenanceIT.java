package dev.kuratoon.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.chapter.Chapter;
import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.media.MediaRepository;
import dev.kuratoon.support.ImportTestBase;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Maintenance page: integrity scan, thumbnail regeneration, rebuild library from disk. */
class LibraryMaintenanceIT extends ImportTestBase {

    private static final UUID RESTORED = UUID.fromString("01a20000-0000-7000-8000-000000000001");

    @Autowired
    MediaRepository mediaRepository;

    @Autowired
    ChapterRepository chapterRepository;

    private UUID basic;

    @BeforeEach
    void importTwo() {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        basic = importFolder("series-basic").getMediaId();
        copyFixture("restore-snapshot");
        assertThat(importFolder("restore-snapshot").getStatus()).isEqualTo(ImportStatus.DONE);
    }

    @Test
    void cleanLibrary() {
        assertThat(post("/api/v1/admin/library/integrity-scan", "")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.mediaChecked").isEqualTo(2);
            assertThat(json).extractingPath("$.foldersWithoutRows").asArray().isEmpty();
            assertThat(json).extractingPath("$.rowsWithoutFolders").asArray().isEmpty();
            assertThat(json).extractingPath("$.missingPages").asArray().isEmpty();
            assertThat(json).extractingPath("$.missingThumbnails").asArray().hasSize(vipsAvailable() ? 0 : 2);
        });
    }

    @Test
    void reportsEveryKindOfInconsistency() throws Exception {
        // A row without its folder, a missing page, and a folder whose row is gone.
        Files.move(mediaDir(RESTORED), paths.libraryDir().resolve("moved-away"));
        Files.delete(mediaDir(basic).resolve("2/0003.jpg"));
        UUID orphan = data.aMedia("Orphan").persist().getId();
        Files.createDirectories(mediaDir(orphan));
        jdbc.update("delete from media where id = ?", orphan);

        assertThat(post("/api/v1/admin/library/integrity-scan", "")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.rowsWithoutFolders[0].id").isEqualTo(RESTORED.toString());
            assertThat(json).extractingPath("$.missingPages[0].chapter").isEqualTo("2");
            assertThat(json).extractingPath("$.missingPages[0].example").isEqualTo("0003.jpg");
            assertThat(json).extractingPath("$.foldersWithoutRows[0].folder").isEqualTo(orphan.toString());
            assertThat(json).extractingPath("$.foldersWithoutRows[0].hasSidecar").isEqualTo(false);
        });
    }

    @Test
    void regenerateThumbnails() {
        String body = "{\"mediaIds\": [\"" + basic + "\"]}";
        assertThat(post("/api/v1/admin/library/thumbnails", body)).hasStatusOk().bodyJson().satisfies(json -> {
            if (vipsAvailable()) {
                assertThat(json).extractingPath("$.regenerated[0].id").isEqualTo(basic.toString());
            } else {
                assertThat(json).extractingPath("$.failed").asArray().hasSize(1);
            }
        });
        assertThat(post("/api/v1/admin/library/thumbnails", "{\"mediaIds\": []}")).hasStatus(400);
        assertThat(auditCount("THUMBNAILS_REGENERATED")).isEqualTo(1);
    }

    @Test
    void rebuildRestoresMissingMediaWithRelationsAndDates() throws Exception {
        String relation = "{\"relations\": [{\"type\": \"SEQUEL\", \"mediaId\": \"" + RESTORED + "\"}]}";
        assertThat(put("/api/v1/admin/media/" + basic + "/relations", relation)).hasStatusOk();
        sidecars.flushPending();
        List<Instant> basicDates = chapterRepository.findByMediaIdOrdered(basic).stream().map(Chapter::getCreatedAt).toList();
        Files.createDirectories(paths.libraryDir().resolve(UUID.randomUUID().toString()));
        Files.createDirectories(paths.libraryDir().resolve(".deleted-" + UUID.randomUUID()));

        jdbc.execute("truncate table media, creator, tag, parody, character, import_job cascade");

        assertThat(post("/api/v1/admin/library/rebuild", "")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.restored").asArray().hasSize(2);
            assertThat(json).extractingPath("$.skipped").asArray().hasSize(1);
            assertThat(json).extractingPath("$.skipped[0].reason").asString().contains("No snapshot sidecar");
        });
        assertThat(mediaRepository.findById(basic)).isPresent();
        assertThat(mediaRepository.findById(RESTORED).orElseThrow().getLatestChapterAddedAt())
                .isEqualTo(Instant.parse("2026-02-11T10:00:00Z"));
        assertThat(chapterRepository.findByMediaIdOrdered(basic)).extracting(Chapter::getCreatedAt).isEqualTo(basicDates);
        assertThat(jdbc.queryForObject("select count(*) from media_relation where from_media_id = ? and to_media_id = ?",
                Integer.class, basic, RESTORED)).isEqualTo(1);
        assertThat(auditCount("LIBRARY_REBUILT")).isEqualTo(1);

        // Nothing is missing any more.
        assertThat(post("/api/v1/admin/library/rebuild", "")).bodyJson()
                .extractingPath("$.restored").asArray().isEmpty();
    }

    @Test
    void deletedMediaIsNotResurrected() {
        assertThat(delete("/api/v1/admin/media/{id}", basic)).hasStatus(204);
        assertThat(post("/api/v1/admin/library/rebuild", "")).bodyJson()
                .extractingPath("$.restored").asArray().isEmpty();
        assertThat(mediaRepository.existsById(basic)).isFalse();
    }

    @Test
    void onlyAdminsCanRunMaintenance() {
        asUser();
        assertThat(post("/api/v1/admin/library/integrity-scan", "")).hasStatus(403);
        assertThat(post("/api/v1/admin/library/rebuild", "")).hasStatus(403);
    }
}
