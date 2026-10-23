package dev.kuratoon.media;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.ingestion.ImportJob;
import dev.kuratoon.ingestion.ImportStatus;
import dev.kuratoon.ingestion.KuratoonYaml;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotDocument;
import dev.kuratoon.support.ImportTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/** Media edit page API: field locking, invariant, relations, cover, deletes. */
class MediaAdminIT extends ImportTestBase {

    @Autowired
    MediaRepository mediaRepository;

    @Autowired
    ChapterRepository chapterRepository;

    private UUID mediaId;

    @BeforeEach
    void importBasic() {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        ImportJob job = importFolder("series-basic");
        assertThat(job.getStatus()).as(job.getMessage()).isEqualTo(ImportStatus.DONE);
        mediaId = job.getMediaId();
    }

    @Test
    void listSearchesTitlesAndFiltersByType() {
        data.aMedia("Other Thing").type("manhwa").persist();
        assertThat(get("/api/v1/admin/media?q=basic")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.totalItems").isEqualTo(1);
            assertThat(json).extractingPath("$.items[0].title").isEqualTo("Basic Series");
            assertThat(json).extractingPath("$.items[0].chapterCount").isEqualTo(6);
            assertThat(json).extractingPath("$.items[0].type.slug").isEqualTo("manga");
        });
        UUID manhwa = data.mediaType("manhwa").getId();
        assertThat(get("/api/v1/admin/media?type=" + manhwa)).bodyJson()
                .extractingPath("$.items[0].title").isEqualTo("Other Thing");
        assertThat(get("/api/v1/admin/media?q=%25")).bodyJson().extractingPath("$.totalItems").isEqualTo(0);
    }

    @Test
    void editViewHasValuesLocksCoverAndChapters() {
        assertThat(get("/api/v1/admin/media/{id}", mediaId)).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.values.title").isEqualTo("Basic Series");
            assertThat(json).extractingPath("$.values.authors").asArray().containsExactly("Basic Author");
            assertThat(json).extractingPath("$.lockedFields").asArray().containsExactly("title", "type", "authors");
            assertThat(json).extractingPath("$.cover.file").isEqualTo("cover.jpg");
            assertThat(json).extractingPath("$.cover.width").isEqualTo(800);
            assertThat(json).extractingPath("$.chapters").asArray().hasSize(6);
        });
    }

    @Test
    void editLocksEachEditedFieldAndWritesTheSidecar() throws Exception {
        String body = """
                {"fields": ["description", "genres", "status"],
                 "values": {"description": "New text.", "genres": ["Drama", "Action"], "status": "COMPLETED"}}
                """;
        assertThat(patch("/api/v1/admin/media/" + mediaId, body)).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.values.description").isEqualTo("New text.");
            assertThat(json).extractingPath("$.values.genres").asArray().containsExactly("Action", "Drama");
            assertThat(json).extractingPath("$.lockedFields").asArray()
                    .containsExactly("title", "description", "type", "status", "authors", "genres");
        });
        assertThat(auditCount("MEDIA_UPDATED")).isEqualTo(1);
        // Debounced: written after the window (flushed here).
        assertThat(sidecars.hasPending(mediaId)).isTrue();
        sidecars.flushPending();
        SnapshotDocument sidecar = (SnapshotDocument) KuratoonYaml.parse(Files.readString(sidecars.sidecar(mediaId)));
        assertThat(sidecar.fields().description()).isEqualTo("New text.");
        assertThat(sidecar.locked()).contains("genres");
    }

    @Test
    void editCannotRemoveTheLastAuthorOrArtist() {
        String body = "{\"fields\": [\"authors\", \"artists\"], \"values\": {\"authors\": [], \"artists\": []}}";
        assertThat(patch("/api/v1/admin/media/" + mediaId, body)).hasStatus(400)
                .bodyJson().extractingPath("$.detail").isEqualTo(MediaInvariants.AUTHOR_OR_ARTIST_REQUIRED);
        // Swapping the author for an artist is fine.
        String swap = "{\"fields\": [\"authors\", \"artists\"], \"values\": {\"authors\": [], \"artists\": [\"Ink\"]}}";
        assertThat(patch("/api/v1/admin/media/" + mediaId, swap)).hasStatusOk();
    }

    @Test
    void editValidation() {
        String uri = "/api/v1/admin/media/" + mediaId;
        assertThat(patch(uri, "{\"fields\": [\"type\"], \"values\": {\"type\": \"scroll\"}}")).hasStatus(400);
        assertThat(patch(uri, "{\"fields\": [\"title\"], \"values\": {\"title\": \" \"}}")).hasStatus(400);
        assertThat(patch(uri, "{\"fields\": [\"volume\"], \"values\": {}}")).hasStatus(400);
        assertThat(patch(uri, "{\"fields\": [], \"values\": {}}")).hasStatus(400);
        assertThat(patch(uri, "{\"fields\": [\"type\"], \"values\": {\"type\": \"Manhwa\"}}")).hasStatusOk()
                .bodyJson().extractingPath("$.values.type").isEqualTo("manhwa");
    }

    @Test
    void lockedFieldsCanBeUnlockedIndividually() {
        assertThat(put("/api/v1/admin/media/" + mediaId + "/locked-fields", "{\"fields\": [\"type\"]}"))
                .hasStatusOk().bodyJson().extractingPath("$.lockedFields").asArray().containsExactly("type");
        assertThat(put("/api/v1/admin/media/" + mediaId + "/locked-fields", "{\"fields\": [\"bogus\"]}")).hasStatus(400);
        assertThat(auditCount("MEDIA_LOCKS_CHANGED")).isEqualTo(1);
    }

    @Test
    void relationsAreStoredOnThisMediaAndShownFromBothSides() throws Exception {
        UUID sequel = data.aMedia("Basic Series 2").persist().getId();
        String body = "{\"relations\": [{\"type\": \"SEQUEL\", \"mediaId\": \"" + sequel + "\"}]}";
        assertThat(put("/api/v1/admin/media/" + mediaId + "/relations", body)).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.relations[0].direction").isEqualTo("OUTGOING");
            assertThat(json).extractingPath("$.relations[0].label").isEqualTo("SEQUEL");
        });
        assertThat(get("/api/v1/admin/media/{id}", sequel)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.relations[0].direction").isEqualTo("INCOMING");
            assertThat(json).extractingPath("$.relations[0].label").isEqualTo("PREQUEL");
        });
        sidecars.flushPending();
        SnapshotDocument sidecar = (SnapshotDocument) KuratoonYaml.parse(Files.readString(sidecars.sidecar(mediaId)));
        assertThat(sidecar.relations()).singleElement().satisfies(r -> assertThat(r.mediaId()).isEqualTo(sequel));

        String self = "{\"relations\": [{\"type\": \"SEQUEL\", \"mediaId\": \"" + mediaId + "\"}]}";
        assertThat(put("/api/v1/admin/media/" + mediaId + "/relations", self)).hasStatus(400);
        String reverse = "{\"relations\": [{\"type\": \"SPIN_OFF\", \"mediaId\": \"" + mediaId + "\"}]}";
        assertThat(put("/api/v1/admin/media/" + sequel + "/relations", reverse)).hasStatus(409);
        assertThat(put("/api/v1/admin/media/" + mediaId + "/relations", "{\"relations\": []}")).hasStatusOk()
                .bodyJson().extractingPath("$.relations").asArray().isEmpty();
    }

    @Test
    void coverCropBumpsTheVersion() {
        String uri = "/api/v1/admin/media/" + mediaId + "/cover-crop";
        assertThat(put(uri, "{\"crop\": {\"x\": 0.8, \"y\": 0, \"w\": 0.5, \"h\": 1}}")).hasStatus(400);
        assertThat(put(uri, "{\"crop\": {\"x\": 0.1, \"y\": 0.1, \"w\": 0.5, \"h\": 0.5}}")).hasStatusOk()
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.cover.version").isEqualTo(2);
                    assertThat(json).extractingPath("$.cover.crop.w").isEqualTo(0.5);
                    if (vipsAvailable()) {
                        assertThat(json).extractingPath("$.warning").isNull();
                        assertThat(json).extractingPath("$.cover.thumbnails").isEqualTo(true);
                    } else {
                        assertThat(json).extractingPath("$.warning").asString().contains("thumbnails");
                    }
                });
        assertThat(auditCount("MEDIA_COVER_CROPPED")).isEqualTo(1);
    }

    @Test
    void coverUploadReplacesTheOriginal() throws Exception {
        byte[] png = Files.readAllBytes(FIXTURES.resolve("series-messy/045/2.png"));
        assertThat(upload("/api/v1/admin/media/" + mediaId + "/cover", new MockMultipartFile("file", "new.png", "image/png", png)))
                .hasStatusOk().bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.cover.file").isEqualTo("cover.png");
                    assertThat(json).extractingPath("$.cover.crop").isNull();
                });
        Path dir = mediaDir(mediaId);
        assertThat(dir.resolve("cover.png")).hasBinaryContent(png);
        assertThat(dir.resolve("cover.jpg")).doesNotExist();
        assertThat(listNames(dir)).noneMatch(n -> n.startsWith(".upload-"));

        assertThat(upload("/api/v1/admin/media/" + mediaId + "/cover",
                new MockMultipartFile("file", "x.png", "image/png", "not an image".getBytes()))).hasStatus(400);
        assertThat(upload("/api/v1/admin/media/" + mediaId + "/cover",
                new MockMultipartFile("file", "x.exe", "application/octet-stream", png))).hasStatus(400);
        assertThat(dir.resolve("cover.png")).hasBinaryContent(png);

        assertThat(get("/api/v1/admin/media/{id}/cover/original", mediaId)).hasStatusOk()
                .hasContentType("image/png");
    }

    @Test
    void deleteChapterUpdatesCountersAndRemovesTheFolder() {
        UUID last = chapterRepository.findByMediaIdOrdered(mediaId).getLast().getId();
        assertThat(delete("/api/v1/admin/media/{id}/chapters/{c}", mediaId, last)).hasStatusOk()
                .bodyJson().extractingPath("$.chapters").asArray().hasSize(5);
        assertThat(mediaRepository.findById(mediaId).orElseThrow().getChapterCount()).isEqualTo(5);
        assertThat(listNames(mediaDir(mediaId))).doesNotContain("5");
        assertThat(delete("/api/v1/admin/media/{id}/chapters/{c}", mediaId, UUID.randomUUID())).hasStatus(404);
        assertThat(auditCount("CHAPTER_DELETED")).isEqualTo(1);
    }

    @Test
    void deleteMediaRemovesRowsFilesAndThumbnails() {
        assertThat(delete("/api/v1/admin/media/{id}", mediaId)).hasStatus(204);
        assertThat(mediaRepository.existsById(mediaId)).isFalse();
        assertThat(listNames(paths.libraryDir())).doesNotContain(mediaId.toString(), ".deleted-" + mediaId);
        assertThat(paths.libraryDir().resolveSibling("data").resolve("thumbs").resolve(mediaId.toString())).doesNotExist();
        assertThat(auditCount("MEDIA_DELETED")).isEqualTo(1);
        // The import job keeps its history without the media.
        assertThat(jobs.findAll()).singleElement().satisfies(j -> assertThat(j.getMediaId()).isNull());
        assertThat(delete("/api/v1/admin/media/{id}", mediaId)).hasStatus(404);
    }

    @Test
    void usersCannotEdit() {
        asUser();
        assertThat(get("/api/v1/admin/media/{id}", mediaId)).hasStatus(403);
        assertThat(patch("/api/v1/admin/media/" + mediaId, "{\"fields\": [\"title\"], \"values\": {\"title\": \"X\"}}"))
                .hasStatus(403);
        assertThat(delete("/api/v1/admin/media/{id}", mediaId)).hasStatus(403);
        List<String> unchanged = mediaRepository.findById(mediaId).orElseThrow().getLockedFields();
        assertThat(unchanged).containsExactly("title", "type", "authors");
    }
}
