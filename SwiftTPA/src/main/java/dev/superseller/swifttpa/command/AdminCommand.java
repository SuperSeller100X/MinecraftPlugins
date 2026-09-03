package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /swifttpaadmin (aliases /stpaadmin, /tpaadmin) — the full staff toolbox:
 * config reload, force teleports, clearing requests, the spy feed and
 * statistics. Every sub-command has at least one short alias.
 */
public final class AdminCommand extends AbstractCommand {

    public AdminCommand(SwiftTPAPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requirePermission(sender, "swifttpa.admin")) {
            return true;
        }
        if (args.length == 0) {
            plugin.messages().sendHelp(sender, "admin");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload", "rl" -> {
                if (!requirePermission(sender, "swifttpa.admin.reload")) {
                    return true;
                }
                plugin.reloadAll();
                plugin.messages().send(sender, "reloaded");
            }
            case "forcetp", "ftp" -> {
                if (args.length < 3) {
                    plugin.messages().send(sender, "admin.usage-forcetp");
                    return true;
                }
                Player mover = findPlayer(args[1]);
                if (mover == null) {
                    plugin.messages().send(sender, "player-not-found", Map.of("input", args[1]));
                    return true;
                }
                Player anchor = findPlayer(args[2]);
                if (anchor == null) {
                    plugin.messages().send(sender, "player-not-found", Map.of("input", args[2]));
                    return true;
                }
                plugin.service().forceTeleport(sender, mover, anchor);
            }
            case "forcetphere", "ftph" -> {
                Player anchor = requirePlayer(sender);
                if (anchor == null) {
                    return true;
                }
                if (args.length < 2) {
                    plugin.messages().send(sender, "admin.usage-forcetphere");
                    return true;
                }
                Player mover = findPlayer(args[1]);
                if (mover == null) {
                    plugin.messages().send(sender, "player-not-found", Map.of("input", args[1]));
                    return true;
                }
                plugin.service().forceTeleport(sender, mover, anchor);
            }
            case "clear", "c" -> {
                if (args.length < 2 || args[1].equalsIgnoreCase("all")) {
                    plugin.service().clearAll(sender);
                    return true;
                }
                Player target = findPlayer(args[1]);
                if (target == null) {
                    plugin.messages().send(sender, "player-not-found", Map.of("input", args[1]));
                    return true;
                }
                plugin.service().clearFor(sender, target.getUniqueId(), target.getName());
            }
            case "spy", "s" -> {
                Player admin = requirePlayer(sender);
                if (admin == null) {
                    return true;
                }
                if (!plugin.tpaConfig().spyEnabled()) {
                    plugin.messages().send(admin, "error.disabled");
                    return true;
                }
                plugin.service().toggleSpy(admin);
            }
            case "stats", "st" -> {
                if (args.length < 2) {
                    Player admin = requirePlayer(sender);
                    if (admin == null) {
                        return true;
                    }
                    plugin.service().sendStats(admin,
                            plugin.storage().data(admin.getUniqueId()), admin.getName(), true);
                    return true;
                }
                Player target = findPlayer(args[1]);
                if (target == null) {
                    plugin.messages().send(sender, "player-not-found", Map.of("input", args[1]));
                    return true;
                }
                plugin.service().sendStats(sender,
                        plugin.storage().data(target.getUniqueId()), target.getName(), false);
            }
            case "info", "i" -> plugin.service().sendInfo(sender);
            case "help", "h", "?" -> plugin.messages().sendHelp(sender, "admin");
            default -> plugin.messages().send(sender, "unknown-subcommand", Map.of("input", args[0]));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("swifttpa.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of(
                    "forcetp", "forcetphere", "clear", "spy", "stats", "info", "help"));
            if (sender.hasPermission("swifttpa.admin.reload")) {
                options.add("reload");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "forcetp", "ftp", "forcetphere", "ftph", "stats", "st" -> {
                    return filter(onlineNames(null), args[1]);
                }
                case "clear", "c" -> {
                    List<String> options = new ArrayList<>(onlineNames(null));
                    options.add("all");
                    return filter(options, args[1]);
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("forcetp") || args[0].equalsIgnoreCase("ftp"))) {
            return filter(onlineNames(null), args[2]);
        }
        return List.of();
    }
}
