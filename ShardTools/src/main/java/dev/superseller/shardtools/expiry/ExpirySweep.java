package dev.superseller.shardtools.expiry;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.superseller.shardtools.ShardToolsPlugin;
import dev.superseller.shardtools.item.ShardCatalog;
import dev.superseller.shardtools.util.TimeWords;

/**
 * Real-time self-destruct engine. The repeating sweep runs on the global
 * region and defers to each player's entity scheduler (Folia-safe), so
 * expired items vanish even after days of server downtime.
 */
public final class ExpirySweep {

    private final ShardToolsPlugin plugin;

    public ExpirySweep(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Global-region tick: schedule per-player scans. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            dev.superseller.shardtools.scheduler.PlatformScheduler.runEntity(player, () -> scan(player));
        }
        if (plugin.accounts().isDirty()) {
            plugin.accounts().saveAsync();
        }
    }

    /** Scans one player's inventory (runs on the player's region thread). */
    public void scan(Player player) {
        if (!player.isOnline()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            String id = plugin.items().itemId(stack);
            if (id == null) {
                continue;
            }
            ShardCatalog.Entry entry = plugin.catalog().byId(id);
            if (entry == null) {
                continue;
            }
            if (plugin.items().expired(stack, now)) {
                player.getInventory().setItem(i, null);
                destroy(player, entry);
                changed = true;
            } else {
                boolean expired = plugin.items().tick(stack, entry, now, player);
                if (expired) {
                    player.getInventory().setItem(i, null);
                    destroy(player, entry);
                    changed = true;
                } else if (stack.getItemMeta() != null
                        && plugin.items().itemId(stack) != null
                        && !stack.equals(contents[i])) {
                    changed = true;
                }
            }
        }
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (cursor != null && plugin.items().expired(cursor, now)) {
            String id = plugin.items().itemId(cursor);
            ShardCatalog.Entry entry = plugin.catalog().byId(id);
            player.getOpenInventory().setCursor(null);
            if (entry != null) {
                destroy(player, entry);
            }
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    private void destroy(Player player, ShardCatalog.Entry entry) {
        plugin.messages().send(player, "expire.destroyed", "%item%", entry.displayName());
        Sound sound = plugin.soundResolver().resolve(plugin.settings().soundExpire());
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 0.8f);
        }
    }

    /** Removes expired shard items from any opened inventory (chests etc.). */
    public int scanInventory(org.bukkit.inventory.Inventory inventory, Player viewer) {
        long now = System.currentTimeMillis();
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null) {
                continue;
            }
            String id = plugin.items().itemId(stack);
            if (id == null || !plugin.items().expired(stack, now)) {
                continue;
            }
            ShardCatalog.Entry entry = plugin.catalog().byId(id);
            inventory.setItem(i, null);
            removed++;
            if (entry != null) {
                plugin.messages().send(viewer, "expire.destroyed", "%item%", entry.displayName());
            }
        }
        return removed;
    }

    /** Vanilla-style ore XP for area-mined blocks. */
    public void dropOreXp(Block block, Material material) {
        if (!plugin.settings().oreXp()) {
            return;
        }
        int[] range = plugin.settings().xpRange(material);
        if (range == null) {
            return;
        }
        int xp = range[0];
        if (range[1] > range[0]) {
            xp += ThreadLocalRandom.current().nextInt(range[1] - range[0] + 1);
        }
        if (xp <= 0) {
            return;
        }
        World world = block.getWorld();
        Location location = new Location(world, block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        final int experience = xp;
        world.spawn(location, ExperienceOrb.class, orb -> orb.setExperience(experience));
    }

    /** Unused placeholder kept for API symmetry. */
    public static String describeRemaining(long remainingMs) {
        return TimeWords.format(remainingMs);
    }
}
