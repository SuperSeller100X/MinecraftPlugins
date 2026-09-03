package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;
import dev.superseller.swifttpa.request.TeleportRequest;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /tpaccept [player] — accepts the named player's request, or the newest
 * request when no name is given.
 */
public final class TpAcceptCommand extends AbstractCommand {

    public TpAcceptCommand(SwiftTPAPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "swifttpa.accept")) {
            return true;
        }
        String senderName = args.length > 0 ? args[0] : null;
        plugin.service().accept(player, senderName);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        if (sender instanceof Player viewer) {
            for (TeleportRequest request : plugin.service().store().incoming(viewer.getUniqueId())) {
                Player requestSender = Bukkit.getPlayer(request.sender());
                if (requestSender != null && !names.contains(requestSender.getName())) {
                    names.add(requestSender.getName());
                }
            }
        }
        return filter(names, args[0]);
    }
}
