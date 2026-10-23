package dev.kuratoon.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kuratoon.chapter.Chapter;
import dev.kuratoon.chapter.ChapterRepository;
import dev.kuratoon.chapter.PageEntry;
import dev.kuratoon.ingestion.ImportFaults.Step;
import dev.kuratoon.ingestion.JobDetail.Attention;
import dev.kuratoon.ingestion.JobDetail.ConflictChoice;
import dev.kuratoon.ingestion.KuratoonYaml.SnapshotDocument;
import dev.kuratoon.media.MediaRepository;
import dev.kuratoon.support.ImportTestBase;
import dev.kuratoon.support.TestFaults.SimulatedCrash;
import dev.kuratoon.support.TestFolders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.FileSystemUtils;

/**
 * Crash safety (docs/spec/02-ingestion.md "Commit order"): a crash at each step, then startup
 * recovery. Before the commit nothing is left behind; after it, the job is rolled forward.
 */
class CrashRecoveryIT extends ImportTestBase {

    @Autowired
    MediaRepository mediaRepository;

    @Autowired
    ChapterRepository chapterRepository;

    @ParameterizedTest
    @EnumSource(value = Step.class, names = {"AFTER_COPY", "AFTER_VALIDATE", "IN_TRANSACTION"})
    void crashBeforeCommitLeavesNoPartialLibraryFolder(Step step) {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        ImportJob job = crashDuringImport("series-basic", step);
        assertThat(job.getStatus()).isEqualTo(ImportStatus.PROCESSING);

        recovery.recoverOnStartup();

        ImportJob after = reload(job.getId());
        assertThat(after.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(after.getMessage()).contains("Interrupted by a restart");
        assertThat(after.getCommittedAt()).isNull();
        assertThat(mediaRepository.count()).isZero();
        assertThat(listNames(paths.libraryDir())).as("no media folder, no staging").containsExactly(".staging");
        assertThat(listNames(paths.stagingRoot())).isEmpty();
        assertThat(listNames(TestFolders.IMPORT)).as("source intact").containsExactly("series-basic");

        // Retry imports it normally.
        ImportJob retried = decideAndRetry(job.getId(), d -> d);
        assertThat(retried.getStatus()).as(retried.getMessage()).isEqualTo(ImportStatus.DONE);
        assertLibraryConsistent(retried);
    }

    @ParameterizedTest
    @EnumSource(value = Step.class, names = {"AFTER_COMMIT", "AFTER_MOVE", "AFTER_THUMBNAILS", "AFTER_SIDECAR"})
    void crashAfterCommitIsRolledForward(Step step) {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        ImportJob job = crashDuringImport("series-basic", step);
        assertThat(job.getStatus()).isEqualTo(ImportStatus.PROCESSING);
        assertThat(job.getCommittedAt()).isNotNull();

        recovery.recoverOnStartup();

        ImportJob after = reload(job.getId());
        assertThat(after.getStatus()).as(after.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(mediaRepository.count()).isEqualTo(1);
        assertLibraryConsistent(after);
    }

    @Test
    void crashMidMoveOfAnUpdateIsRolledForward() {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        UUID mediaId = importFolder("series-basic").getMediaId();
        copyFixture("series-update");
        ImportJob job = crashDuringImport("series-update", Step.MID_MOVE);
        // One of the two chapter folders is in place, the other still staged.
        assertThat(listNames(mediaDir(mediaId))).contains("6").doesNotContain("7");

        recovery.recoverOnStartup();

        ImportJob after = reload(job.getId());
        assertThat(after.getStatus()).as(after.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(listNames(mediaDir(mediaId))).contains("6", "7");
        assertLibraryConsistent(after);
    }

    @Test
    void crashMidReplaceSwapIsCompleted() {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        UUID mediaId = importFolder("series-basic").getMediaId();
        copyFixture("series-conflict");
        ImportJob job = importFolder("series-conflict");
        assertThat(job.detail().attention()).isEqualTo(Attention.CONFLICT);

        faults.crashAt(Step.MID_REPLACE_SWAP);
        assertThatThrownBy(() -> decideAndRetry(job.getId(), d -> d.withConflicts(Map.of("3", ConflictChoice.REPLACE))))
                .isInstanceOf(SimulatedCrash.class);
        // The old chapter 3 is in the backup, the new one still staged: the library lacks it.
        assertThat(listNames(mediaDir(mediaId))).doesNotContain("3");
        assertThat(paths.backup(job.getId()).resolve("3")).isDirectory();

        recovery.recoverOnStartup();

        ImportJob after = reload(job.getId());
        assertThat(after.getStatus()).as(after.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(listNames(mediaDir(mediaId).resolve("3"))).containsExactly("0001.jpg", "0002.jpg");
        assertThat(paths.staging(job.getId())).doesNotExist();
        assertLibraryConsistent(after);
    }

    @Test
    void committedJobWithoutStagingAndIncompleteLibraryNeedsAttention() throws Exception {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        ImportJob job = crashDuringImport("series-basic", Step.AFTER_COMMIT);
        FileSystemUtils.deleteRecursively(paths.staging(job.getId()));

        recovery.recoverOnStartup();

        ImportJob after = reload(job.getId());
        assertThat(after.getStatus()).isEqualTo(ImportStatus.NEEDS_ATTENTION);
        assertThat(after.detail().attention()).isEqualTo(Attention.RECOVERY);
        assertThat(after.getMessage()).contains("library folder").contains("missing");
    }

    @Test
    void ordinaryFailureBeforeCommitCleansUpImmediately() {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        faults.failAt(Step.AFTER_VALIDATE);
        ImportJob job = importFolder("series-basic");
        assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(job.getMessage()).contains("simulated failure");
        assertThat(listNames(paths.stagingRoot())).isEmpty();
        assertThat(mediaRepository.count()).isZero();
    }

    @Test
    void interruptedResolutionIsQueuedAgain() {
        copyFixture("series-no-number");
        ImportJob job = queue("series-no-number");
        tx.executeWithoutResult(s -> jobs.findById(job.getId()).orElseThrow()
                .moveTo(ImportStatus.RESOLVING, null, clock.instant()));
        recovery.recoverOnStartup();
        assertThat(reload(job.getId()).getStatus()).isEqualTo(ImportStatus.QUEUED);
    }

    @Test
    void leftoversAreRemovedOnStartup() throws Exception {
        Files.createDirectories(paths.staging(UUID.randomUUID()).resolve("media/1"));
        Files.createDirectories(paths.libraryDir().resolve(".deleted-" + UUID.randomUUID()));
        Files.createDirectories(TestFolders.IMPORT.resolve(".imported-old-" + UUID.randomUUID()));
        Files.createDirectories(TestFolders.IMPORT.resolve(".dismissed-keep-" + UUID.randomUUID()));
        recovery.recoverOnStartup();
        assertThat(listNames(paths.stagingRoot())).isEmpty();
        assertThat(listNames(paths.libraryDir())).containsExactly(".staging");
        assertThat(listNames(TestFolders.IMPORT)).singleElement().asString().startsWith(".dismissed-keep-");
    }

    @Test
    void failedSourceDeleteNeverCausesAReimport() throws Exception {
        Path source = copyFixture("series-basic");
        writeManifest(source, BASIC_COMPLETE);
        Path locked = source.resolve("Chapter 0 - Prologue");
        lockAgainstDeletion(locked);
        ImportJob job;
        try {
            job = importFolder("series-basic");
        } finally {
            TestFolders.makeWritable(TestFolders.IMPORT);
            unlock(locked);
        }
        assertThat(job.getStatus()).as(job.getMessage()).isEqualTo(ImportStatus.DONE);
        assertThat(job.detail().warnings()).anyMatch(w -> w.contains("could not be deleted"));
        List<String> left = listNames(TestFolders.IMPORT);
        assertThat(left).singleElement().asString().startsWith(".imported-series-basic-");

        scanner.scanOnce();
        clock.advance(Duration.ofMinutes(5));
        assertThat(scanner.scanOnce()).isEmpty();

        recovery.recoverOnStartup();
        assertThat(listNames(TestFolders.IMPORT)).isEmpty();
    }

    @Test
    void staleSidecarIsRewrittenOnStartup() throws Exception {
        writeManifest(copyFixture("series-basic"), BASIC_COMPLETE);
        ImportJob job = importFolder("series-basic");
        UUID mediaId = job.getMediaId();
        // A metadata change whose debounced sidecar write was lost (e.g. the app stopped).
        jdbc.update("update media set title = 'Renamed', updated_at = now() + interval '1 hour' where id = ?", mediaId);

        recovery.recoverOnStartup();

        SnapshotDocument sidecar = (SnapshotDocument) KuratoonYaml.parse(Files.readString(sidecars.sidecar(mediaId)));
        assertThat(sidecar.fields().title()).isEqualTo("Renamed");
    }

    // --- helpers --------------------------------------------------------------------------------

    private ImportJob crashDuringImport(String folder, Step step) {
        ImportJob queued = queue(folder);
        faults.crashAt(step);
        assertThatThrownBy(() -> worker.processAll()).isInstanceOf(SimulatedCrash.class);
        return reload(queued.getId());
    }

    /** The job's media matches the library folder exactly; nothing is left in staging or /import. */
    private void assertLibraryConsistent(ImportJob job) {
        UUID mediaId = job.getMediaId();
        Path dir = mediaDir(mediaId);
        List<Chapter> chapters = chapterRepository.findByMediaIdOrdered(mediaId);
        assertThat(chapters).isNotEmpty();
        for (Chapter c : chapters) {
            assertThat(listNames(dir.resolve(c.getFolder())))
                    .as("chapter %s files", c.getFolder())
                    .containsExactlyElementsOf(c.getPages().stream().map(PageEntry::f).sorted().toList());
        }
        try {
            SnapshotDocument sidecar = (SnapshotDocument) KuratoonYaml.parse(Files.readString(sidecars.sidecar(mediaId)));
            assertThat(sidecar.chapters()).extracting(KuratoonYaml.SnapshotChapter::folder)
                    .containsExactlyElementsOf(chapters.stream().map(Chapter::getFolder).toList());
        } catch (Exception e) {
            throw new AssertionError("sidecar unreadable", e);
        }
        String cover = mediaRepository.findById(mediaId).orElseThrow().getCoverFile();
        assertThat(dir.resolve(cover)).isRegularFile();
        assertThat(listNames(paths.stagingRoot())).isEmpty();
        assertThat(listNames(TestFolders.IMPORT)).isEmpty();
    }

    /** Makes the folder's contents undeletable (POSIX: no write on the dir; Windows: read-only files). */
    private static void lockAgainstDeletion(Path dir) throws Exception {
        if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
        } else {
            try (var files = Files.list(dir)) {
                for (Path f : files.toList()) {
                    Files.setAttribute(f, "dos:readonly", true);
                }
            }
        }
    }

    private static void unlock(Path dir) throws Exception {
        Path current = Files.exists(dir) ? dir : null;
        if (current == null) {
            // Renamed with its parent to .imported-*; makeWritable on /import handled POSIX.
            try (var all = Files.walk(TestFolders.IMPORT)) {
                for (Path p : all.toList()) {
                    if (Files.isRegularFile(p) && !Files.getFileStore(p).supportsFileAttributeView("posix")) {
                        Files.setAttribute(p, "dos:readonly", false);
                    }
                }
            }
            return;
        }
        if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        } else {
            try (var files = Files.list(dir)) {
                for (Path f : files.toList()) {
                    Files.setAttribute(f, "dos:readonly", false);
                }
            }
        }
    }
}
