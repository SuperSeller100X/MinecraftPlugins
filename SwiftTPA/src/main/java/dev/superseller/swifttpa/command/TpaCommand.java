package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;
import dev.superseller.swifttpa.request.RequestType;

import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /tpa {@code <player>} — ask a player if you may teleport to them. */
public final class TpaCommand extends AbstractCommand {

    public TpaCommand(SwiftTPAPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "swifttpa.tpa")) {
            return true;
        }
        if (args.length == 0 || args[0].isBlank()) {
            plugin.messages().send(player, "usage-tpa");
            return true;
        }
        Player target = findPlayer(args[0]);
        if (target == null) {
            plugin.messages().send(player, "player-not-found", Map.of("input", args[0]));
            return true;
        }
        plugin.service().sendRequest(player, target, RequestType.TPA);
        return true;
    }

    @Override
    public List<String> complete(String[] args) {
        return args.length == 1 ? filter(onlineNames(null), args[0]) : List.of();
    }
}
