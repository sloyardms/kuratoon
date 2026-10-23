package dev.kuratoon.image;

import dev.kuratoon.config.KuratoonProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Runs the libvips CLI ({@code vips <operation> ...}) as a child process: an argument list, never
 * a shell. Output is captured (bounded), and a process that exceeds the timeout is killed.
 */
@Component
public class VipsRunner {

    private static final Logger log = LoggerFactory.getLogger(VipsRunner.class);

    /** Captured stdout/stderr per stream; the rest is discarded. */
    static final int MAX_OUTPUT_BYTES = 64 * 1024;

    private final List<String> command;
    private final Duration timeout;

    @Autowired
    public VipsRunner(KuratoonProperties properties) {
        this(List.of(properties.images().vipsBinary()), properties.images().commandTimeout());
    }

    /** @param command the executable and any fixed leading arguments (tests use a fake tool) */
    public VipsRunner(List<String> command, Duration timeout) {
        this.command = List.copyOf(command);
        this.timeout = timeout;
    }

    /**
     * Runs {@code vips <args>} and returns its stdout.
     *
     * @throws ImageToolException on a non-zero exit, a timeout or a failure to start
     */
    public String run(String... args) throws ImageToolException {
        List<String> cmd = new ArrayList<>(command);
        cmd.addAll(List.of(args));
        Process process;
        try {
            process = new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            throw new ImageToolException("could not start " + command.getFirst() + ": " + e.getMessage(), e);
        }
        try {
            process.getOutputStream().close();
            Capture stdout = Capture.start(process.getInputStream());
            Capture stderr = Capture.start(process.getErrorStream());
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new ImageToolException(describe(args) + " timed out after " + timeout.toSeconds() + " s");
            }
            String err = stderr.text();
            if (process.exitValue() != 0) {
                throw new ImageToolException(
                        describe(args) + " failed (exit " + process.exitValue() + "): " + tail(err.strip()));
            }
            if (!err.isBlank()) {
                log.debug("{}: {}", describe(args), tail(err.strip()));
            }
            return stdout.text();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ImageToolException(describe(args) + " interrupted", e);
        } catch (IOException e) {
            process.destroyForcibly();
            throw new ImageToolException(describe(args) + " failed: " + e.getMessage(), e);
        }
    }

    /** {@code vips --version} output, or empty when vips is not available. */
    public Optional<String> version() {
        try {
            return Optional.of(run("--version").strip());
        } catch (ImageToolException e) {
            return Optional.empty();
        }
    }

    private static String describe(String... args) {
        return "vips " + (args.length == 0 ? "" : args[0]);
    }

    private static String tail(String text) {
        return text.length() <= 500 ? text : "…" + text.substring(text.length() - 500);
    }

    /** Drains a stream on a virtual thread, keeping the first {@link #MAX_OUTPUT_BYTES}. */
    private static final class Capture {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final Thread thread;

        private Capture(InputStream in) {
            this.thread = Thread.ofVirtual().start(() -> {
                byte[] chunk = new byte[8192];
                try (in) {
                    int n;
                    while ((n = in.read(chunk)) >= 0) {
                        synchronized (buffer) {
                            int room = MAX_OUTPUT_BYTES - buffer.size();
                            if (room > 0) {
                                buffer.write(chunk, 0, Math.min(n, room));
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // Process killed or stream closed: keep what was read.
                }
            });
        }

        static Capture start(InputStream in) {
            return new Capture(in);
        }

        String text() throws InterruptedException {
            thread.join(Duration.ofSeconds(5));
            synchronized (buffer) {
                return buffer.toString(StandardCharsets.UTF_8);
            }
        }
    }
}
