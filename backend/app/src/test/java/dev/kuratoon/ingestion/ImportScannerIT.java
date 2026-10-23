package dev.kuratoon.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.support.ImportTestBase;
import dev.kuratoon.support.TestFolders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** docs/spec/02-ingestion.md "Folder detection". */
class ImportScannerIT extends ImportTestBase {

    @Test
    void folderIsQueuedOnlyAfterBeingStableForTheWindow() throws Exception {
        copyFixture("oneshot");
        assertThat(scanner.scanOnce()).isEmpty();
        clock.advance(Duration.ofSeconds(30));
        assertThat(scanner.scanOnce()).isEmpty();
        clock.advance(Duration.ofSeconds(30));
        assertThat(scanner.scanOnce()).containsExactly("oneshot");
        assertThat(jobs.findAll()).singleElement().satisfies(j -> {
            assertThat(j.getStatus()).isEqualTo(ImportStatus.QUEUED);
            assertThat(j.getSourceFolder()).isEqualTo("oneshot");
            assertThat(j.getAttempts()).isZero();
        });
    }

    @Test
    void aChangeRestartsTheWindow() throws Exception {
        Path folder = copyFixture("oneshot");
        scanner.scanOnce();
        clock.advance(Duration.ofSeconds(59));
        // Still being copied: a new file arrives.
        Files.copy(folder.resolve("001.jpg"), folder.resolve("006.jpg"));
        assertThat(scanner.scanOnce()).isEmpty();
        clock.advance(Duration.ofSeconds(59));
        assertThat(scanner.scanOnce()).isEmpty();
        clock.advance(Duration.ofSeconds(2));
        assertThat(scanner.scanOnce()).containsExactly("oneshot");
    }

    @Test
    void dotFoldersAndLooseFilesAreIgnored() throws Exception {
        copyFixture("oneshot", ".hidden");
        copyFixture("oneshot", ".dismissed-oneshot-0192");
        Files.writeString(TestFolders.IMPORT.resolve("loose.txt"), "x");
        scanner.scanOnce();
        clock.advance(Duration.ofMinutes(5));
        assertThat(scanner.scanOnce()).isEmpty();
        assertThat(jobs.count()).isZero();
    }

    @Test
    void folderOwnedByAnUnfinishedJobIsNotQueuedAgain() {
        copyFixture("series-no-number");
        ImportJob job = importFolder("series-no-number");
        assertThat(job.getStatus()).isEqualTo(ImportStatus.NEEDS_ATTENTION);
        scanner.scanOnce();
        clock.advance(Duration.ofMinutes(5));
        assertThat(scanner.scanOnce()).isEmpty();
        assertThat(jobs.count()).isEqualTo(1);
    }

    @Test
    void queuedJobsRunOldestFirst() {
        copyFixture("series-no-number", "b-second");
        copyFixture("series-no-number", "a-first");
        scanner.scanOnce();
        clock.advance(Duration.ofSeconds(61));
        scanner.scanOnce();
        assertThat(worker.processNext()).isPresent();
        assertThat(worker.processNext()).isPresent();
        assertThat(worker.processNext()).isEmpty();
        assertThat(jobs.findAll()).allMatch(j -> j.getStatus() == ImportStatus.NEEDS_ATTENTION);
    }
}
