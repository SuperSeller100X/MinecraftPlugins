package dev.superseller.playerheads.head;

import com.destroystokyo.paper.profile.PlayerProfile;

import dev.superseller.playerheads.PlayerHeadsPlugin;
import dev.superseller.playerheads.config.Messages;
import dev.superseller.playerheads.config.PlayerHeadsConfig;
import dev.superseller.playerheads.scheduler.PlatformScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Resolves a Minecraft account name to a skin profile asynchronously and
 * hands the finished player head to the requesting player on their region
 * thread (Folia-safe).
 */
public final class HeadService {

    /** Vanilla maximum stack size of a player head item. */
    private static final int MAX_STACK_SIZE = 64;

    private final PlayerHeadsPlugin plugin;
    private final PlayerHeadsConfig settings;
    private final Messages messages;

    public HeadService(PlayerHeadsPlugin plugin, PlayerHeadsConfig settings, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
    }

    /**
     * Gives the requesting player {@code amount} heads showing the named
     * account's skin. The profile lookup runs async; the item hand-out runs
     * on the player's entity thread.
     *
     * @param player     receiver of the heads (must be online)
     * @param targetName any valid Minecraft account name
     * @param amount     number of heads (1..65536, already validated)
     */
    public void giveHead(Player player, String targetName, int amount) {
        PlatformScheduler.runAsync(() -> {
            PlayerProfile profile;
            try {
                profile = Bukkit.createProfile(targetName);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not create profile for '" + targetName + "': " + e.getMessage());
                PlatformScheduler.runEntitySync(player, () ->
                        messages.send(player, "player-not-found", Map.of("player", targetName)));
                return;
            }

            boolean completed = false;
            try {
                // Blocking Mojang/session-server lookup (paper caches profiles of
                // online and recently seen players). We are on an async thread.
                completed = profile.complete();
            } catch (Exception e) {
                plugin.getLogger().warning("Profile lookup for '" + targetName + "' failed: " + e.getMessage());
            }

            if (!completed || profile.getId() == null) {
                PlatformScheduler.runEntitySync(player, () ->
                        messages.send(player, "player-not-found", Map.of("player", targetName)));
                return;
            }

            PlatformScheduler.runEntitySync(player, () -> {
                if (!player.isOnline()) {
                    return; // receiver logged off during the lookup
                }
                give(player, profile, amount);
            });
        });
    }

    private void give(Player player, PlayerProfile profile, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            int size = Math.min(remaining, MAX_STACK_SIZE);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, size);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setPlayerProfile(profile.clone());
                head.setItemMeta(meta);
            }
            stacks.add(head);
            remaining -= size;
        }

        Map<Integer, ItemStack> leftovers = new HashMap<>();
        for (ItemStack stack : stacks) {
            leftovers.putAll(player.getInventory().addItem(stack));
        }

        String displayName = profile.getName() != null ? profile.getName() : "?";
        messages.send(player, "head-given", Map.of(
                "player", displayName,
                "amount", String.valueOf(amount)));

        if (!leftovers.isEmpty()) {
            int droppedCount = 0;
            for (ItemStack leftover : leftovers.values()) {
                droppedCount += leftover.getAmount();
            }
            if (settings.dropWhenFull()) {
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItem(player.getLocation(), leftover);
                }
                messages.send(player, "inventory-full-dropped", Map.of("count", String.valueOf(droppedCount)));
            } else {
                messages.send(player, "inventory-full", Map.of("count", String.valueOf(droppedCount)));
            }
        }
    }
}
