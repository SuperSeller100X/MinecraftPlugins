package dev.superseller.chestlock.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable lock metadata persisted in a container TileState PDC. */
public record LockData(
        UUID lockId,
        UUID ownerId,
        String ownerName,
        byte[] salt,
        byte[] hash,
        int iterations,
        long unlockDurationMillis,
        UUID keyToken,
        long createdAtMillis,
        List<BlockRef> accessBlocks
) {
    public LockData {
        Objects.requireNonNull(lockId, "lockId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(salt, "salt");
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(keyToken, "keyToken");
        Objects.requireNonNull(accessBlocks, "accessBlocks");
        salt = salt.clone();
        hash = hash.clone();
        accessBlocks = List.copyOf(accessBlocks);
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    @Override
    public byte[] hash() {
        return hash.clone();
    }

    /** Detects a passcode rotation while an unlock dialog or hash operation is in flight. */
    public boolean sameSecurityVersion(LockData other) {
        return other != null
                && lockId.equals(other.lockId)
                && iterations == other.iterations
                && Arrays.equals(salt, other.salt)
                && Arrays.equals(hash, other.hash);
    }

    public boolean samePersistedData(LockData other) {
        return sameSecurityVersion(other)
                && ownerId.equals(other.ownerId)
                && ownerName.equals(other.ownerName)
                && unlockDurationMillis == other.unlockDurationMillis
                && keyToken.equals(other.keyToken)
                && createdAtMillis == other.createdAtMillis
                && accessBlocks.equals(other.accessBlocks);
    }

    public LockData withPassword(byte[] newSalt, byte[] newHash, int newIterations) {
        return new LockData(lockId, ownerId, ownerName, newSalt, newHash, newIterations,
                unlockDurationMillis, keyToken, createdAtMillis, accessBlocks);
    }

    public LockData withKeyToken(UUID newKeyToken) {
        return new LockData(lockId, ownerId, ownerName, salt, hash, iterations,
                unlockDurationMillis, newKeyToken, createdAtMillis, accessBlocks);
    }

    public LockData withAccessBlocks(List<BlockRef> newAccessBlocks) {
        return new LockData(lockId, ownerId, ownerName, salt, hash, iterations,
                unlockDurationMillis, keyToken, createdAtMillis, newAccessBlocks);
    }
}
