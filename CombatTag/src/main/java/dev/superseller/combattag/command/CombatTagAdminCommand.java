package dev.superseller.combattag.command;

import dev.superseller.combattag.CombatTagPlugin;
import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.config.Messages;
import dev.superseller.combattag.config.PluginConfig;
import dev.superseller.combattag.gui.CombatGui;
import dev.superseller.combattag.util.TimeUtil;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Admin command {@code /combattagadmin} (aliases {@code /cta}, {@code /ctadmin}).
 *
 * <p>Sub-commands: {@code tag|t}, {@code untag|u}, {@code clear|c}, {@code list|l},
 * {@code exempt|e}, {@code duration|d}, {@code stats|st}, {@code gui|g}, {@code reload|rl}.</p>
 */
public final class CombatTagAdminCommand implements CommandExecutor {

    private final CombatTagPlugin plugin;
    private final PluginConfig config;
    private final Messages messages;
    private final CombatManager combat;
    private final CombatGui gui;

    public CombatTagAdminCommand(CombatTagPlugin plugin, PluginConfig config, Messages messages,
                                 CombatManager combat, CombatGui gui) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.combat = combat;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("combattag.admin")) {
            messages.send(sender, "error.no-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "help.admin", "label", label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "tag", "t" -> tag(sender, label, args);
            case "untag", "u" -> untag(sender, label, args);
            case "clear", "c" -> {
                requirePerm(sender, "combattag.admin.clear", () -> {
                    int cleared = combat.untagAll();
                    messages.send(sender, "admin.cleared", "count", String.valueOf(cleared));
                });
            }
            case "list", "l" -> list(sender);
            case "exempt", "e" -> exempt(sender, label, args);
            case "duration", "d" -> duration(sender, label, args);
            case "stats", "st" -> sender.sendMessage(messages.get("admin.stats", Map.of(
                    "active", String.valueOf(combat.getActiveCount()),
                    "tags", String.valueOf(combat.getTotalTags()),
                    "blocked", String.valueOf(combat.getTotalBlocked()),
                    "logs", String.valueOf(combat.getTotalCombatLogs()))));
            case "gui", "g" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "error.players-only");
                    return true;
                }
                gui.openAdmin(player);
            }
            case "reload", "rl" -> requirePerm(sender, "combattag.admin.reload", () -> {
                plugin.reloadEverything();
                messages.send(sender, "admin.reloaded");
            });
            default -> messages.send(sender, "help.admin", "label", label);
        }
        return true;
    }

    private void tag(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("combattag.admin.tag")) {
            messages.send(sender, "error.no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "error.usage", "usage", "/" + label + " tag <player> [seconds]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "error.player-not-found", "player", args[1]);
            return;
        }
        int seconds = config.getTagSeconds();
        if (args.length >= 3) {
            int parsed = TimeUtil.parseSeconds(args[2]);
            if (parsed <= 0) {
                messages.send(sender, "error.invalid-duration", "input", args[2]);
                return;
            }
            seconds = parsed;
        }
        combat.tagFor(target, sender instanceof Player p ? p.getUniqueId() : null, seconds);
        messages.send(sender, "admin.tagged", "player", target.getName(), "seconds", String.valueOf(seconds));
    }

    private void untag(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("combattag.admin.untag")) {
            messages.send(sender, "error.no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "error.usage", "usage", "/" + label + " untag <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "error.player-not-found", "player", args[1]);
            return;
        }
        boolean removed = combat.untag(target.getUniqueId());
        messages.send(sender, removed ? "admin.untagged" : "admin.not-tagged", "player", target.getName());
    }

    private void list(CommandSender sender) {
        if (!sender.hasPermission("combattag.admin.list")) {
            messages.send(sender, "error.no-permission");
            return;
        }
        if (combat.getActiveCount() == 0) {
            messages.send(sender, "admin.list-empty");
            return;
        }
        messages.send(sender, "admin.list-header", "count", String.valueOf(combat.getActiveCount()));
        long now = System.currentTimeMillis();
        combat.snapshot().forEach((id, entry) -> {
            String name = nameOf(id);
            sender.sendMessage(messages.get("admin.list-entry", Map.of(
                    "player", name,
                    "seconds", String.valueOf(entry.remainingSeconds(now)),
                    "opponent", entry.opponent() == null ? "-" : nameOf(entry.opponent()))));
        });
    }

    private void exempt(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("combattag.admin.exempt")) {
            messages.send(sender, "error.no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "error.usage", "usage", "/" + label + " exempt <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "error.player-not-found", "player", args[1]);
            return;
        }
        boolean nowExempt = combat.toggleExempt(target.getUniqueId());
        if (nowExempt) {
            combat.untag(target.getUniqueId());
        }
        messages.send(sender, nowExempt ? "admin.exempt-on" : "admin.exempt-off", "player", target.getName());
    }

    private void duration(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("combattag.admin.duration")) {
            messages.send(sender, "error.no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "admin.duration-hint", "seconds", String.valueOf(config.getTagSeconds()));
            return;
        }
        int parsed = TimeUtil.parseSeconds(args[1]);
        if (parsed <= 0) {
            messages.send(sender, "error.invalid-duration", "input", args[1]);
            return;
        }
        config.setTagSeconds(parsed);
        messages.send(sender, "admin.duration-set", "seconds", String.valueOf(parsed));
    }

    private void requirePerm(CommandSender sender, String permission, Runnable action) {
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "error.no-permission");
            return;
        }
        action.run();
    }

    private static String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            return p.getName();
        }
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString().substring(0, 8);
    }
}
