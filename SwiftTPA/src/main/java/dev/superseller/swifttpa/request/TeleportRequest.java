package dev.superseller.swifttpa.request;

import java.util.UUID;

/**
 * One pending teleport request. Immutable and free of Bukkit types so the
 * queue logic can be unit-tested without a server.
 *
 * @param sender      player who sent the request
 * @param target      player who received (and can answer) the request
 * @param type        whether the sender travels ({@link RequestType#TPA}) or
 *                    the target travels ({@link RequestType#TPA_HERE})
 * @param createdAtMs creation wall-clock time in milliseconds
 * @param expiresAtMs expiry wall-clock time in milliseconds; {@code 0} means
 *                    the request never expires
 */
public record TeleportRequest(UUID sender, UUID target, RequestType type, long createdAtMs, long expiresAtMs) {

    /** True when the request is older than its expiry time. */
    public boolean isExpired(long nowMs) {
        return expiresAtMs > 0 && nowMs >= expiresAtMs;
    }

    /** Seconds until expiry for displays; 0 when the request never expires. */
    public long secondsLeft(long nowMs) {
        if (expiresAtMs <= 0) {
            return 0L;
        }
        long leftMs = expiresAtMs - nowMs;
        return leftMs <= 0 ? 0L : (leftMs + 999L) / 1000L;
    }

    /** Seconds since the request was created, for GUI lore. */
    public long ageSeconds(long nowMs) {
        return Math.max(0L, (nowMs - createdAtMs) / 1000L);
    }

    /** The player who will move when this request is accepted. */
    public UUID mover() {
        return type == RequestType.TPA ? sender : target;
    }

    /** The player who stays in place when this request is accepted. */
    public UUID anchor() {
        return type == RequestType.TPA ? target : sender;
    }
}
