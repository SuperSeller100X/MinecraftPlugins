package dev.superseller.randomstructurechallenge.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.superseller.randomstructurechallenge.RandomStructureChallengePlugin;
import dev.superseller.randomstructurechallenge.challenge.ChallengeManager;
import dev.superseller.randomstructurechallenge.util.DurationParser;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /challenge} plus short forms: {@code s p r x i rl h}.
 */
public final class ChallengeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> START = List.of("start", "s");
    private static final List<String> STOP = List.of("stop", "x", "end");
    private static final List<String> PAUSE = List.of("pause", "p");
    private static final List<String> RESUME = List.of("resume", "r");
    private static final List<String> STATUS = List.of("status", "stat", "i", "info");
    private static final List<String> RELOAD = List.of("reload", "rl");
    private static final List<String> HELP = List.of("help", "h", "?");
    private static final List<String> INTERVAL_SUGGESTIONS =
            List.of("10", "15", "30", "60", "90", "120", "1m", "2m", "5m");

    private final RandomStructureChallengePlugin plugin;

    public ChallengeCommand(RandomStructureChallengePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.messages().sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        ChallengeManager manager = plugin.manager();

        if (matches(sub, HELP)) {
            plugin.messages().sendHelp(sender);
            return true;
        }
        if (matches(sub, START)) {
            return handleStart(sender, args);
        }
        if (matches(sub, STOP)) {
            if (!sender.hasPermission(Permissions.STOP)) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            manager.stop(sender);
            return true;
        }
        if (matches(sub, PAUSE)) {
            if (!sender.hasPermission(Permissions.PAUSE)) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            manager.pause(sender);
            return true;
        }
        if (matches(sub, RESUME)) {
            if (!sender.hasPermission(Permissions.RESUME)) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            manager.resume(sender);
            return true;
        }
        if (matches(sub, STATUS)) {
            if (!sender.hasPermission(Permissions.STATUS)) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            manager.sendStatus(sender);
            return true;
        }
        if (matches(sub, RELOAD)) {
            if (!sender.hasPermission(Permissions.RELOAD)) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            plugin.reloadAll();
            plugin.messages().send(sender, "reloaded");
            return true;
        }

        plugin.messages().send(sender, "unknown-subcommand", Map.of("input", args[0]));
        return true;
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.START)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "players-only");
                return true;
            }
            plugin.manager().beginAwaiting(player);
            return true;
        }
        String joined = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        DurationParser.Result parsed = DurationParser.parse(joined);
        if (!parsed.ok()) {
            plugin.messages().send(sender, parsed.errorKey(), Map.of(
                    "input", parsed.input(),
                    "min", Integer.toString(plugin.settings().minInterval()),
                    "max", Integer.toString(plugin.settings().maxInterval())
            ));
            return true;
        }
        plugin.manager().start(sender, parsed.seconds());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            Set<String> suggestions = new LinkedHashSet<>();
            addIfAllowed(sender, Permissions.START, prefix, suggestions, START);
            addIfAllowed(sender, Permissions.STOP, prefix, suggestions, STOP);
            addIfAllowed(sender, Permissions.PAUSE, prefix, suggestions, PAUSE);
            addIfAllowed(sender, Permissions.RESUME, prefix, suggestions, RESUME);
            addIfAllowed(sender, Permissions.STATUS, prefix, suggestions, STATUS);
            addIfAllowed(sender, Permissions.RELOAD, prefix, suggestions, RELOAD);
            addIfAllowed(sender, null, prefix, suggestions, HELP);
            return new ArrayList<>(suggestions);
        }
        if (args.length >= 2 && matches(args[0].toLowerCase(Locale.ROOT), START)
                && sender.hasPermission(Permissions.START)) {
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String suggestion : INTERVAL_SUGGESTIONS) {
                if (suggestion.startsWith(prefix)) {
                    out.add(suggestion);
                }
            }
            return out;
        }
        return List.of();
    }

    private static void addIfAllowed(
            CommandSender sender,
            String permission,
            String prefix,
            Set<String> out,
            List<String> names) {
        if (permission != null && !sender.hasPermission(permission)) {
            return;
        }
        for (String name : names) {
            if (name.startsWith(prefix)) {
                out.add(name);
            }
        }
    }

    private static boolean matches(String input, List<String> aliases) {
        return aliases.contains(input);
    }
}
