package dev.superseller.chunkvoter.command;

import dev.superseller.chunkvoter.ChunkVoterPlugin;

import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /chunkvoter — start a chunk regeneration vote, cast a yes/no vote or show
 * the running vote. Aliases: /chunkvote, /cv.
 */
public final class ChunkVoteCommand implements CommandExecutor, TabCompleter {

    private final ChunkVoterPlugin plugin;

    public ChunkVoteCommand(ChunkVoterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            return plugin.voteManager().start(player);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "yes", "y", "1" -> plugin.voteManager().castVote(player, true);
            case "no", "n", "0" -> plugin.voteManager().castVote(player, false);
            case "info", "status" -> plugin.voteManager().info(player);
            default -> {
                plugin.messages().sendHelp(sender, "chunkvoter");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("yes", "no", "info");
        }
        return List.of();
    }
}
