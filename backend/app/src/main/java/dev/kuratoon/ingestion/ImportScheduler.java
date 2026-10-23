package dev.kuratoon.ingestion;

import dev.kuratoon.config.KuratoonProperties;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Background ingestion: once the app is ready, startup recovery runs, then every
 * {@code IMPORT_POLL_SECONDS} the import folder is scanned and queued jobs are processed, one at
 * a time on a single thread.
 */
@Component
public class ImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImportScheduler.class);

    private final ImportRecovery recovery;
    private final ImportScanner scanner;
    private final ImportWorker worker;
    private final boolean enabled;
    private final Duration interval;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("import-worker").factory());

    ImportScheduler(ImportRecovery recovery, ImportScanner scanner, ImportWorker worker, KuratoonProperties properties) {
        this.recovery = recovery;
        this.scanner = scanner;
        this.worker = worker;
        this.enabled = properties.importPolling().enabled();
        this.interval = properties.importPolling().pollInterval();
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        if (!enabled) {
            return;
        }
        executor.execute(() -> {
            try {
                recovery.recoverOnStartup();
            } catch (RuntimeException e) {
                log.error("Import startup recovery failed", e);
            }
        });
        executor.scheduleWithFixedDelay(this::poll, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Import polling every {} s", interval.toSeconds());
    }

    /** Processes queued jobs now (after an admin action) instead of at the next poll. */
    public void trigger() {
        if (enabled && !executor.isShutdown()) {
            executor.execute(this::processQueued);
        }
    }

    private void processQueued() {
        try {
            worker.processAll();
        } catch (RuntimeException e) {
            log.error("Import processing failed", e);
        }
    }

    private void poll() {
        try {
            scanner.scanOnce();
            worker.processAll();
        } catch (RuntimeException e) {
            // Never let an exception cancel the schedule.
            log.error("Import poll failed", e);
        }
    }

    @PreDestroy
    void stop() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}
