package dev.kuratoon.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.media.MediaInvariants;
import dev.kuratoon.support.ImportTestBase;
import dev.kuratoon.support.TestFolders;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Imports admin page API: list, detail and every action. */
class ImportAdminIT extends ImportTestBase {

    private static final String MANUAL = """
            {"title": "One Shot", "type": "one-shot", "authors": ["Manual Author"], "year": 2024,
             "genres": ["Drama"], "characters": [{"name": "Hero", "parody": null}]}
            """;

    @Test
    void listAndDetailShowStatusChapterTableAndActions() {
        copyFixture("oneshot");
        UUID id = importFolder("oneshot").getId();

        assertThat(get("/api/v1/admin/imports?status=NEEDS_ATTENTION")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.totalItems").isEqualTo(1);
            assertThat(json).extractingPath("$.items[0].sourceFolder").isEqualTo("oneshot");
            assertThat(json).extractingPath("$.items[0].attention").isEqualTo("NO_PLUGIN");
            assertThat(json).extractingPath("$.items[0].chapterCount").isEqualTo(1);
            assertThat(json).extractingPath("$.items[0].actions").asArray()
                    .containsExactly("RETRY", "FILL_METADATA", "DISMISS");
        });
        assertThat(get("/api/v1/admin/imports?status=DONE")).bodyJson().extractingPath("$.totalItems").isEqualTo(0);

        assertThat(get("/api/v1/admin/imports/{id}", id)).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.url").isEqualTo("http://fake-manga.test/oneshot/one");
            assertThat(json).extractingPath("$.chapters[0].pageCount").isEqualTo(5);
            assertThat(json).extractingPath("$.missing").asArray().containsExactly("type", "authors or artists");
            assertThat(json).extractingPath("$.committed").isEqualTo(false);
        });
        assertThat(get("/api/v1/admin/imports/{id}", UUID.randomUUID())).hasStatus(404);
    }

    @Test
    void fillMetadataManuallyImportsAndLocksTheFields() {
        copyFixture("oneshot");
        UUID id = importFolder("oneshot").getId();

        assertThat(put("/api/v1/admin/imports/" + id + "/metadata", MANUAL)).hasStatusOk()
                .bodyJson().extractingPath("$.job.status").isEqualTo("QUEUED");
        assertThat(auditCount("IMPORT_METADATA_ENTERED")).isEqualTo(1);
        worker.processAll();

        ImportJob job = reload(id);
        assertThat(job.getStatus()).as(job.getMessage()).isEqualTo(ImportStatus.DONE);
        List<String> locked = jdbc.queryForObject(
                "select array_to_string(locked_fields, ',') from media where id = ?", String.class, job.getMediaId())
                .lines().flatMap(l -> List.of(l.split(",")).stream()).toList();
        assertThat(locked).containsExactly("title", "type", "year", "authors", "genres", "characters");
        assertThat(get("/api/v1/admin/imports/{id}", id)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.job.mediaTitle").isEqualTo("One Shot");
            assertThat(json).extractingPath("$.job.actions").asArray().isEmpty();
        });
    }

    @Test
    void manualMetadataNeedsTitleTypeAndAnAuthorOrArtist() {
        copyFixture("oneshot");
        UUID id = importFolder("oneshot").getId();
        String uri = "/api/v1/admin/imports/" + id + "/metadata";
        assertThat(put(uri, "{\"title\": \"T\", \"type\": \"manga\"}")).hasStatus(400)
                .bodyJson().extractingPath("$.detail").isEqualTo(MediaInvariants.AUTHOR_OR_ARTIST_REQUIRED);
        assertThat(put(uri, "{\"title\": \"T\", \"type\": \"scroll\", \"artists\": [\"A\"]}")).hasStatus(400);
        assertThat(put(uri, "{\"type\": \"manga\", \"artists\": [\"A\"]}")).hasStatus(400);
        assertThat(reload(id).getStatus()).isEqualTo(ImportStatus.NEEDS_ATTENTION);
    }

    @Test
    void resolveConflictsNeedsAChoiceForEachChapter() {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        importFolder("series-basic");
        copyFixture("series-conflict");
        UUID id = importFolder("series-conflict").getId();
        String uri = "/api/v1/admin/imports/" + id + "/conflicts";

        assertThat(post(uri, "{\"choices\": {\"9\": \"SKIP\"}}")).hasStatus(400);
        assertThat(post(uri, "{\"choices\": {}}")).hasStatus(400);
        assertThat(post(uri, "{\"choices\": {\"3\": \"REPLACE\"}}")).hasStatusOk();
        worker.processAll();
        assertThat(reload(id).getStatus()).isEqualTo(ImportStatus.DONE);
        assertThat(auditCount("IMPORT_CONFLICT_RESOLVED")).isEqualTo(1);
        // Done jobs cannot be changed.
        assertThat(post("/api/v1/admin/imports/" + id + "/retry", "")).hasStatus(409);
    }

    @Test
    void resolveDuplicateCreateNew() {
        String noUrl = BASIC_COMPLETE.replace("url: http://fake-manga.test/series/basic\n", "");
        writeManifest(copyFixture("series-basic", "first"), noUrl);
        importFolder("first");
        writeManifest(copyFixture("series-update", "second"), noUrl);
        UUID id = importFolder("second").getId();

        assertThat(get("/api/v1/admin/imports/{id}", id)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.duplicate.title").isEqualTo("Basic Series");
            assertThat(json).extractingPath("$.job.actions").asArray().contains("RESOLVE_DUPLICATE");
        });
        assertThat(post("/api/v1/admin/imports/" + id + "/conflicts", "{\"choices\": {\"6\": \"SKIP\"}}")).hasStatus(409);
        assertThat(post("/api/v1/admin/imports/" + id + "/duplicate", "{\"choice\": \"CREATE_NEW\"}")).hasStatusOk();
        worker.processAll();
        assertThat(reload(id).getKind()).isEqualTo(ImportKind.NEW);
        assertThat(reload(id).getStatus()).isEqualTo(ImportStatus.DONE);
    }

    @Test
    void retryRunsTheJobAgainFromTheFolder() throws Exception {
        copyFixture("series-no-number");
        UUID id = importFolder("series-no-number").getId();
        // The admin fixes the folder name, then retries.
        Files.move(TestFolders.IMPORT.resolve("series-no-number/Extra"),
                TestFolders.IMPORT.resolve("series-no-number/Chapter 1.5 - Extra"));
        assertThat(post("/api/v1/admin/imports/" + id + "/retry", "")).hasStatusOk();
        worker.processAll();
        ImportJob job = reload(id);
        assertThat(job.getStatus()).isEqualTo(ImportStatus.NEEDS_ATTENTION);
        assertThat(job.detail().attention()).isEqualTo(JobDetail.Attention.NO_PLUGIN);
        assertThat(job.getAttempts()).isEqualTo(2);
        assertThat(auditCount("IMPORT_RETRIED")).isEqualTo(1);
    }

    @Test
    void dismissRenamesTheFolderAndFinishesTheJob() {
        copyFixture("series-no-number");
        UUID id = importFolder("series-no-number").getId();
        assertThat(post("/api/v1/admin/imports/" + id + "/dismiss", "")).hasStatusOk()
                .bodyJson().extractingPath("$.job.status").isEqualTo("DISMISSED");
        ImportJob job = reload(id);
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(listNames(TestFolders.IMPORT)).containsExactly(".dismissed-series-no-number-" + id);
        // The poller ignores it, even after the stability window.
        scanner.scanOnce();
        clock.advance(Duration.ofMinutes(5));
        assertThat(scanner.scanOnce()).isEmpty();
        assertThat(post("/api/v1/admin/imports/" + id + "/dismiss", "")).hasStatus(409);
        assertThat(auditCount("IMPORT_DISMISSED")).isEqualTo(1);
    }

    @Test
    void aCommittedJobCannotBeDismissedButRetryFinishesIt() throws Exception {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        ImportJob queued = queue("series-basic");
        faults.failAt(ImportFaults.Step.AFTER_MOVE);
        worker.processAll();
        ImportJob job = reload(queued.getId());
        assertThat(job.getStatus()).isEqualTo(ImportStatus.NEEDS_ATTENTION);
        assertThat(job.detail().attention()).isEqualTo(JobDetail.Attention.RECOVERY);

        assertThat(post("/api/v1/admin/imports/" + job.getId() + "/dismiss", "")).hasStatus(409);
        assertThat(get("/api/v1/admin/imports/{id}", job.getId())).bodyJson()
                .extractingPath("$.job.actions").asArray().containsExactly("RETRY");
        assertThat(post("/api/v1/admin/imports/" + job.getId() + "/retry", "")).hasStatusOk();
        worker.processAll();
        ImportJob done = reload(job.getId());
        assertThat(done.getStatus()).as(done.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(jdbc.queryForObject("select count(*) from media", Integer.class)).isEqualTo(1);
    }
}
