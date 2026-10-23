package dev.kuratoon.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.chapter.Chapter;
import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.chapter.PageEntry;
import dev.kuratoon.ingestion.JobDetail.Attention;
import dev.kuratoon.ingestion.JobDetail.ConflictChoice;
import dev.kuratoon.ingestion.JobDetail.DuplicateChoice;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotDocument;
import dev.kuratoon.media.Media;
import dev.kuratoon.media.MediaFields;
import dev.kuratoon.media.MediaRepository;
import dev.kuratoon.support.ImportTestBase;
import dev.kuratoon.support.TestFolders;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Roadmap phase 3 integration scenarios using {@code fixtures/import}. */
class ImportPipelineIT extends ImportTestBase {

    @Autowired
    MediaRepository mediaRepository;

    @Autowired
    ChapterRepository chapterRepository;

    // --- new series -----------------------------------------------------------------------------

    @Test
    void newSeriesWithCompleteManifest() throws Exception {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        ImportJob job = importFolder("series-basic");

        assertThat(job.getStatus()).as(job.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(job.getKind()).isEqualTo(ImportKind.NEW);
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getMessage()).isEqualTo("Imported 6 chapters.");
        UUID mediaId = job.getMediaId();

        Media m = tx.execute(s -> {
            Media x = mediaRepository.findById(mediaId).orElseThrow();
            x.getCreators().size();
            x.getMediaType().getSlug();
            return x;
        });
        assertThat(m.getTitle()).isEqualTo("Basic Series");
        assertThat(m.getMediaType().getSlug()).isEqualTo("manga");
        assertThat(m.getLanguage()).isEqualTo("en");
        assertThat(m.getSourceUrl()).isEqualTo("http://fake-manga.test/series/basic");
        assertThat(m.getLockedFields()).containsExactly("title", "type", "authors");
        assertThat(m.getChapterCount()).isEqualTo(6);
        assertThat(m.getLatestChapterAddedAt()).isNotNull();
        assertThat(m.getCoverFile()).isEqualTo("cover.jpg");

        List<Chapter> chapters = chapterRepository.findByMediaIdOrdered(mediaId);
        assertThat(chapters).extracting(Chapter::getFolder).containsExactly("0", "1", "2", "3", "4", "5");
        assertThat(chapters).extracting(Chapter::getTitle)
                .containsExactly("Prologue", "The Beginning", "Rising", "The Middle", "Falling", "The End");
        assertThat(chapters.getFirst().getPages()).containsExactly(
                new PageEntry("0001.jpg", 800, 1200), new PageEntry("0002.jpg", 800, 1200),
                new PageEntry("0003.jpg", 800, 1200), new PageEntry("0004.jpg", 800, 1200));

        // Library layout: pages copied byte-for-byte, original cover, sidecar.
        Path dir = mediaDir(mediaId);
        assertThat(listNames(dir)).containsExactly("0", "1", "2", "3", "4", "5", "cover.jpg", "kuratoon.yml");
        assertThat(dir.resolve("3/0002.jpg")).hasSameBinaryContentAs(
                FIXTURES.resolve("series-basic/Chapter 3 - The Middle/002.jpg"));
        assertThat(dir.resolve("cover.jpg")).hasSameBinaryContentAs(
                FIXTURES.resolve("series-basic/Chapter 0 - Prologue/001.jpg"));
        SnapshotDocument sidecar = (SnapshotDocument) KuratoonYaml.parse(Files.readString(dir.resolve("kuratoon.yml")));
        assertThat(sidecar.id()).isEqualTo(mediaId);
        assertThat(sidecar.fields().authors()).containsExactly("Basic Author");
        assertThat(sidecar.chapters()).hasSize(6);
        assertThat(sidecar.chapters().getFirst().createdAt()).isEqualTo(chapters.getFirst().getCreatedAt());
        assertThat(sidecar.locked()).containsExactly("title", "type", "authors");

        if (vipsAvailable()) {
            assertThat(listNames(paths.libraryDir().resolve("../data/thumbs").normalize().resolve(mediaId.toString())))
                    .containsExactly("cover-240.webp", "cover-480.webp", "cover-720.webp");
        } else {
            assertThat(job.detail().warnings()).anyMatch(w -> w.contains("thumbnails could not be generated"));
        }

        // Source gone, nothing left in staging, and the audit records the commit.
        assertThat(listNames(TestFolders.IMPORT)).isEmpty();
        assertThat(listNames(paths.stagingRoot())).isEmpty();
        assertThat(auditCount("IMPORT_COMMITTED")).isEqualTo(1);
    }

    @Test
    void urlOnlyManifestNeedsAPluginThenManualMetadataImportsIt() {
        copyFixture("series-basic");
        ImportJob job = importFolder("series-basic");
        assertThat(job.getStatus()).isEqualTo(ImportStatus.NEEDS_ATTENTION);
        assertThat(job.detail().attention()).isEqualTo(Attention.NO_PLUGIN);
        assertThat(job.getMessage()).contains("No metadata plugin supports http://fake-manga.test/series/basic");
        assertThat(job.detail().missing()).containsExactly("type", "authors or artists");
        // The chapter table is shown while waiting.
        assertThat(job.detail().chapters()).hasSize(6);
        assertThat(job.getKind()).isEqualTo(ImportKind.NEW);
        assertThat(mediaRepository.count()).isZero();

        ImportJob done = decideAndRetry(job.getId(), d -> d.withManualMetadata(manual("Basic Series", "manga")));
        assertThat(done.getStatus()).as(done.getMessage()).isEqualTo(ImportStatus.DONE);
        Media m = mediaRepository.findById(done.getMediaId()).orElseThrow();
        assertThat(m.getSourceUrl()).isEqualTo("http://fake-manga.test/series/basic");
        // Manual entries lock their fields.
        assertThat(m.getLockedFields()).containsExactly("title", "type", "authors");
        assertThat(done.getAttempts()).isEqualTo(2);
    }

    @Test
    void oneShot() {
        copyFixture("oneshot");
        ImportJob job = importFolder("oneshot");
        assertThat(job.getStatus()).isEqualTo(ImportStatus.NEEDS_ATTENTION);
        assertThat(job.detail().attention()).isEqualTo(Attention.NO_PLUGIN);
        assertThat(job.detail().url()).isEqualTo("http://fake-manga.test/oneshot/one");

        ImportJob done = decideAndRetry(job.getId(), d -> d.withManualMetadata(manual("One Shot", "one-shot")));
        assertThat(done.getStatus()).as(done.getMessage()).isEqualTo(ImportStatus.DONE);
        List<Chapter> chapters = chapterRepository.findByMediaIdOrdered(done.getMediaId());
        assertThat(chapters).singleElement().satisfies(c -> {
            assertThat(c.getNumber()).isEqualByComparingTo("1");
            assertThat(c.getTitle()).isNull();
            assertThat(c.getFolder()).isEqualTo("1");
            assertThat(c.getPageCount()).isEqualTo(5);
        });
        assertThat(listNames(mediaDir(done.getMediaId()).resolve("1")))
                .containsExactly("0001.jpg", "0002.jpg", "0003.jpg", "0004.jpg", "0005.jpg");
    }

    @Test
    void messyNamesAreParsedAndPagesOrdered() throws Exception {
        copyFixture("series-messy");
        ImportJob job = importFolder("series-messy");
        assertThat(job.getStatus()).as(job.getMessage()).isEqualTo(ImportStatus.DONE);
        List<Chapter> chapters = chapterRepository.findByMediaIdOrdered(job.getMediaId());
        assertThat(chapters).extracting(Chapter::getFolder).containsExactly("10", "12", "12.5", "45");
        Chapter twelve = chapters.get(1);
        assertThat(twelve.getPages()).extracting(PageEntry::f)
                .containsExactly("0001.jpg", "0002.webp", "0003.png", "0004.jpg", "0005.png", "0006.jpg");
        Path src = FIXTURES.resolve("series-messy/chapter 012");
        Path dst = mediaDir(job.getMediaId()).resolve("12");
        assertThat(dst.resolve("0002.webp")).hasSameBinaryContentAs(src.resolve("01.webp"));
        assertThat(dst.resolve("0005.png")).hasSameBinaryContentAs(src.resolve("page 10.5.png"));
        assertThat(dst.resolve("0006.jpg")).hasSameBinaryContentAs(src.resolve("extra.jpg"));
        // Junk files are not copied.
        assertThat(listNames(dst)).hasSize(6);
    }

    // --- update ---------------------------------------------------------------------------------

    @Test
    void updateAppendsChapters() {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        ImportJob first = importFolder("series-basic");
        UUID mediaId = first.getMediaId();
        List<Chapter> before = chapterRepository.findByMediaIdOrdered(mediaId);
        Instant latestBefore = mediaRepository.findById(mediaId).orElseThrow().getLatestChapterAddedAt();

        clock.advance(Duration.ofHours(2));
        copyFixture("series-update");
        ImportJob update = importFolder("series-update");
        assertThat(update.getStatus()).as(update.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(update.getKind()).isEqualTo(ImportKind.UPDATE);
        assertThat(update.getMediaId()).isEqualTo(mediaId);
        assertThat(update.getMessage()).isEqualTo("Added 2, replaced 0 chapter(s).");

        List<Chapter> after = chapterRepository.findByMediaIdOrdered(mediaId);
        assertThat(after).extracting(Chapter::getFolder).containsExactly("0", "1", "2", "3", "4", "5", "6", "7");
        // Existing chapters untouched.
        assertThat(after.subList(0, 6)).usingRecursiveFieldByFieldElementComparatorOnFields("id", "pages", "createdAt")
                .isEqualTo(before);
        Media m = mediaRepository.findById(mediaId).orElseThrow();
        assertThat(m.getChapterCount()).isEqualTo(8);
        assertThat(m.getLatestChapterAddedAt()).isAfter(latestBefore);
        assertThat(listNames(mediaDir(mediaId))).contains("6", "7");
    }

    @Test
    void conflictingChapterNeedsAttentionThenSkip() {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        UUID mediaId = importFolder("series-basic").getMediaId();

        copyFixture("series-conflict");
        ImportJob job = importFolder("series-conflict");
        assertThat(job.getStatus()).isEqualTo(ImportStatus.NEEDS_ATTENTION);
        assertThat(job.detail().attention()).isEqualTo(Attention.CONFLICT);
        assertThat(job.getKind()).isEqualTo(ImportKind.UPDATE);
        assertThat(job.getMediaId()).isEqualTo(mediaId);
        assertThat(job.detail().conflicts()).singleElement().satisfies(c -> {
            assertThat(c.folder()).isEqualTo("3");
            assertThat(c.existingPageCount()).isEqualTo(4);
            assertThat(c.newPageCount()).isEqualTo(2);
        });
        assertThat(chapterRepository.findByMediaIdOrdered(mediaId)).hasSize(6);

        ImportJob done = decideAndRetry(job.getId(), d -> d.withConflicts(Map.of("3", ConflictChoice.SKIP)));
        assertThat(done.getStatus()).as(done.getMessage()).isEqualTo(ImportStatus.DONE);
        List<Chapter> chapters = chapterRepository.findByMediaIdOrdered(mediaId);
        assertThat(chapters).extracting(Chapter::getFolder).containsExactly("0", "1", "2", "3", "4", "5", "8");
        assertThat(chapters.get(3).getPageCount()).isEqualTo(4);
    }

    @Test
    void replaceSwapsPagesKeepsProgressAndClampsIt() {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        UUID mediaId = importFolder("series-basic").getMediaId();
        Chapter three = chapterRepository.findByMediaIdOrdered(mediaId).get(3);
        UUID reader = data.aUser("reader", dev.kuratoon.user.Role.USER).getId();
        jdbc.update("""
                insert into chapter_progress (user_id, chapter_id, media_id, page_index, page_offset, completed_at, updated_at)
                values (?, ?, ?, 3, 0.5, now(), now())
                """, reader, three.getId(), mediaId);

        copyFixture("series-conflict");
        ImportJob job = importFolder("series-conflict");
        ImportJob done = decideAndRetry(job.getId(), d -> d.withConflicts(Map.of("3", ConflictChoice.REPLACE)));
        assertThat(done.getStatus()).as(done.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(done.getMessage()).isEqualTo("Added 1, replaced 1 chapter(s).");

        Chapter replaced = chapterRepository.findById(three.getId()).orElseThrow();
        assertThat(replaced.getPageCount()).isEqualTo(2);
        assertThat(replaced.getCreatedAt()).isEqualTo(three.getCreatedAt());
        assertThat(listNames(mediaDir(mediaId).resolve("3"))).containsExactly("0001.jpg", "0002.jpg");
        assertThat(mediaDir(mediaId).resolve("3/0002.jpg")).hasSameBinaryContentAs(
                FIXTURES.resolve("series-conflict/Chapter 3 - The Middle (HQ)/002.jpg"));
        Map<String, Object> progress = jdbc.queryForMap(
                "select page_index, page_offset, completed_at from chapter_progress where chapter_id = ?", three.getId());
        assertThat(progress.get("page_index")).isEqualTo(1);
        assertThat(((Number) progress.get("page_offset")).doubleValue()).isZero();
        assertThat(progress.get("completed_at")).as("sticky read status").isNotNull();
        assertThat(mediaRepository.findById(mediaId).orElseThrow().getChapterCount()).isEqualTo(7);
        assertThat(listNames(paths.stagingRoot())).isEmpty();
    }

    // --- needs attention ------------------------------------------------------------------------

    @Test
    void corruptImageNeedsAttentionAndWritesNothing() {
        copyFixture("series-corrupt");
        ImportJob job = importFolder("series-corrupt");
        assertThat(job.getStatus()).isEqualTo(ImportStatus.NEEDS_ATTENTION);
        assertThat(job.detail().attention()).isEqualTo(Attention.UNREADABLE_IMAGE);
        assertThat(job.getMessage()).contains("Chapter 2/002.jpg").contains("truncated");
        assertThat(job.detail().chapters().get(1).warnings()).anyMatch(w -> w.contains("002.jpg"));
        assertThat(mediaRepository.count()).isZero();
        assertThat(listNames(paths.libraryDir())).isEmpty();
        assertThat(listNames(TestFolders.IMPORT)).containsExactly("series-corrupt");
    }

    @Test
    void duplicateChapterNumbersNeedAttention() {
        copyFixture("series-duplicate-number");
        ImportJob job = importFolder("series-duplicate-number");
        assertThat(job.getStatus()).isEqualTo(ImportStatus.NEEDS_ATTENTION);
        assertThat(job.detail().attention()).isEqualTo(Attention.CHAPTERS);
        assertThat(job.getMessage()).contains("Chapter number 2 appears in more than one folder");
    }

    @Test
    void folderWithoutNumberNeedsAttention() {
        copyFixture("series-no-number");
        ImportJob job = importFolder("series-no-number");
        assertThat(job.detail().attention()).isEqualTo(Attention.CHAPTERS);
        assertThat(job.getMessage()).contains("Extra: No chapter number");
        assertThat(job.detail().chapters()).extracting(JobDetail.ChapterRow::sourceFolder)
                .containsExactly("Chapter 1", "Extra");
    }

    @Test
    void htmlOnlyUsesTheCanonicalUrl() {
        copyFixture("series-html-only");
        ImportJob job = importFolder("series-html-only");
        assertThat(job.detail().attention()).isEqualTo(Attention.NO_PLUGIN);
        assertThat(job.detail().url()).isEqualTo("http://fake-manga.test/series/html-only");
    }

    @Test
    void invalidManifestNeedsAttention() {
        writeManifest(copyFixture("series-basic"), "kuratoon: 7\n");
        ImportJob job = importFolder("series-basic");
        assertThat(job.detail().attention()).isEqualTo(Attention.INVALID_MANIFEST);
        assertThat(job.getMessage()).contains("schema version 7");
    }

    @Test
    void unknownTypeNeedsAttention() {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE.replace("type: manga", "type: scroll"));
        ImportJob job = importFolder("series-basic");
        assertThat(job.detail().attention()).isEqualTo(Attention.INCOMPLETE_METADATA);
        assertThat(job.getMessage()).isEqualTo("Unknown media type 'scroll'.");
    }

    // --- manifest-only NEW and duplicates -------------------------------------------------------

    @Test
    void completeManifestWithoutUrlImportsWithNullSourceUrl() {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE.replace("url: http://fake-manga.test/series/basic\n", ""));
        ImportJob job = importFolder("series-basic");
        assertThat(job.getStatus()).as(job.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(mediaRepository.findById(job.getMediaId()).orElseThrow().getSourceUrl()).isNull();
    }

    @Test
    void incompleteManifestWithoutUrlHasNoMetadataSource() {
        writeManifest(copyFixture("series-basic"), "kuratoon: 1\ntitle: Only A Title\n");
        ImportJob job = importFolder("series-basic");
        assertThat(job.detail().attention()).isEqualTo(Attention.NO_METADATA_SOURCE);
        assertThat(job.detail().prefill().title()).isEqualTo("Only A Title");
    }

    @Test
    void sameTitleAndTypeIsADuplicate() {
        String noUrl = BASIC_COMPLETE.replace("url: http://fake-manga.test/series/basic\n", "");
        writeManifest(copyFixture("series-basic", "first"), noUrl);
        UUID existing = importFolder("first").getMediaId();

        writeManifest(copyFixture("series-update", "second"), noUrl.replace("Basic Series", "basic series"));
        ImportJob job = importFolder("second");
        assertThat(job.detail().attention()).isEqualTo(Attention.DUPLICATE);
        assertThat(job.detail().duplicate().mediaId()).isEqualTo(existing);

        // "Update existing" appends the chapters to the candidate.
        ImportJob done = decideAndRetry(job.getId(), d -> d.withDuplicate(DuplicateChoice.UPDATE_EXISTING, existing));
        assertThat(done.getStatus()).as(done.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(done.getKind()).isEqualTo(ImportKind.UPDATE);
        assertThat(chapterRepository.findByMediaIdOrdered(existing)).hasSize(8);
    }

    @Test
    void duplicateCanBeCreatedAsNew() {
        String noUrl = BASIC_COMPLETE.replace("url: http://fake-manga.test/series/basic\n", "");
        writeManifest(copyFixture("series-basic", "first"), noUrl);
        importFolder("first");
        writeManifest(copyFixture("series-update", "second"), noUrl);
        ImportJob job = importFolder("second");
        ImportJob done = decideAndRetry(job.getId(), d -> d.withDuplicate(DuplicateChoice.CREATE_NEW, null));
        assertThat(done.getStatus()).as(done.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(done.getKind()).isEqualTo(ImportKind.NEW);
        assertThat(mediaRepository.count()).isEqualTo(2);
    }

    // --- restore --------------------------------------------------------------------------------

    @Test
    void restoreFromSnapshotFixture() {
        copyFixture("restore-snapshot");
        ImportJob job = importFolder("restore-snapshot");
        assertThat(job.getStatus()).as(job.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(job.getKind()).isEqualTo(ImportKind.RESTORE);
        UUID id = UUID.fromString("01a20000-0000-7000-8000-000000000001");
        assertThat(job.getMediaId()).isEqualTo(id);
        Media m = mediaRepository.findById(id).orElseThrow();
        assertThat(m.getCreatedAt()).isEqualTo(Instant.parse("2026-01-05T09:00:00Z"));
        assertThat(m.getLatestChapterAddedAt()).isEqualTo(Instant.parse("2026-02-11T10:00:00Z"));
        assertThat(m.getChapterCount()).isEqualTo(3);
        assertThat(m.getLockedFields()).containsExactly("title");
        assertThat(m.getCoverCrop()).isNotNull();
        assertThat(chapterRepository.findByMediaIdOrdered(id)).extracting(Chapter::getCreatedAt).containsExactly(
                Instant.parse("2026-01-05T10:00:00Z"), Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-11T10:00:00Z"));
        assertThat(listNames(mediaDir(id))).containsExactly("1", "2", "2.5", "cover.jpg", "kuratoon.yml");
    }

    @Test
    void restoreFromSidecarAfterDatabaseWipe() throws Exception {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        ImportJob original = importFolder("series-basic");
        UUID mediaId = original.getMediaId();
        List<Chapter> before = chapterRepository.findByMediaIdOrdered(mediaId);
        Instant createdBefore = mediaRepository.findById(mediaId).orElseThrow().getCreatedAt();

        wipeDatabase();
        clock.advance(Duration.ofDays(3));
        // The library folder (with its sidecar) is dropped into /import again.
        Files.move(mediaDir(mediaId), TestFolders.IMPORT.resolve("from-backup"));

        ImportJob job = importFolder("from-backup");
        assertThat(job.getStatus()).as(job.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(job.getKind()).isEqualTo(ImportKind.RESTORE);
        assertThat(job.getMediaId()).isEqualTo(mediaId);
        Media m = mediaRepository.findById(mediaId).orElseThrow();
        assertThat(m.getCreatedAt()).isEqualTo(createdBefore);
        assertThat(m.getSourceUrl()).isEqualTo("http://fake-manga.test/series/basic");
        assertThat(m.getLockedFields()).containsExactly("title", "type", "authors");
        List<Chapter> after = chapterRepository.findByMediaIdOrdered(mediaId);
        assertThat(after).extracting(Chapter::getCreatedAt).isEqualTo(before.stream().map(Chapter::getCreatedAt).toList());
        assertThat(after).extracting(Chapter::getPages).isEqualTo(before.stream().map(Chapter::getPages).toList());
        assertThat(m.getLatestChapterAddedAt()).isEqualTo(before.getLast().getCreatedAt());
    }

    @Test
    void restoreOfAnExistingMediaNeedsAttention() throws Exception {
        copyFixture("restore-snapshot", "a");
        importFolder("a");
        copyFixture("restore-snapshot", "b");
        ImportJob job = importFolder("b");
        assertThat(job.detail().attention()).isEqualTo(Attention.ALREADY_IN_LIBRARY);
        assertThat(job.getMediaId()).isEqualTo(UUID.fromString("01a20000-0000-7000-8000-000000000001"));
    }

    @Test
    void restoreWhoseUrlBelongsToAnotherMediaNeedsAttention() throws Exception {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        UUID owner = importFolder("series-basic").getMediaId();
        Path restore = copyFixture("restore-snapshot");
        Path yml = restore.resolve("kuratoon.yml");
        Files.writeString(yml, Files.readString(yml).replace(
                "url: http://fake-manga.test/series/restored", "url: http://FAKE-MANGA.test/series/basic/"));
        ImportJob job = importFolder("restore-snapshot");
        assertThat(job.getStatus()).isEqualTo(ImportStatus.NEEDS_ATTENTION);
        assertThat(job.detail().attention()).isEqualTo(Attention.URL_IN_USE);
        assertThat(job.getMessage()).contains(owner.toString());
        assertThat(job.getMediaId()).isNull();
    }

    // --- helpers --------------------------------------------------------------------------------

    private static MediaFields manual(String title, String type) {
        return new MediaFields(title, null, null, type, null, null, null, null, List.of("Manual Author"), null, null,
                null, null, null);
    }

    /** Library rows gone, files kept (the import jobs go too). */
    private void wipeDatabase() {
        jdbc.execute("truncate table media, creator, tag, parody, character, import_job, audit_log cascade");
    }

    @SuppressWarnings("unused")
    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }
}
