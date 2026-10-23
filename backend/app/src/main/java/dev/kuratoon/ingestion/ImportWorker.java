package dev.kuratoon.ingestion;

import dev.kuratoon.common.BadRequestException;
import dev.kuratoon.ingestion.ImportFaults.Step;
import dev.kuratoon.ingestion.ImportFinisher.CannotFinishException;
import dev.kuratoon.ingestion.ImportProcessor.StagingException;
import dev.kuratoon.ingestion.ImportResolver.Failed;
import dev.kuratoon.ingestion.ImportResolver.NeedsAttention;
import dev.kuratoon.ingestion.ImportResolver.Outcome;
import dev.kuratoon.ingestion.ImportResolver.Ready;
import dev.kuratoon.ingestion.JobDetail.Attention;
import java.io.IOException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;

/**
 * Runs import jobs one at a time: QUEUED → RESOLVING → PROCESSING → DONE, or NEEDS_ATTENTION /
 * FAILED (docs/spec/02-ingestion.md "Job lifecycle"). Job state changes are short transactions of
 * their own; file work happens outside them.
 */
@Service
public class ImportWorker {

    private static final Logger log = LoggerFactory.getLogger(ImportWorker.class);

    private final ImportJobRepository jobs;
    private final ImportResolver resolver;
    private final ImportProcessor processor;
    private final ImportCommitter committer;
    private final ImportFinisher finisher;
    private final ImportFaults faults;
    private final LibraryPaths paths;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ImportWorker(
            ImportJobRepository jobs,
            ImportResolver resolver,
            ImportProcessor processor,
            ImportCommitter committer,
            ImportFinisher finisher,
            ImportFaults faults,
            LibraryPaths paths,
            TransactionTemplate tx,
            Clock clock) {
        this.jobs = jobs;
        this.resolver = resolver;
        this.processor = processor;
        this.committer = committer;
        this.finisher = finisher;
        this.faults = faults;
        this.paths = paths;
        this.tx = tx;
        this.clock = clock;
    }

    /** Claims and runs the oldest QUEUED job. Empty when there is none. */
    public synchronized Optional<UUID> processNext() {
        UUID jobId = tx.execute(s -> jobs.lockNextQueued().map(id -> {
            ImportJob job = jobs.findById(id).orElseThrow();
            job.startAttempt(clock.instant());
            return id;
        }).orElse(null));
        if (jobId == null) {
            return Optional.empty();
        }
        run(jobId);
        return Optional.of(jobId);
    }

    /** Runs every QUEUED job (tests and the poller). */
    public void processAll() {
        while (processNext().isPresent()) {
            // next
        }
    }

    private void run(UUID jobId) {
        try {
            ImportJob job = tx.execute(s -> jobs.findById(jobId).orElseThrow());
            if (job.getCommittedAt() != null) {
                // Retry of a job saved earlier: finish it, never resolve it again.
                finishCommitted(jobId);
                return;
            }
            Outcome outcome = resolver.resolve(job);
            switch (outcome) {
                case Failed f -> update(jobId, j -> j.moveTo(ImportStatus.FAILED, f.message(), clock.instant()));
                case NeedsAttention na -> update(jobId, j -> {
                    j.setKind(na.kind());
                    j.setMediaId(na.mediaId());
                    j.setDetail(na.detail());
                    j.moveTo(ImportStatus.NEEDS_ATTENTION, na.message(), clock.instant());
                });
                case Ready ready -> process(jobId, ready);
            }
        } catch (RuntimeException e) {
            fail(jobId, e);
        }
    }

    private void process(UUID jobId, Ready ready) {
        ImportPlan plan = ready.plan();
        update(jobId, j -> {
            j.setKind(plan.kind());
            j.setMediaId(plan.kind() == ImportKind.UPDATE ? plan.targetMediaId() : null);
            j.setDetail(ready.detail());
            j.moveTo(ImportStatus.PROCESSING, null, clock.instant());
        });
        try {
            processor.stage(jobId, plan);
        } catch (StagingException e) {
            attention(jobId, Attention.UNREADABLE_IMAGE, e.getMessage());
            return;
        } catch (IOException e) {
            failBeforeCommit(jobId, "Copying into the library failed: " + e.getMessage());
            return;
        }
        try {
            committer.commit(jobId, plan);
        } catch (BadRequestException e) {
            cleanStaging(jobId);
            attention(jobId, Attention.INCOMPLETE_METADATA, e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.error("Import job {} failed in the commit transaction", jobId, e);
            failBeforeCommit(jobId, "Saving the import failed: " + e.getMessage());
            return;
        }
        faults.checkpoint(Step.AFTER_COMMIT);
        finishCommitted(jobId);
    }

    /** Steps 4–6 of a committed job; on failure the job needs attention and Retry finishes it. */
    public void finishCommitted(UUID jobId) {
        try {
            finisher.finish(jobId);
        } catch (CannotFinishException e) {
            attention(jobId, Attention.RECOVERY, "The import was saved but could not be completed: " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            log.error("Import job {} failed after commit", jobId, e);
            attention(jobId, Attention.RECOVERY, "The import was saved but could not be completed: " + e.getMessage()
                    + ". Press Retry to finish it.");
        }
    }

    private void fail(UUID jobId, RuntimeException e) {
        log.error("Import job {} failed", jobId, e);
        ImportJob job = tx.execute(s -> jobs.findById(jobId).orElse(null));
        if (job != null && job.getCommittedAt() != null) {
            attention(jobId, Attention.RECOVERY, "The import was saved but could not be completed: " + e.getMessage()
                    + ". Press Retry to finish it.");
        } else {
            failBeforeCommit(jobId, "Unexpected error: " + e.getMessage());
        }
    }

    private void failBeforeCommit(UUID jobId, String message) {
        cleanStaging(jobId);
        update(jobId, j -> j.moveTo(ImportStatus.FAILED, message, clock.instant()));
    }

    private void attention(UUID jobId, Attention reason, String message) {
        update(jobId, j -> {
            j.setDetail(j.detail().withAttention(reason));
            j.moveTo(ImportStatus.NEEDS_ATTENTION, message, clock.instant());
        });
    }

    private void cleanStaging(UUID jobId) {
        try {
            FileSystemUtils.deleteRecursively(paths.staging(jobId));
        } catch (IOException e) {
            log.warn("Could not remove staging of job {}: {}", jobId, e.toString());
        }
    }

    private void update(UUID jobId, Consumer<ImportJob> change) {
        tx.executeWithoutResult(s -> change.accept(jobs.findById(jobId).orElseThrow()));
    }
}
