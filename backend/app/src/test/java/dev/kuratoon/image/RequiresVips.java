package dev.kuratoon.image;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Runs the test only when the {@code vips} CLI is on PATH. With
 * {@code KURATOON_TEST_REQUIRE_VIPS=true} (set in CI) a missing vips fails the test instead of
 * skipping it, so the thumbnail tests can never silently not run there.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RequiresVips.Condition.class)
public @interface RequiresVips {

    final class Condition implements ExecutionCondition {

        private static volatile Boolean available;

        public static boolean available() {
            if (available == null) {
                available = new VipsRunner(List.of("vips"), Duration.ofSeconds(10)).version().isPresent();
            }
            return available;
        }

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (available()) {
                return ConditionEvaluationResult.enabled("vips is available");
            }
            if ("true".equalsIgnoreCase(System.getenv("KURATOON_TEST_REQUIRE_VIPS"))) {
                throw new IllegalStateException("KURATOON_TEST_REQUIRE_VIPS=true but the vips CLI is not on PATH");
            }
            return ConditionEvaluationResult.disabled("vips CLI not installed (install libvips-tools to run)");
        }
    }
}
