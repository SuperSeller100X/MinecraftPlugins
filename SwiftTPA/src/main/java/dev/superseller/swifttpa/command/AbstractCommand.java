package dev.superseller.swifttpa.command;

import dev.superseller.swifttpa.SwiftTPAPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Shared plumbing for every SwiftTPA command: players-only guards, permission
 * checks with the configurable denial message, name resolution and
 * case-insensitive prefix filtering for tab completion.
 */
public abstract class AbstractCommand implements CommandExecutor, TabCompleter {

    protected final SwiftTPAPlugin plugin;

    protected AbstractCommand(SwiftTPAPlugin plugin) {
        this.plugin = plugin;
    }

    /** Returns the sender as a player, or sends players-only and returns null. */
    protected Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.messages().send(sender, "players-only");
        return null;
    }

    /** True when the sender holds the node; otherwise sends no-permission. */
    protected boolean requirePermission(CommandSender sender, String node) {
        if (sender.hasPermission(node)) {
            return true;
        }
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    /** Finds an online player by exact name first, then case-insensitively. */
    protected Player findPlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    /** Names of all online players, optionally excluding one (usually the sender). */
    protected List<String> onlineNames(Player exclude) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (exclude != null && player.getUniqueId().equals(exclude.getUniqueId())) {
                continue;
            }
            names.add(player.getName());
        }
        return names;
    }

    /** Case-insensitive prefix filter used by every tab completer. */
    protected static List<String> filter(Collection<String> options, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                matches.add(option);
            }
        }
        Collections.sort(matches, String.CASE_INSENSITIVE_ORDER);
        return matches;
    }

    /** The tab-completion contract: no prefix filter by default. */
    protected List<String> complete(String[] args) {
        return List.of();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command,
                                      String alias, String[] args) {
        return complete(args);
    }
}
