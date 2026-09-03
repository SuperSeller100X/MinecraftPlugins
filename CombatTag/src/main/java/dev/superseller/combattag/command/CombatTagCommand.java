package dev.superseller.combattag.command;

import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.combat.CombatTagEntry;
import dev.superseller.combattag.config.Messages;
import dev.superseller.combattag.config.PluginConfig;
import dev.superseller.combattag.gui.CombatGui;
import dev.superseller.combattag.util.TimeUtil;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Player command {@code /combattag} (aliases {@code /ct}, {@code /combat}).
 *
 * <p>Sub-commands: {@code status|s}, {@code gui|g}, {@code time|t}, {@code check|c <player>},
 * {@code info|i}, {@code help|h}.</p>
 */
public final class CombatTagCommand implements CommandExecutor {

    private final PluginConfig config;
    private final Messages messages;
    private final CombatManager combat;
    private final CombatGui gui;

    public CombatTagCommand(PluginConfig config, Messages messages, CombatManager combat, CombatGui gui) {
        this.config = config;
        this.messages = messages;
        this.combat = combat;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("combattag.use")) {
            messages.send(sender, "error.no-permission");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "status", "s", "time", "t" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "error.players-only");
                    return true;
                }
                sendStatus(player);
            }
            case "gui", "g", "menu", "m" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "error.players-only");
                    return true;
                }
                if (!config.isGuiEnabled()) {
                    messages.send(sender, "error.gui-disabled");
                    return true;
                }
                if (!player.hasPermission("combattag.gui")) {
                    messages.send(sender, "error.no-permission");
                    return true;
                }
                gui.openStatus(player);
            }
            case "check", "c" -> {
                if (!sender.hasPermission("combattag.check")) {
                    messages.send(sender, "error.no-permission");
                    return true;
                }
                if (args.length < 2) {
                    messages.send(sender, "error.usage", "usage", "/" + label + " check <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(sender, "error.player-not-found", "player", args[1]);
                    return true;
                }
                boolean tagged = combat.isTagged(target.getUniqueId());
                messages.send(sender, tagged ? "check.tagged" : "check.safe",
                        "player", target.getName(),
                        "seconds", String.valueOf(combat.remainingSeconds(target.getUniqueId())));
            }
            case "info", "i" -> messages.send(sender, "info.summary",
                    "duration", String.valueOf(config.getTagSeconds()),
                    "projectile", String.valueOf(config.getProjectileTagSeconds()),
                    "shops", String.valueOf(config.isBlockShops()),
                    "teleports", String.valueOf(config.isBlockTeleports()),
                    "easymending", String.valueOf(config.isBlockEasyMending()),
                    "active", String.valueOf(combat.getActiveCount()));
            default -> messages.send(sender, "help.player", "label", label);
        }
        return true;
    }

    private void sendStatus(Player player) {
        Optional<CombatTagEntry> entry = combat.get(player.getUniqueId());
        if (!combat.isTagged(player.getUniqueId()) || entry.isEmpty()) {
            messages.send(player, "status.safe");
            return;
        }
        long now = System.currentTimeMillis();
        CombatTagEntry e = entry.get();
        String opponent = e.opponent() == null ? "-" : nameOf(e);
        player.sendMessage(messages.get("status.tagged", Map.of(
                "seconds", String.valueOf(e.remainingSeconds(now)),
                "time", TimeUtil.format(e.remainingMillis(now)),
                "opponent", opponent)));
    }

    private static String nameOf(CombatTagEntry entry) {
        Player p = Bukkit.getPlayer(entry.opponent());
        if (p != null) {
            return p.getName();
        }
        String n = Bukkit.getOfflinePlayer(entry.opponent()).getName();
        return n != null ? n : "unknown";
    }
}
