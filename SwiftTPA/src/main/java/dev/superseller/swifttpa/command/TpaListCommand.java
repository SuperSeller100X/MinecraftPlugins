package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /tpalist — lists your pending incoming requests plus your outgoing one. */
public final class TpaListCommand extends AbstractCommand {

    public TpaListCommand(SwiftTPAPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "swifttpa.list")) {
            return true;
        }
        plugin.service().sendList(player);
        return true;
    }
}
