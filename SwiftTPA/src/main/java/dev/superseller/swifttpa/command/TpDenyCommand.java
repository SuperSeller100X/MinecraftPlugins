package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;
import dev.superseller.swifttpa.request.TeleportRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /tpdeny [player] — denies the named player's request, or the newest
 * request when no name is given.
 */
public final class TpDenyCommand extends AbstractCommand {

    public TpDenyCommand(SwiftTPAPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "swifttpa.deny")) {
            return true;
        }
        String senderName = args.length > 0 ? args[0] : null;
        plugin.service().deny(player, senderName);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        if (sender instanceof Player viewer) {
            UUID viewerId = viewer.getUniqueId();
            for (TeleportRequest request : plugin.service().store().incoming(viewerId)) {
                Player requestSender = Bukkit.getPlayer(request.sender());
                if (requestSender != null && !names.contains(requestSender.getName())) {
                    names.add(requestSender.getName());
                }
            }
        }
        return filter(names, args[0]);
    }
}
