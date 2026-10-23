package dev.kuratoon.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.util.FileSystemUtils;

/**
 * The /import, /library and /data folders of the test application context: one temp root per
 * JVM (so every integration test shares one Spring context), emptied before each test.
 */
public final class TestFolders {

    public static final Path ROOT;

    static {
        try {
            ROOT = Files.createTempDirectory("kuratoon-it-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static final Path IMPORT = ROOT.resolve("import");
    public static final Path LIBRARY = ROOT.resolve("library");
    public static final Path DATA = ROOT.resolve("data");
    public static final Path PLUGINS = ROOT.resolve("plugins");

    private TestFolders() {}

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("kuratoon.paths.import-dir", IMPORT::toString);
        registry.add("kuratoon.paths.library-dir", LIBRARY::toString);
        registry.add("kuratoon.paths.data-dir", DATA::toString);
        registry.add("kuratoon.paths.plugins-dir", PLUGINS::toString);
    }

    /** Empties all folders (keeping the folders themselves). */
    public static void reset() {
        for (Path dir : new Path[] {IMPORT, LIBRARY, DATA, PLUGINS}) {
            try {
                if (Files.exists(dir)) {
                    makeWritable(dir);
                    FileSystemUtils.deleteRecursively(dir);
                }
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Undoes permission changes made by tests, so cleanup can delete everything. */
    public static void makeWritable(Path dir) throws IOException {
        try (Stream<Path> all = Files.walk(dir)) {
            for (Path p : all.toList()) {
                p.toFile().setWritable(true);
                if (Files.isDirectory(p)) {
                    p.toFile().setExecutable(true);
                    p.toFile().setReadable(true);
                }
            }
        }
    }
}
