package dev.superseller.combattag.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Permission-aware tab completion for both CombatTag commands. */
public final class CombatTabCompleter implements TabCompleter {

    private static final List<String> PLAYER_SUBS =
            List.of("status", "s", "gui", "g", "time", "t", "check", "c", "info", "i", "help", "h");
    private static final List<String> ADMIN_SUBS =
            List.of("tag", "t", "untag", "u", "clear", "c", "list", "l", "exempt", "e",
                    "duration", "d", "stats", "st", "gui", "g", "reload", "rl");
    private static final List<String> DURATIONS = List.of("10", "15", "30", "60", "30s", "2m");

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        boolean admin = command.getName().toLowerCase(Locale.ROOT).contains("admin");
        if (admin && !sender.hasPermission("combattag.admin")) {
            return Collections.emptyList();
        }
        if (!admin && !sender.hasPermission("combattag.use")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(admin ? ADMIN_SUBS : PLAYER_SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (admin) {
                if (sub.equals("duration") || sub.equals("d")) {
                    return filter(DURATIONS, args[1]);
                }
                if (List.of("tag", "t", "untag", "u", "exempt", "e").contains(sub)) {
                    return filter(onlinePlayers(sender), args[1]);
                }
                return Collections.emptyList();
            }
            if (sub.equals("check") || sub.equals("c")) {
                return filter(onlinePlayers(sender), args[1]);
            }
            return Collections.emptyList();
        }
        if (admin && args.length == 3 && (sub.equals("tag") || sub.equals("t"))) {
            return filter(DURATIONS, args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> onlinePlayers(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!(sender instanceof Player viewer) || viewer.canSee(p)) {
                names.add(p.getName());
            }
        }
        return names;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(option);
            }
        }
        Collections.sort(out);
        return out;
    }
}
