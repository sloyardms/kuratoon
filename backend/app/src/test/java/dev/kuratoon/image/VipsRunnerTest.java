package dev.kuratoon.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Process handling, with a fake tool so it runs without libvips on any OS. */
class VipsRunnerTest {

    private static VipsRunner fake(Duration timeout) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new VipsRunner(
                List.of(java, "-cp", System.getProperty("java.class.path"), FakeVips.class.getName()), timeout);
    }

    @Test
    void returnsStdoutAndPassesArgumentsWithoutAShell() throws Exception {
        assertThat(fake(Duration.ofSeconds(30)).run("echo", "a b", "$HOME;", "x[Q=80]").strip())
                .isEqualTo("echo a b $HOME; x[Q=80]");
    }

    @Test
    void nonZeroExitIsAnErrorWithStderr() {
        assertThatThrownBy(() -> fake(Duration.ofSeconds(30)).run("fail"))
                .isInstanceOf(ImageToolException.class)
                .hasMessageContaining("exit 3")
                .hasMessageContaining("unable to load");
    }

    @Test
    void timeoutKillsTheProcess() {
        Instant start = Instant.now();
        assertThatThrownBy(() -> fake(Duration.ofSeconds(2)).run("sleep"))
                .isInstanceOf(ImageToolException.class)
                .hasMessageContaining("timed out after 2 s");
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(20));
    }

    @Test
    void largeOutputDoesNotBlockAndIsCapped() throws Exception {
        String out = fake(Duration.ofSeconds(30)).run("flood");
        assertThat(out).hasSizeLessThanOrEqualTo(VipsRunner.MAX_OUTPUT_BYTES);
    }

    @Test
    void missingExecutableIsAnError() {
        VipsRunner missing = new VipsRunner(List.of("kuratoon-no-such-vips-binary"), Duration.ofSeconds(5));
        assertThatThrownBy(() -> missing.run("--version"))
                .isInstanceOf(ImageToolException.class)
                .hasMessageContaining("could not start");
        assertThat(missing.version()).isEmpty();
    }
}
