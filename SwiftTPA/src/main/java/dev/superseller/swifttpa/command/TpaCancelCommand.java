package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /tpacancel — cancels the teleport request you sent. */
public final class TpaCancelCommand extends AbstractCommand {

    public TpaCancelCommand(SwiftTPAPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "swifttpa.cancel")) {
            return true;
        }
        plugin.service().cancel(player);
        return true;
    }
}
