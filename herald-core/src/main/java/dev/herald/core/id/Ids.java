package dev.herald.core.id;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates RFC 9562 version-7 UUIDs.
 *
 * <p>UUIDv7 embeds a millisecond Unix timestamp in the most significant bits, so ids sort by
 * creation time. That keeps B-tree index inserts append-only and lets cursor pagination use the id
 * alone.
 */
public final class Ids {

    private Ids() {}

    public static UUID newId() {
        return newId(System.currentTimeMillis());
    }

    static UUID newId(long epochMillis) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long msb = (epochMillis << 16) | 0x7000L | (random.nextInt() & 0x0FFFL);
        long lsb = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    /** Extracts the creation timestamp from a UUIDv7. */
    public static long epochMillis(UUID id) {
        if (id.version() != 7) {
            throw new IllegalArgumentException("Not a UUIDv7: " + id);
        }
        return id.getMostSignificantBits() >>> 16;
    }
}
