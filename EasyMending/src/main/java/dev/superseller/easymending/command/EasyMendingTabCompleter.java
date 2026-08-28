package dev.superseller.easymending.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Provides intelligent, permission-filtered tab completion for EasyMending commands.
 */
public final class EasyMendingTabCompleter implements TabCompleter {

    private static final List<String> BASE_SUBS = List.of(
            "gui", "hand", "offhand", "armor", "hotbar", "all", "info", "cost", "help"
    );

    private static final List<String> ADMIN_SUBS = List.of(
            "reload", "repair", "inspect", "setratio", "bypass", "stats", "help"
    );

    private static final List<String> SCOPES = List.of(
            "hand", "offhand", "armor", "hotbar", "all"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmdName = command.getName().toLowerCase(Locale.ROOT);

        if (cmdName.equals("easymendingadmin") || alias.equalsIgnoreCase("ema") || alias.equalsIgnoreCase("emadmin")) {
            return completeAdmin(sender, args, 0);
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>(BASE_SUBS);
            if (sender.hasPermission("easymending.admin")) {
                options.add("admin");
            }
            return filter(options, args[0]);
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("admin")) {
            return completeAdmin(sender, args, 1);
        }

        return Collections.emptyList();
    }

    private List<String> completeAdmin(CommandSender sender, String[] args, int offset) {
        if (!sender.hasPermission("easymending.admin")) {
            return Collections.emptyList();
        }

        int index = args.length - 1 - offset;

        if (index == 0) {
            return filter(ADMIN_SUBS, args[offset]);
        }

        String sub = args[offset].toLowerCase(Locale.ROOT);

        if (index == 1) {
            switch (sub) {
                case "repair", "r", "inspect", "i", "bypass", "bp" -> {
                    return filter(getOnlinePlayerNames(), args[offset + 1]);
                }
                case "setratio", "sr" -> {
                    return filter(List.of("1.0", "1.5", "2.0", "2.5", "3.0", "4.0"), args[offset + 1]);
                }
            }
        }

        if (index == 2 && (sub.equals("repair") || sub.equals("r"))) {
            List<String> options = new ArrayList<>(SCOPES);
            options.add("--free");
            return filter(options, args[offset + 2]);
        }

        if (index == 3 && (sub.equals("repair") || sub.equals("r"))) {
            return filter(List.of("--free"), args[offset + 3]);
        }

        return Collections.emptyList();
    }

    private List<String> getOnlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private List<String> filter(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(c);
            }
        }
        Collections.sort(result);
        return result;
    }
}
