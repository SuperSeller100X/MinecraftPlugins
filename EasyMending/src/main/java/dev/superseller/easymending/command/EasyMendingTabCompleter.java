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

    private static final List<String> SCOPES = List.of(
            "hand", "offhand", "armor", "hotbar", "all"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender == null || args == null || args.length == 0) {
            return Collections.emptyList();
        }

        String cmdName = command != null ? command.getName().toLowerCase(Locale.ROOT) : "";
        String aliasLower = alias != null ? alias.toLowerCase(Locale.ROOT) : "";

        boolean isAdminCommand = cmdName.equals("easymendingadmin")
                || aliasLower.equals("ema")
                || aliasLower.equals("emadmin")
                || aliasLower.equals("mendadmin");

        if (isAdminCommand) {
            return completeAdmin(sender, args, 0);
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("easymending.gui")) options.add("gui");
            if (sender.hasPermission("easymending.hand")) options.add("hand");
            if (sender.hasPermission("easymending.offhand")) options.add("offhand");
            if (sender.hasPermission("easymending.armor")) options.add("armor");
            if (sender.hasPermission("easymending.hotbar")) options.add("hotbar");
            if (sender.hasPermission("easymending.all")) options.add("all");
            if (sender.hasPermission("easymending.info")) options.add("info");
            if (sender.hasPermission("easymending.cost")) options.add("cost");
            if (sender.hasPermission("easymending.use")) options.add("help");
            if (sender.hasPermission("easymending.admin")) options.add("admin");

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
            List<String> adminSubs = new ArrayList<>();
            if (sender.hasPermission("easymending.admin.reload")) adminSubs.add("reload");
            if (sender.hasPermission("easymending.admin.repair")) adminSubs.add("repair");
            if (sender.hasPermission("easymending.admin.inspect")) adminSubs.add("inspect");
            if (sender.hasPermission("easymending.admin.setratio")) adminSubs.add("setratio");
            if (sender.hasPermission("easymending.admin.bypass")) adminSubs.add("bypass");
            if (sender.hasPermission("easymending.admin.stats")) adminSubs.add("stats");
            adminSubs.add("help");
            return filter(adminSubs, args[offset]);
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
            if (p != null && p.isOnline()) {
                names.add(p.getName());
            }
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
