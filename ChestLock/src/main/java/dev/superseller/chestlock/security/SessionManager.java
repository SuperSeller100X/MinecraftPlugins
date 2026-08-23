package dev.superseller.chestlock.security;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe transient unlock, bypass, and double-break-confirmation state. */
public final class SessionManager {
    private final ConcurrentMap<AccessKey, Long> unlocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<AccessKey, Long> breakConfirmations = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> bypass = ConcurrentHashMap.newKeySet();

    public void authorize(UUID playerId, UUID lockId, long expiresAtMillis) {
        unlocks.put(new AccessKey(playerId, lockId), expiresAtMillis);
    }

    public boolean isAuthorized(UUID playerId, UUID lockId, long nowMillis) {
        AccessKey key = new AccessKey(playerId, lockId);
        Long expiry = unlocks.get(key);
        if (expiry == null) {
            return false;
        }
        if (expiry <= nowMillis) {
            unlocks.remove(key, expiry);
            breakConfirmations.remove(key);
            return false;
        }
        return true;
    }

    public long remaining(UUID playerId, UUID lockId, long nowMillis) {
        AccessKey key = new AccessKey(playerId, lockId);
        Long expiry = unlocks.get(key);
        return expiry == null ? 0 : Math.max(0, expiry - nowMillis);
    }

    /** Returns true on a second break inside the confirmation window. */
    public boolean confirmBreak(UUID playerId, UUID lockId, long nowMillis, long confirmationWindowMillis) {
        AccessKey key = new AccessKey(playerId, lockId);
        Long previous = breakConfirmations.put(key, nowMillis + confirmationWindowMillis);
        if (previous != null && previous > nowMillis) {
            breakConfirmations.remove(key);
            return true;
        }
        return false;
    }

    public boolean toggleBypass(UUID playerId) {
        if (bypass.remove(playerId)) {
            return false;
        }
        bypass.add(playerId);
        return true;
    }

    public boolean hasBypass(UUID playerId) {
        return bypass.contains(playerId);
    }

    public void clearLock(UUID lockId) {
        unlocks.keySet().removeIf(key -> key.lockId.equals(lockId));
        breakConfirmations.keySet().removeIf(key -> key.lockId.equals(lockId));
    }

    public void disableBypass(UUID playerId) {
        bypass.remove(playerId);
    }

    public void clearPlayer(UUID playerId) {
        unlocks.keySet().removeIf(key -> key.playerId.equals(playerId));
        breakConfirmations.keySet().removeIf(key -> key.playerId.equals(playerId));
        bypass.remove(playerId);
    }

    public void clearAll() {
        unlocks.clear();
        breakConfirmations.clear();
        bypass.clear();
    }

    private record AccessKey(UUID playerId, UUID lockId) {
    }
}
