package dev.superseller.veltryx.shards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.superseller.veltryx.storage.DataStore;

/**
 * The shard economy: balances persisted through the DataStore, player to
 * player payments and the leaderboard. Buying shards with money is
 * intentionally NOT part of this system - shards are earned by playing
 * (1 shard per minute), paid between players or given by staff.
 */
public final class ShardService {

    /** Passive income per minute of playtime (the Veltryx default). */
    public static final long SHARDS_PER_MINUTE = 1L;

    /** Outcome of a shard payment: whether it worked and the message key. */
    public record Result(boolean ok, String key) { }

    private final DataStore data;

    public ShardService(DataStore data) {
        this.data = data;
    }

    public long balance(UUID id) {
        return data.shards(id);
    }

    public void give(UUID id, long amount) {
        if (amount > 0) {
            data.setShards(id, data.shards(id) + amount);
        }
    }

    public boolean take(UUID id, long amount) {
        if (amount <= 0 || data.shards(id) < amount) {
            return false;
        }
        data.setShards(id, data.shards(id) - amount);
        return true;
    }

    /** Pays shards to another player. Neither player needs to be online. */
    public Result pay(UUID from, UUID to, long amount) {
        if (amount <= 0) {
            return new Result(false, "shards.pay-bad-amount");
        }
        if (from.equals(to)) {
            return new Result(false, "shards.pay-self");
        }
        if (!take(from, amount)) {
            return new Result(false, "shards.not-enough");
        }
        give(to, amount);
        return new Result(true, null);
    }

    /** Highest shard balances, descending. */
    public List<Map.Entry<UUID, Long>> top(int limit) {
        List<Map.Entry<UUID, Long>> rows = new ArrayList<>(data.shardBalances().entrySet());
        rows.sort(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue).reversed());
        return rows.size() > limit ? rows.subList(0, limit) : rows;
    }
}
