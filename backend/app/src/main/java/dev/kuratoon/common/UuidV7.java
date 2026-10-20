package dev.kuratoon.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7 (RFC 9562) generator for entity ids (ADR 0004). Ids are assigned by the application, not
 * by the database, so a restored sidecar can re-create rows with their original ids.
 *
 * <p>Layout: 48-bit Unix millis, version, 12-bit {@code rand_a} used as a counter within the same
 * millisecond (RFC 9562 method 1), variant, 62 random bits. Ids are strictly increasing per JVM.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int COUNTER_MAX = 0xFFF;

    private static long lastMillis = -1;
    private static int counter;

    private UuidV7() {}

    public static UUID generate() {
        long millis;
        int seq;
        synchronized (UuidV7.class) {
            millis = Math.max(System.currentTimeMillis(), lastMillis);
            if (millis == lastMillis) {
                if (counter == COUNTER_MAX) {
                    // Counter exhausted: borrow the next millisecond to stay monotonic.
                    millis++;
                    counter = RANDOM.nextInt(0x800);
                } else {
                    counter++;
                }
            } else {
                // Start low in the counter space so it rarely overflows.
                counter = RANDOM.nextInt(0x800);
            }
            lastMillis = millis;
            seq = counter;
        }
        long msb = (millis << 16) | (0x7L << 12) | seq;
        long lsb = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    /** The Unix-millisecond timestamp embedded in a UUIDv7. */
    public static long timestampMillis(UUID id) {
        return id.getMostSignificantBits() >>> 16;
    }
}
