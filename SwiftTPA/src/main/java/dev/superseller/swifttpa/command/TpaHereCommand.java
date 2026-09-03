package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;
import dev.superseller.swifttpa.request.RequestType;

import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /tpahere {@code <player>} — ask a player to teleport to you. */
public final class TpaHereCommand extends AbstractCommand {

    public TpaHereCommand(SwiftTPAPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "swifttpa.tpahere")) {
            return true;
        }
        if (args.length == 0 || args[0].isBlank()) {
            plugin.messages().send(player, "usage-tpahere");
            return true;
        }
        Player target = findPlayer(args[0]);
        if (target == null) {
            plugin.messages().send(player, "player-not-found", Map.of("input", args[0]));
            return true;
        }
        plugin.service().sendRequest(player, target, RequestType.TPA_HERE);
        return true;
    }

    @Override
    public List<String> complete(String[] args) {
        return args.length == 1 ? filter(onlineNames(null), args[0]) : List.of();
    }
}
