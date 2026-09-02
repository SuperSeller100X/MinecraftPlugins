package dev.superseller.combattag.combat;

import dev.superseller.combattag.config.PluginConfig;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Thread-safe registry of combat tags. All state is in memory; nothing is persisted between
 * restarts, which is what a combat timer should do.
 */
public final class CombatManager {

    private final PluginConfig config;
    private final BypassService bypass;
    private final Map<UUID, CombatTagEntry> tags = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> exempt = new ConcurrentHashMap<>();

    private final AtomicLong totalTags = new AtomicLong();
    private final AtomicLong totalBlocked = new AtomicLong();
    private final AtomicLong totalCombatLogs = new AtomicLong();

    private BiConsumer<Player, CombatTagEntry> onTagStart = (p, e) -> { };
    private BiConsumer<UUID, CombatTagEntry> onTagEnd = (p, e) -> { };

    public CombatManager(PluginConfig config, BypassService bypass) {
        this.config = config;
        this.bypass = bypass;
    }

    public void setCallbacks(BiConsumer<Player, CombatTagEntry> start, BiConsumer<UUID, CombatTagEntry> end) {
        this.onTagStart = start != null ? start : (p, e) -> { };
        this.onTagEnd = end != null ? end : (p, e) -> { };
    }

    /**
     * Tags a player for the configured duration.
     *
     * @param player the player to tag
     * @param opponent the opponent's UUID, may be {@code null}
     * @param cause why the tag is applied
     * @return the resulting tag entry, or empty when the player is exempt / the world is disabled
     */
    public Optional<CombatTagEntry> tag(Player player, UUID opponent, CombatCause cause) {
        if (player == null || !player.isOnline()) {
            return Optional.empty();
        }
        if (config.isWorldDisabled(player.getWorld().getName())) {
            return Optional.empty();
        }
        if (isExempt(player)) {
            return Optional.empty();
        }
        int seconds = cause == CombatCause.PROJECTILE
                ? config.getProjectileTagSeconds()
                : config.getTagSeconds();
        return Optional.of(tagFor(player, opponent, seconds));
    }

    /**
     * Tags a player for an explicit number of seconds, bypassing the exemption checks
     * (used by admin commands and the API).
     */
    public CombatTagEntry tagFor(Player player, UUID opponent, int seconds) {
        long now = System.currentTimeMillis();
        long duration = Math.max(1L, seconds) * 1000L;
        UUID id = player.getUniqueId();
        CombatTagEntry existing = tags.get(id);
        boolean fresh = existing == null || existing.isExpired(now);

        if (!fresh && !config.isRefreshOnHit() && existing.remainingMillis(now) >= duration) {
            return existing;
        }

        CombatTagEntry entry = new CombatTagEntry(id, opponent, now + duration, now, duration);
        tags.put(id, entry);
        if (fresh) {
            totalTags.incrementAndGet();
            onTagStart.accept(player, entry);
        }
        return entry;
    }

    /** Removes a player's tag, firing the end callback if one existed. */
    public boolean untag(UUID id) {
        CombatTagEntry removed = tags.remove(id);
        if (removed != null) {
            onTagEnd.accept(id, removed);
            return true;
        }
        return false;
    }

    /** Clears every active tag (used on reload / shutdown). */
    public int untagAll() {
        int size = tags.size();
        for (UUID id : java.util.Set.copyOf(tags.keySet())) {
            untag(id);
        }
        return size;
    }

    /** @return true when the given player is currently combat tagged. */
    public boolean isTagged(UUID id) {
        if (id == null) {
            return false;
        }
        CombatTagEntry entry = tags.get(id);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired(System.currentTimeMillis())) {
            untag(id);
            return false;
        }
        return true;
    }

    public boolean isTagged(Player player) {
        return player != null && isTagged(player.getUniqueId());
    }

    public Optional<CombatTagEntry> get(UUID id) {
        return Optional.ofNullable(tags.get(id));
    }

    public int remainingSeconds(UUID id) {
        CombatTagEntry entry = tags.get(id);
        return entry == null ? 0 : entry.remainingSeconds(System.currentTimeMillis());
    }

    public Map<UUID, CombatTagEntry> snapshot() {
        return Collections.unmodifiableMap(Map.copyOf(tags));
    }

    /** Expires timed-out tags; called by the display ticker. */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, CombatTagEntry> e : Map.copyOf(tags).entrySet()) {
            if (e.getValue().isExpired(now)) {
                untag(e.getKey());
            }
        }
    }

    /** Toggles a manual exemption for a player (admin command). */
    public boolean toggleExempt(UUID id) {
        if (exempt.remove(id) != null) {
            return false;
        }
        exempt.put(id, Boolean.TRUE);
        return true;
    }

    public boolean isExempt(Player player) {
        if (player == null) {
            return false;
        }
        // Runtime exemptions (/cta exempt) always apply; permission/OP bypasses only
        // count while the bypass system is switched on.
        return exempt.containsKey(player.getUniqueId()) || bypass.canBypassTagging(player);
    }

    public boolean isExemptId(UUID id) {
        if (exempt.containsKey(id)) {
            return true;
        }
        Player p = Bukkit.getPlayer(id);
        return p != null && bypass.canBypassTagging(p);
    }

    public void countBlocked() {
        totalBlocked.incrementAndGet();
    }

    public void countCombatLog() {
        totalCombatLogs.incrementAndGet();
    }

    public long getTotalTags() { return totalTags.get(); }
    public long getTotalBlocked() { return totalBlocked.get(); }
    public long getTotalCombatLogs() { return totalCombatLogs.get(); }
    public int getActiveCount() { return tags.size(); }
}
