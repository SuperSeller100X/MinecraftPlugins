package dev.superseller.veltryx.util;

import java.util.UUID;

/** Small shared helpers. */
public final class Ids {

    private Ids() {
    }

    /** A short, human-readable, collision-resistant id (8 hex characters). */
    public static String next() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
