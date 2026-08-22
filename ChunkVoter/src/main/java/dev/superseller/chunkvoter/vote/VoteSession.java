package dev.superseller.chunkvoter.vote;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single, in-memory regeneration vote for one chunk. Votes are placed by
 * player {@link UUID} and may be changed at any time until the vote closes.
 */
public final class VoteSession {

    private final UUID id;
    private final ChunkKey key;
    private final org.bukkit.World world;
    private final int chunkX;
    private final int chunkZ;
    private final UUID initiator;
    private final boolean inRegion;
    private final Set<UUID> regionOwners;
    private final long startAt;
    private final long endAt;

    private final Map<UUID, Boolean> votes = new ConcurrentHashMap<>(); // true = yes
    private boolean finished;

    public VoteSession(
            org.bukkit.World world,
            int chunkX,
            int chunkZ,
            UUID initiator,
            boolean inRegion,
            Set<UUID> regionOwners,
            long startAt,
            long durationMillis) {
        this.id = UUID.randomUUID();
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.key = ChunkKey.of(world, chunkX, chunkZ);
        this.initiator = initiator;
        this.inRegion = inRegion;
        this.regionOwners = regionOwners == null ? Set.of() : Collections.unmodifiableSet(regionOwners);
        this.startAt = startAt;
        this.endAt = startAt + durationMillis;
    }

    public UUID id() {
        return id;
    }

    public ChunkKey key() {
        return key;
    }

    public org.bukkit.World world() {
        return world;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public UUID initiator() {
        return initiator;
    }

    public boolean inRegion() {
        return inRegion;
    }

    public Set<UUID> regionOwners() {
        return regionOwners;
    }

    public long startAt() {
        return startAt;
    }

    public long endAt() {
        return endAt;
    }

    public long remainingMillis(long now) {
        return Math.max(0L, endAt - now);
    }

    public boolean isFinished() {
        return finished;
    }

    public void finish() {
        if (!finished) {
            finished = true;
        }
    }

    public boolean hasVoted(UUID player) {
        return votes.containsKey(player);
    }

    /** Places (or updates) the player's vote. Returns true if it was a change. */
    public boolean cast(UUID player, boolean yes) {
        Boolean before = votes.put(player, yes);
        return before == null || before != yes;
    }

    public Map<UUID, Boolean> votes() {
        return Collections.unmodifiableMap(votes);
    }

    public int yes() {
        int c = 0;
        for (Boolean b : votes.values()) {
            if (Boolean.TRUE.equals(b)) {
                c++;
            }
        }
        return c;
    }

    public int no() {
        return votes.size() - yes();
    }

    public int total() {
        return votes.size();
    }
}
