package dev.superseller.veltryx.teleport;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Teleport-request bookkeeping: pending {@code /tpa} and {@code /tpahere}
 * requests plus the per-counterpart send cooldown. Pure logic (UUIDs and
 * millis only) so it can be unit tested offline.
 *
 * <p>The cooldown is tracked <b>per counterpart</b>: you may ask several
 * different players at once, but the same player only once per cooldown —
 * sending to B never blocks your cooldown towards A.
 */
public final class TpaService {

    /** One pending teleport request aimed at a target player. */
    public record Request(UUID from, UUID to, boolean here, long sentAt) {
        /** Whether the request is older than the timeout. */
        public boolean expired(long nowMillis, long timeoutMillis) {
            return nowMillis - sentAt > timeoutMillis;
        }
    }

    /** target -> newest request. */
    private final Map<UUID, Request> pending = new HashMap<>();
    /** sender -> target -> last send time. */
    private final Map<UUID, Map<UUID, Long>> sentAt = new HashMap<>();

    /** Millis still to wait before {@code from} may send {@code to} again. */
    public long cooldownLeftMillis(UUID from, UUID to, long nowMillis, long cooldownMillis) {
        Map<UUID, Long> targets = sentAt.get(from);
        if (targets == null) {
            return 0L;
        }
        Long last = targets.get(to);
        if (last == null) {
            return 0L;
        }
        return Math.max(0L, last + cooldownMillis - nowMillis);
    }

    /** Records a send towards the counterpart (starts the cooldown). */
    public void recordSend(UUID from, UUID to, long nowMillis) {
        sentAt.computeIfAbsent(from, key -> new HashMap<>()).put(to, nowMillis);
    }

    /**
     * Records a new request for the target (replacing any older one).
     *
     * @param here false = the sender wants to come over, true = the sender
     *             wants the target to come over
     */
    public Request send(UUID from, UUID to, boolean here, long nowMillis) {
        Request request = new Request(from, to, here, nowMillis);
        pending.put(to, request);
        return request;
    }

    /** Takes (removes) the pending request for the target, or {@code null}. */
    public Request takePending(UUID target) {
        return pending.remove(target);
    }

    /** The currently pending request for the target, or {@code null}. */
    public Request peekPending(UUID target) {
        return pending.get(target);
    }
}
