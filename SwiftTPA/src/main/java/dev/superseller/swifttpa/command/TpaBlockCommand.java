package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /tpablock {@code <player>} — blocks a player from sending you teleport
 * requests (persisted across restarts). /tpablock list shows your block list.
 */
public final class TpaBlockCommand extends AbstractCommand {

    public TpaBlockCommand(SwiftTPAPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "swifttpa.block")) {
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            plugin.service().sendBlockList(player);
            return true;
        }
        if (args.length == 0 || args[0].isBlank()) {
            plugin.messages().send(player, "usage-block");
            return true;
        }
        Player target = findPlayer(args[0]);
        if (target == null) {
            plugin.messages().send(player, "player-not-found", Map.of("input", args[0]));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "error.self");
            return true;
        }
        plugin.service().block(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        options.add("list");
        if (sender instanceof Player viewer) {
            for (String name : onlineNames(viewer)) {
                Player target = findPlayer(name);
                if (target != null
                        && !plugin.storage().data(viewer.getUniqueId()).isBlocked(target.getUniqueId())) {
                    options.add(name);
                }
            }
        }
        return filter(options, args[0]);
    }
}
