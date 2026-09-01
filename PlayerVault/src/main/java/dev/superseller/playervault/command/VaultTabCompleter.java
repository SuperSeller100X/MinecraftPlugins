package dev.superseller.playervault.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Tab completion for both {@code /playervault} and {@code /playervaultadmin}.
 *
 * <p>Only sub-commands the sender is actually allowed to run are offered, and the
 * player list is filtered by visibility so staff cannot enumerate vanished players
 * through tab completion.
 */
public final class VaultTabCompleter implements TabCompleter {

    private static final List<String> ROW_COUNTS = List.of("1", "2", "3", "5", "10");

    private record Entry(String name, String permission) {
    }

    private static final List<Entry> PLAYER_SUBCOMMANDS = List.of(
            new Entry("upgrade", "playervault.upgrade"),
            new Entry("price", "playervault.price"),
            new Entry("info", "playervault.info"),
            new Entry("sort", "playervault.sort"),
            new Entry("reload", "playervault.reload"),
            new Entry("help", null));

    private static final List<Entry> ADMIN_SUBCOMMANDS = List.of(
            new Entry("open", "playervault.admin.open"),
            new Entry("rows", "playervault.admin.rows"),
            new Entry("addrows", "playervault.admin.rows"),
            new Entry("reset", "playervault.admin.reset"),
            new Entry("clear", "playervault.admin.clear"),
            new Entry("info", "playervault.admin.info"),
            new Entry("price", null),
            new Entry("reload", "playervault.reload"),
            new Entry("help", null));

    /** Sub-commands whose second argument is a player name. */
    private static final List<String> ADMIN_TARGET_COMMANDS =
            List.of("open", "o", "rows", "r", "setrows", "addrows", "ar", "give", "reset", "x", "clear", "empty", "c",
                    "info", "i", "inspect");

    /** Admin sub-commands whose third argument is a number. */
    private static final List<String> ADMIN_NUMBER_COMMANDS = List.of("rows", "r", "setrows", "addrows", "ar", "give");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = command.getName().equalsIgnoreCase("playervaultadmin");
        if (args.length == 1) {
            return filter(admin ? ADMIN_SUBCOMMANDS : PLAYER_SUBCOMMANDS, sender, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (admin) {
                if (ADMIN_TARGET_COMMANDS.contains(sub)) {
                    return players(sender, args[1]);
                }
                if (sub.equals("price") || sub.equals("p") || sub.equals("cost")) {
                    return match(ROW_COUNTS, args[1]);
                }
                return List.of();
            }
            if (sub.equals("upgrade") || sub.equals("u") || sub.equals("up") || sub.equals("buy") || sub.equals("b")) {
                List<String> options = new ArrayList<>(ROW_COUNTS);
                options.add("yes");
                return match(options, args[1]);
            }
            if (sub.equals("price") || sub.equals("p") || sub.equals("cost")) {
                return match(ROW_COUNTS, args[1]);
            }
            return List.of();
        }
        if (args.length == 3 && admin && ADMIN_NUMBER_COMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            return match(ROW_COUNTS, args[2]);
        }
        if (args.length == 3 && !admin
                && (args[0].equalsIgnoreCase("upgrade") || args[0].equalsIgnoreCase("u"))) {
            return match(List.of("yes"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<Entry> entries, CommandSender sender, String prefix) {
        List<String> options = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.permission() == null || sender.hasPermission(entry.permission())) {
                options.add(entry.name());
            }
        }
        return match(options, prefix);
    }

    private List<String> players(CommandSender sender, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player viewer && !viewer.canSee(player)) {
                continue;
            }
            String name = player.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(needle)) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private List<String> match(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return List.copyOf(options);
        }
        String needle = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(needle))
                .toList();
    }
}
