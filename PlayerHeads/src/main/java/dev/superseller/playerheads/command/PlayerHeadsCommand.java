package dev.superseller.playerheads.command;

import dev.superseller.playerheads.PlayerHeadsPlugin;
import dev.superseller.playerheads.config.PlayerHeadsConfig;
import dev.superseller.playerheads.util.Amounts;
import dev.superseller.playerheads.util.Names;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Handles {@code /playerheads <player> [amount]} (aliases: playerhead, phead,
 * ph) and {@code /playerheads reload}.
 */
public final class PlayerHeadsCommand implements CommandExecutor, TabCompleter {

    private final PlayerHeadsPlugin plugin;

    public PlayerHeadsCommand(PlayerHeadsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.messages().sendHelp(sender);
            return true;
        }

        String first = args[0];

        if (first.equalsIgnoreCase("help")) {
            plugin.messages().sendHelp(sender);
            return true;
        }

        if (first.equalsIgnoreCase("reload") && args.length == 1) {
            if (!sender.hasPermission(Permissions.RELOAD)) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            plugin.reloadAll();
            plugin.messages().send(sender, "reloaded");
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }

        if (!player.hasPermission(Permissions.USE)) {
            plugin.messages().send(player, "no-permission");
            return true;
        }

        String targetName = first;
        if (!Names.isValid(targetName, plugin.settings().namePattern())) {
            plugin.messages().send(player, "invalid-name", Map.of("name", targetName));
            return true;
        }

        String amountRaw = args.length >= 2 ? args[1] : null;
        int max = player.hasPermission(Permissions.BYPASS_MAX)
                ? PlayerHeadsConfig.hardLimit()
                : plugin.settings().maxAmount();
        Amounts.Result result = Amounts.parse(amountRaw, plugin.settings().defaultAmount(), max);
        if (!result.ok()) {
            plugin.messages().send(player, result.errorKey(), result.placeholders());
            return true;
        }

        plugin.headService().giveHead(player, targetName, result.amount());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            Set<String> suggestions = new LinkedHashSet<>();
            if (sender.hasPermission(Permissions.RELOAD) && "reload".startsWith(prefix)) {
                suggestions.add("reload");
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(online.getName());
                }
            }
            return new ArrayList<>(suggestions);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            Set<String> suggestions = new LinkedHashSet<>(List.of("1", "16", "64"));
            suggestions.add(String.valueOf(plugin.settings().defaultAmount()));
            suggestions.add(String.valueOf(plugin.settings().maxAmount()));
            return suggestions.stream()
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
        }
        return List.of();
    }
}
