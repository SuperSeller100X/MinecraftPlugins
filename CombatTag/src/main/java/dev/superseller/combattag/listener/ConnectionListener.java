package dev.superseller.combattag.listener;

import dev.superseller.combattag.combat.BypassService;
import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.combat.CombatTagEntry;
import dev.superseller.combattag.config.Messages;
import dev.superseller.combattag.config.PluginConfig;
import dev.superseller.combattag.display.DisplayManager;
import dev.superseller.combattag.scheduler.PlatformScheduler;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Handles combat logging punishment and cleans up displays on quit / join. */
public final class ConnectionListener implements Listener {

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final Messages messages;
    private final CombatManager combat;
    private final DisplayManager display;
    private final BypassService bypass;

    public ConnectionListener(JavaPlugin plugin, PluginConfig config, Messages messages,
                              CombatManager combat, DisplayManager display, BypassService bypass) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.combat = combat;
        this.display = display;
        this.bypass = bypass;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // A fresh session always starts clean.
        combat.untag(event.getPlayer().getUniqueId());
        display.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        handleDisconnect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        handleDisconnect(event.getPlayer());
    }

    private void handleDisconnect(Player player) {
        UUID id = player.getUniqueId();
        Optional<CombatTagEntry> entry = combat.get(id);
        boolean tagged = combat.isTagged(id);
        display.clear(id);
        combat.untag(id);

        if (!tagged || !config.isPunishCombatLog()) {
            return;
        }
        if (bypass.canBypassCombatLog(player)) {
            return;
        }
        combat.countCombatLog();

        String opponentName = entry.map(CombatTagEntry::opponent)
                .map(o -> {
                    Player op = Bukkit.getPlayer(o);
                    return op != null ? op.getName() : "unknown";
                })
                .orElse("unknown");

        if (config.isCombatLogKill()) {
            killLogger(player);
        }
        if (config.isCombatLogBroadcast()) {
            Component msg = messages.get("combat-log.broadcast",
                    Map.of("player", player.getName(), "opponent", opponentName));
            Bukkit.broadcast(msg);
        }
        if (config.isCombatLogLogToConsole()) {
            plugin.getLogger().warning(player.getName() + " combat logged while fighting " + opponentName + ".");
        }
        for (String raw : config.getCombatLogCommands()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String command = raw.replace("{player}", player.getName())
                    .replace("{opponent}", opponentName)
                    .replace("%player%", player.getName());
            PlatformScheduler.runGlobal(() -> {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Combat-log command failed: " + command + " (" + t.getMessage() + ")");
                }
            });
        }
    }

    private void killLogger(Player player) {
        try {
            if (config.isCombatLogDropInventory()) {
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() != org.bukkit.Material.AIR) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
                player.getInventory().clear();
            }
            player.setHealth(0.0D);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not punish combat logger " + player.getName() + ": " + t.getMessage());
        }
    }
}
