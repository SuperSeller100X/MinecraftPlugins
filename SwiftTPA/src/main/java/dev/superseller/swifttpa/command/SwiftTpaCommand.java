package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;
import dev.superseller.swifttpa.scheduler.PlatformScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /swifttpa (alias /stpa) — help, the requests GUI, personal info/statistics
 * and the plugin version.
 */
public final class SwiftTpaCommand extends AbstractCommand {

    public SwiftTpaCommand(SwiftTPAPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help", "h", "?" -> plugin.messages().sendHelp(sender, "player");
            case "gui", "g" -> {
                Player player = requirePlayer(sender);
                if (player == null || !requirePermission(player, "swifttpa.gui")) {
                    return true;
                }
                // openInventory must run on the player's owning region thread.
                PlatformScheduler.runForPlayer(player, () -> plugin.gui().open(player));
            }
            case "info", "i", "stats", "st" -> {
                Player player = requirePlayer(sender);
                if (player == null || !requirePermission(player, "swifttpa.stats")) {
                    return true;
                }
                plugin.service().sendStats(player,
                        plugin.storage().data(player.getUniqueId()), player.getName(), true);
                plugin.service().sendInfo(player);
            }
            case "version", "v" -> plugin.service().sendInfo(sender);
            default -> plugin.messages().send(sender, "unknown-subcommand", Map.of("input", args[0]));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("help", "info", "version"));
        if (sender.hasPermission("swifttpa.gui")) {
            options.add("gui");
        }
        return filter(options, args[0]);
    }
}
