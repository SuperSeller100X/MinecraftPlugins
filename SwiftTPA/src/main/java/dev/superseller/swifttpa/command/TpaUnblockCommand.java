package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /tpaunblock {@code <player>} — removes a player from your teleport block list. */
public final class TpaUnblockCommand extends AbstractCommand {

    public TpaUnblockCommand(SwiftTPAPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "swifttpa.block")) {
            return true;
        }
        if (args.length == 0 || args[0].isBlank()) {
            plugin.messages().send(player, "usage-unblock");
            return true;
        }
        plugin.service().unblock(player, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return filter(plugin.storage().data(player.getUniqueId()).blockedNames(), args[0]);
    }
}
