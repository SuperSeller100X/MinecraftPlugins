package dev.superseller.swifttpa.request;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thread-safe queue of pending teleport requests. Pure data structure — no
 * Bukkit types — so every rule can be unit-tested without a server:
 *
 * <ul>
 *   <li>Incoming requests are kept per target, oldest first; the "latest"
 *       request (what bare /tpaccept answers) is the tail.</li>
 *   <li>With {@code one-outgoing} enabled a sender keeps a single outgoing
 *       request: sending a new one replaces (and returns) the old one.</li>
 *   <li>Each target queue is capped: when full, the oldest request is
 *       evicted and returned so both players can be notified.</li>
 * </ul>
 */
public final class RequestStore {

    /** Incoming queues keyed by target, FIFO per target. */
    private final Map<UUID, ArrayDeque<TeleportRequest>> incoming = new LinkedHashMap<>();

    /** Outgoing requests keyed by sender (only meaningful when one-outgoing is on; also used as an index). */
    private final Map<UUID, TeleportRequest> outgoing = new HashMap<>();

    /** Result of {@link #add}: everything that was displaced by the insert. */
    public record AddResult(TeleportRequest replacedOutgoing, TeleportRequest evictedOldest) {

        /** True when an older outgoing request of the same sender was replaced. */
        public boolean replaced() {
            return replacedOutgoing != null;
        }

        /** True when the target's full queue dropped its oldest entry. */
        public boolean evicted() {
            return evictedOldest != null;
        }
    }

    /**
     * Inserts a request applying the one-outgoing and per-target cap rules.
     *
     * @param request       the new request
     * @param oneOutgoing   replace the sender's previous outgoing request
     * @param maxPerTarget  maximum queue length per target (>= 1)
     * @return displaced entries for notification purposes
     */
    public synchronized AddResult add(TeleportRequest request, boolean oneOutgoing, int maxPerTarget) {
        TeleportRequest replaced = null;
        if (oneOutgoing) {
            TeleportRequest previous = outgoing.get(request.sender());
            if (previous != null) {
                remove(previous.sender(), previous.target());
                replaced = previous;
            }
        }
        TeleportRequest evicted = null;
        ArrayDeque<TeleportRequest> queue =
                incoming.computeIfAbsent(request.target(), k -> new ArrayDeque<>());
        int cap = Math.max(1, maxPerTarget);
        while (queue.size() >= cap) {
            TeleportRequest dropped = queue.pollFirst();
            if (dropped == null) {
                break;
            }
            if (evicted == null) {
                evicted = dropped;
            }
            outgoing.remove(dropped.sender(), dropped);
        }
        queue.addLast(request);
        outgoing.put(request.sender(), request);
        return new AddResult(replaced, evicted);
    }

    /** All pending requests for a target, oldest first. */
    public synchronized List<TeleportRequest> incoming(UUID target) {
        ArrayDeque<TeleportRequest> queue = incoming.get(target);
        if (queue == null) {
            return List.of();
        }
        return new ArrayList<>(queue);
    }

    /** The newest request for a target — what bare /tpaccept answers. */
    public synchronized Optional<TeleportRequest> latestIncoming(UUID target) {
        ArrayDeque<TeleportRequest> queue = incoming.get(target);
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(queue.peekLast());
    }

    /** Finds the request one specific sender sent to a target. */
    public synchronized Optional<TeleportRequest> findIncoming(UUID target, UUID sender) {
        ArrayDeque<TeleportRequest> queue = incoming.get(target);
        if (queue == null) {
            return Optional.empty();
        }
        for (TeleportRequest request : queue) {
            if (request.sender().equals(sender)) {
                return Optional.of(request);
            }
        }
        return Optional.empty();
    }

    /** The outgoing request of a sender, if any. */
    public synchronized Optional<TeleportRequest> outgoing(UUID sender) {
        return Optional.ofNullable(outgoing.get(sender));
    }

    /** Removes a specific request. Returns true when it was present. */
    public synchronized boolean remove(UUID sender, UUID target) {
        ArrayDeque<TeleportRequest> queue = incoming.get(target);
        if (queue == null) {
            return false;
        }
        boolean removed = queue.removeIf(r -> r.sender().equals(sender));
        if (queue.isEmpty()) {
            incoming.remove(target);
        }
        if (removed) {
            outgoing.remove(sender);
        }
        return removed;
    }

    /** Removes a concrete request instance; used when the answer was already looked up. */
    public synchronized boolean remove(TeleportRequest request) {
        return remove(request.sender(), request.target());
    }

    /** Removes and returns the outgoing request of a sender, if any. */
    public synchronized Optional<TeleportRequest> removeOutgoing(UUID sender) {
        TeleportRequest request = outgoing.remove(sender);
        if (request == null) {
            return Optional.empty();
        }
        ArrayDeque<TeleportRequest> queue = incoming.get(request.target());
        if (queue != null) {
            queue.removeIf(r -> r.sender().equals(sender));
            if (queue.isEmpty()) {
                incoming.remove(request.target());
            }
        }
        return Optional.of(request);
    }

    /** Removes and returns every request addressed to a target (e.g. on quit). */
    public synchronized List<TeleportRequest> removeIncomingFor(UUID target) {
        ArrayDeque<TeleportRequest> queue = incoming.remove(target);
        if (queue == null) {
            return List.of();
        }
        List<TeleportRequest> removed = new ArrayList<>(queue);
        for (TeleportRequest request : removed) {
            outgoing.remove(request.sender(), request);
        }
        return removed;
    }

    /** Removes and returns every request involving a player (either side). */
    public synchronized List<TeleportRequest> removeAllInvolving(UUID player) {
        List<TeleportRequest> removed = new ArrayList<>();
        Optional<TeleportRequest> out = removeOutgoing(player);
        out.ifPresent(removed::add);
        removed.addAll(removeIncomingFor(player));
        return removed;
    }

    /** Removes and returns every request whose expiry point has passed. */
    public synchronized List<TeleportRequest> expire(long nowMs) {
        List<TeleportRequest> expired = new ArrayList<>();
        incoming.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(request -> {
                if (request.isExpired(nowMs)) {
                    expired.add(request);
                    outgoing.remove(request.sender(), request);
                    return true;
                }
                return false;
            });
            return entry.getValue().isEmpty();
        });
        return expired;
    }

    /** Every pending request, for admin listings. */
    public synchronized List<TeleportRequest> all() {
        List<TeleportRequest> all = new ArrayList<>();
        for (ArrayDeque<TeleportRequest> queue : incoming.values()) {
            all.addAll(queue);
        }
        return new ArrayList<>(all);
    }

    /** Number of pending requests across all players. */
    public synchronized int size() {
        int size = 0;
        for (ArrayDeque<TeleportRequest> queue : incoming.values()) {
            size += queue.size();
        }
        return size;
    }

    /** Drops everything (plugin disable / admin clear). Returns how many requests were removed. */
    public synchronized int clear() {
        int size = size();
        incoming.clear();
        outgoing.clear();
        return size;
    }
}
