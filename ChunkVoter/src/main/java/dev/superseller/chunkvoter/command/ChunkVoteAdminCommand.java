package dev.superseller.chunkvoter.command;

import dev.superseller.chunkvoter.ChunkVoterPlugin;
import dev.superseller.chunkvoter.vote.ChunkKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /chunkvoteadmin — admin tools. Aliases: /cva, /chunkvoteradmin.
 */
public final class ChunkVoteAdminCommand implements CommandExecutor, TabCompleter {

    private final ChunkVoterPlugin plugin;

    public ChunkVoteAdminCommand(ChunkVoterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.messages().sendHelp(sender, "chunkvoteadmin");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload", "rl" -> reload(sender);
            case "list", "ls" -> list(sender);
            case "cancel" -> cancel(sender, args);
            case "force", "regen" -> force(sender, args);
            case "info" -> info(sender);
            default -> {
                plugin.messages().sendHelp(sender, "chunkvoteadmin");
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("chunkvoter.admin.reload")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        plugin.reload();
        plugin.messages().send(sender, "admin-reload",
                Map.of("worldguard", plugin.worldGuard().describe()));
        return true;
    }

    private boolean list(CommandSender sender) {
        if (!sender.hasPermission("chunkvoter.admin.list")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        plugin.voteManager().adminList(sender);
        return true;
    }

    private boolean cancel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chunkvoter.admin.cancel")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("-all")) {
            int n = plugin.voteManager().cancelAll();
            sender.sendMessage(plugin.messages().deserialize(
                    "<green>Cancelled " + n + " chunk vote(s).</green>"));
            return true;
        }
        ChunkResolution res = resolveChunk(sender, args, 1);
        if (res == null) {
            return true;
        }
        if (plugin.voteManager().cancel(res.key())) {
            sender.sendMessage(plugin.messages().deserialize(
                    "<green>Cancelled the vote at chunk " + res.key() + ".</green>"));
        } else {
            plugin.messages().send(sender, "no-active");
        }
        return true;
    }

    private boolean force(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chunkvoter.admin.force")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        ChunkResolution res = resolveChunk(sender, args, 1);
        if (res == null) {
            return true;
        }
        plugin.voteManager().force(res.world(), res.x(), res.z());
        return true;
    }

    private boolean info(CommandSender sender) {
        if (!sender.hasPermission("chunkvoter.admin.info")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        plugin.voteManager().adminInfo(sender);
        return true;
    }

    /**
     * Resolves the target chunk. For players: no coords = the chunk they stand
     * in, or explicit {@code <x> <z>} in their world. For console:
     * {@code <world> <x> <z>}.
     */
    private ChunkResolution resolveChunk(CommandSender sender, String[] args, int start) {
        if (sender instanceof Player player) {
            if (args.length >= start + 2) {
                Integer x = parseInt(args[start]);
                Integer z = parseInt(args[start + 1]);
                if (x == null || z == null) {
                    plugin.messages().send(sender, "invalid-coords");
                    return null;
                }
                return new ChunkResolution(player.getWorld(), x, z);
            }
            Chunk c = player.getLocation().getChunk();
            return new ChunkResolution(c.getWorld(), c.getX(), c.getZ());
        }
        if (args.length >= start + 3) {
            World world = Bukkit.getWorld(args[start]);
            Integer x = parseInt(args[start + 1]);
            Integer z = parseInt(args[start + 2]);
            if (world == null || x == null || z == null) {
                plugin.messages().send(sender, "invalid-coords");
                return null;
            }
            return new ChunkResolution(world, x, z);
        }
        plugin.messages().send(sender, "invalid-coords");
        return null;
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "list", "cancel", "force", "info");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) {
            return List.of("-all");
        }
        return new ArrayList<>();
    }

    private record ChunkResolution(World world, int x, int z) {
        ChunkKey key() {
            return ChunkKey.of(world, x, z);
        }
    }
}
