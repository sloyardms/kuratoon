package dev.kuratoon.ingestion;

import dev.kuratoon.config.KuratoonProperties;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Folder layout of /import and /library used by ingestion (docs/spec/02-ingestion.md). */
@Component
public class LibraryPaths {

    public static final String STAGING = ".staging";
    public static final String IMPORTED_PREFIX = ".imported-";
    public static final String DISMISSED_PREFIX = ".dismissed-";
    public static final String DELETED_PREFIX = ".deleted-";

    private final Path importDir;
    private final Path libraryDir;

    public LibraryPaths(KuratoonProperties properties) {
        this.importDir = properties.paths().importDir();
        this.libraryDir = properties.paths().libraryDir();
    }

    public Path importDir() {
        return importDir;
    }

    public Path libraryDir() {
        return libraryDir;
    }

    public Path source(String sourceFolder) {
        return importDir.resolve(sourceFolder);
    }

    /** Step 6 renames the source here before deleting it, so it is never picked up again. */
    public Path imported(String sourceFolder, UUID jobId) {
        return importDir.resolve(IMPORTED_PREFIX + sourceFolder + "-" + jobId);
    }

    public Path dismissed(String sourceFolder, UUID jobId) {
        return importDir.resolve(DISMISSED_PREFIX + sourceFolder + "-" + jobId);
    }

    public Path mediaDir(UUID mediaId) {
        return libraryDir.resolve(mediaId.toString());
    }

    /** A media folder being deleted: renamed first, so a rebuild cannot resurrect it. */
    public Path deletedMediaDir(UUID mediaId) {
        return libraryDir.resolve(DELETED_PREFIX + mediaId);
    }

    public Path stagingRoot() {
        return libraryDir.resolve(STAGING);
    }

    public Path staging(UUID jobId) {
        return stagingRoot().resolve(jobId.toString());
    }

    /** NEW/RESTORE: the whole media folder; UPDATE: only the new or replacing chapter folders. */
    public Path stagedMedia(UUID jobId) {
        return staging(jobId).resolve("media");
    }

    /** Old chapter folders moved aside during a Replace swap. */
    public Path backup(UUID jobId) {
        return staging(jobId).resolve("backup");
    }
}
