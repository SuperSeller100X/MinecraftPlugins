package dev.superseller.rapidhoppers.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.superseller.rapidhoppers.RapidHoppersPlugin;
import dev.superseller.rapidhoppers.util.Sounds;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /rapidhoppers} — player facing command.
 *
 * <p>Aliases: {@code /rhoppers}, {@code /rh}. Every sub-command also has a
 * short form: {@code i} info, {@code s} stats, {@code g} gui, {@code h} help.</p>
 */
public final class RapidHoppersCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "info", "i", "stats", "s", "gui", "g", "help", "h");

    private final RapidHoppersPlugin plugin;
    private final StatusRenderer renderer;

    public RapidHoppersCommand(RapidHoppersPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new StatusRenderer(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(Permissions.USE)) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info", "i" -> {
                if (denied(sender, Permissions.INFO)) {
                    return true;
                }
                renderer.sendInfo(sender);
            }
            case "stats", "s" -> {
                if (denied(sender, Permissions.STATS)) {
                    return true;
                }
                renderer.sendStats(sender);
            }
            case "gui", "g", "panel", "menu" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "general.players-only");
                    return true;
                }
                if (!player.hasPermission(Permissions.GUI) && !player.hasPermission(Permissions.ADMIN)) {
                    plugin.sounds().play(player, Sounds.ERROR);
                    plugin.messages().send(player, "gui.no-permission");
                    return true;
                }
                plugin.panel().open(player, !player.hasPermission(Permissions.ADMIN));
            }
            case "help", "h", "?" -> plugin.messages().sendHelp(sender, "player");
            default -> plugin.messages().send(sender, "general.unknown-command", Map.of("label", label));
        }
        return true;
    }

    private boolean denied(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return false;
        }
        plugin.messages().send(sender, "general.no-permission");
        if (sender instanceof Player player) {
            plugin.sounds().play(player, Sounds.ERROR);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        return List.of();
    }

    public static List<String> filter(List<String> options, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(option);
            }
        }
        return out;
    }
}
