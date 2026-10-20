package dev.kuratoon.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void hasVersion7AndIetfVariant() {
        UUID id = UuidV7.generate();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsCurrentUnixMillis() {
        long before = System.currentTimeMillis();
        UUID id = UuidV7.generate();
        long after = System.currentTimeMillis();
        assertThat(UuidV7.timestampMillis(id)).isBetween(before, after);
    }

    @Test
    void isStrictlyIncreasingAndUniqueEvenWithinOneMillisecond() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            ids.add(UuidV7.generate());
        }
        for (int i = 1; i < ids.size(); i++) {
            // String order equals byte order for the canonical form, which is what Postgres sorts by.
            assertThat(ids.get(i).toString()).isGreaterThan(ids.get(i - 1).toString());
        }
        assertThat(new HashSet<>(ids)).hasSameSizeAs(ids);
    }
}
