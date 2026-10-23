package dev.kuratoon.ingestion;

import dev.kuratoon.config.KuratoonProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Polls /import (docs/spec/02-ingestion.md "Folder detection"): a top-level folder becomes a QUEUED
 * job once its file count and total size have not changed for {@code IMPORT_STABLE_SECONDS}.
 * Folders starting with "." and folders owned by an unfinished job are ignored. Observations are
 * kept in memory; after a restart the stability window starts again.
 */
@Service
public class ImportScanner {

    private static final Logger log = LoggerFactory.getLogger(ImportScanner.class);

    record Observation(long files, long bytes, Instant since) {}

    private final LibraryPaths paths;
    private final ImportJobRepository jobs;
    private final Clock clock;
    private final Duration stableAfter;
    private final Map<String, Observation> seen = new ConcurrentHashMap<>();

    public ImportScanner(LibraryPaths paths, ImportJobRepository jobs, Clock clock, KuratoonProperties properties) {
        this.paths = paths;
        this.jobs = jobs;
        this.clock = clock;
        this.stableAfter = properties.importPolling().stableAfter();
    }

    /** One poll. Returns the folders queued by it. */
    public synchronized List<String> scanOnce() {
        Path importDir = paths.importDir();
        if (!Files.isDirectory(importDir)) {
            log.warn("Import folder {} does not exist", importDir);
            return List.of();
        }
        Set<String> active = new HashSet<>(jobs.findActiveSourceFolders());
        Set<String> present = new HashSet<>();
        List<String> queued = new ArrayList<>();
        Instant now = clock.instant();
        for (Path folder : candidates(importDir)) {
            String name = folder.getFileName().toString();
            present.add(name);
            if (active.contains(name)) {
                seen.remove(name);
                continue;
            }
            Observation current;
            try {
                current = measure(folder, now);
            } catch (UncheckedIOException e) {
                // Being written or removed right now: look again next poll.
                seen.remove(name);
                continue;
            }
            Observation previous = seen.get(name);
            if (previous == null || previous.files() != current.files() || previous.bytes() != current.bytes()) {
                seen.put(name, current);
                continue;
            }
            if (Duration.between(previous.since(), now).compareTo(stableAfter) >= 0) {
                try {
                    jobs.saveAndFlush(new ImportJob(name, now));
                    queued.add(name);
                    log.info("Queued import of '{}'", name);
                } catch (DataIntegrityViolationException e) {
                    // Another node or an admin action queued it first.
                    log.debug("Import of '{}' already queued", name);
                }
                seen.remove(name);
            }
        }
        seen.keySet().retainAll(present);
        return queued;
    }

    private static List<Path> candidates(Path importDir) {
        try (Stream<Path> entries = Files.list(importDir)) {
            return entries.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Observation measure(Path folder, Instant now) {
        long[] totals = new long[2];
        try (Stream<Path> files = Files.walk(folder)) {
            files.forEach(p -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    if (attrs.isRegularFile()) {
                        totals[0]++;
                        totals[1] += attrs.size();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Observation(totals[0], totals[1], now);
    }
}
