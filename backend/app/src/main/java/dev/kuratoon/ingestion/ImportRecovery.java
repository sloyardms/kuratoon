package dev.kuratoon.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;

/**
 * Startup recovery, before polling starts (docs/spec/02-ingestion.md "Commit order"): committed
 * PROCESSING jobs are rolled forward, uncommitted ones fail, and leftovers of interrupted work
 * are removed.
 */
@Service
public class ImportRecovery {

    private static final Logger log = LoggerFactory.getLogger(ImportRecovery.class);

    private final ImportJobRepository jobs;
    private final ImportWorker worker;
    private final LibraryPaths paths;
    private final SidecarService sidecars;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ImportRecovery(
            ImportJobRepository jobs,
            ImportWorker worker,
            LibraryPaths paths,
            SidecarService sidecars,
            TransactionTemplate tx,
            Clock clock) {
        this.jobs = jobs;
        this.worker = worker;
        this.paths = paths;
        this.sidecars = sidecars;
        this.tx = tx;
        this.clock = clock;
    }

    public void recoverOnStartup() {
        List<ImportJob> interrupted = tx.execute(s -> jobs.findByStatusIn(List.of(
                ImportStatus.PROCESSING, ImportStatus.RESOLVING, ImportStatus.FETCHING_METADATA)));
        for (ImportJob job : interrupted) {
            UUID id = job.getId();
            if (job.getStatus() != ImportStatus.PROCESSING) {
                // Resolution has no side effects: run it again.
                update(id, j -> j.moveTo(ImportStatus.QUEUED, null, clock.instant()));
                log.info("Import job {} was interrupted while resolving; queued again", id);
            } else if (job.getCommittedAt() != null) {
                log.info("Import job {} was interrupted after commit; finishing it", id);
                worker.finishCommitted(id);
            } else {
                delete(paths.staging(id));
                update(id, j -> j.moveTo(ImportStatus.FAILED,
                        "Interrupted by a restart during processing. Nothing was saved; press Retry.", clock.instant()));
                log.info("Import job {} was interrupted before commit; marked FAILED", id);
            }
        }
        removeLeftovers();
        sidecars.rewriteStale();
    }

    /**
     * Staging dirs not owned by an unfinished committed job (those still need their staged files
     * for Retry), {@code /library/.deleted-*} and {@code /import/.imported-*}.
     */
    void removeLeftovers() {
        Set<String> keep = new HashSet<>();
        tx.executeWithoutResult(s -> jobs.findByStatusIn(List.of(ImportStatus.NEEDS_ATTENTION, ImportStatus.PROCESSING))
                .stream()
                .filter(j -> j.getCommittedAt() != null)
                .forEach(j -> keep.add(j.getId().toString())));
        for (Path dir : list(paths.stagingRoot())) {
            if (!keep.contains(dir.getFileName().toString())) {
                delete(dir);
            }
        }
        for (Path dir : list(paths.libraryDir())) {
            if (dir.getFileName().toString().startsWith(LibraryPaths.DELETED_PREFIX)) {
                delete(dir);
            }
        }
        for (Path dir : list(paths.importDir())) {
            if (dir.getFileName().toString().startsWith(LibraryPaths.IMPORTED_PREFIX)) {
                delete(dir);
            }
        }
    }

    private static List<Path> list(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.toList();
        } catch (IOException e) {
            log.warn("Cannot list {}: {}", dir, e.toString());
            return List.of();
        }
    }

    private static void delete(Path path) {
        try {
            if (FileSystemUtils.deleteRecursively(path)) {
                log.info("Removed leftover {}", path);
            }
        } catch (IOException e) {
            log.warn("Could not remove {}: {}", path, e.toString());
        }
    }

    private void update(UUID id, Consumer<ImportJob> change) {
        tx.executeWithoutResult(s -> change.accept(jobs.findById(id).orElseThrow()));
    }
}
