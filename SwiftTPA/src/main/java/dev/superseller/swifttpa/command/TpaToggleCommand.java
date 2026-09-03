package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /tpatoggle — switches receiving teleport requests on or off (persisted). */
public final class TpaToggleCommand extends AbstractCommand {

    public TpaToggleCommand(SwiftTPAPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "swifttpa.toggle")) {
            return true;
        }
        plugin.service().toggle(player);
        return true;
    }
}
