package dev.kuratoon.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** Startup must fail fast with a clear message when required settings are missing or invalid. */
class KuratoonPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues(
                    "kuratoon.app-base-url=https://manga.example.com",
                    "kuratoon.jwt-secret=0123456789abcdef0123456789abcdef",
                    "kuratoon.image-signing-secret=signing-secret");

    @Test
    void validConfigurationBindsWithDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            KuratoonProperties props = context.getBean(KuratoonProperties.class);
            assertThat(props.appBaseUrl()).isEqualTo(URI.create("https://manga.example.com"));
            assertThat(props.imageAccess()).isEqualTo(ImageAccess.SIGNED);
            assertThat(props.defaultLanguage()).isEqualTo("en");
            assertThat(props.paths().importDir()).isEqualTo(Path.of("/import"));
            assertThat(props.paths().libraryDir()).isEqualTo(Path.of("/library"));
            assertThat(props.paths().dataDir()).isEqualTo(Path.of("/data"));
            assertThat(props.paths().pluginsDir()).isEqualTo(Path.of("/plugins"));
            assertThat(props.solver().url()).isEqualTo(URI.create("http://byparr:8191"));
            assertThat(props.solver().enabled()).isTrue();
            assertThat(props.solver().timeout()).isEqualTo(Duration.ofMillis(60000));
            assertThat(props.importPolling().pollInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(props.importPolling().stableAfter()).isEqualTo(Duration.ofSeconds(60));
        });
    }

    @Test
    void missingAppBaseUrlFails() {
        runner.withPropertyValues("kuratoon.app-base-url=")
                .run(context -> assertFailureMentions(context, "APP_BASE_URL"));
    }

    @Test
    void missingJwtSecretFails() {
        runner.withPropertyValues("kuratoon.jwt-secret=")
                .run(context -> assertFailureMentions(context, "JWT_SECRET"));
    }

    @Test
    void shortJwtSecretFails() {
        runner.withPropertyValues("kuratoon.jwt-secret=too-short")
                .run(context -> assertFailureMentions(context, "JWT_SECRET must be at least 32 bytes"));
    }

    @Test
    void signedModeRequiresSigningSecret() {
        runner.withPropertyValues("kuratoon.image-signing-secret=")
                .run(context -> assertFailureMentions(context, "IMAGE_SIGNING_SECRET"));
    }

    @Test
    void publicModeDoesNotRequireSigningSecret() {
        runner.withPropertyValues("kuratoon.image-access=public", "kuratoon.image-signing-secret=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KuratoonProperties.class).imageAccess())
                            .isEqualTo(ImageAccess.PUBLIC);
                });
    }

    @Test
    void invalidImageAccessFails() {
        runner.withPropertyValues("kuratoon.image-access=open").run(context -> assertThat(context)
                .hasFailed());
    }

    @Test
    void emptySolverUrlDisablesSolver() {
        runner.withPropertyValues("kuratoon.solver.endpoint=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KuratoonProperties.class).solver().enabled()).isFalse();
        });
    }

    private static void assertFailureMentions(AssertableApplicationContext context, String text) {
        assertThat(context).hasFailed();
        StringBuilder messages = new StringBuilder();
        for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        assertThat(messages.toString()).contains(text);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KuratoonProperties.class)
    static class PropertiesConfig {}
}
