package dev.superseller.hourglass.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.config.Messages;
import dev.superseller.hourglass.data.PlaytimeRecord;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Shared plumbing for the two commands this plugin registers: sub-command
 * lookup (including the short aliases), permission checks, argument parsing,
 * usage and error reporting, and tab completion.
 *
 * <p>Subclasses only declare their sub-commands; every failure mode — unknown
 * sub-command, missing permission, console use of a player-only action, a bad
 * duration, an unknown player — is handled once, here, and reported through
 * {@code messages.yml}.
 */
public abstract class CommandBase implements CommandExecutor, TabCompleter {

    protected final HourGlassPlugin plugin;
    protected final List<SubCommand> subCommands;

    protected CommandBase(HourGlassPlugin plugin) {
        this.plugin = plugin;
        this.subCommands = build();
    }

    /** The sub-commands this command offers, in help/usage order. */
    protected abstract List<SubCommand> build();

    /** Message key of the help text (a list of lines). */
    protected abstract String helpKey();

    /** Root usage shown when nothing is typed correctly. */
    protected abstract String rootUsage();

    /** Runs when the command is used with no arguments at all. */
    protected abstract void onNoArgs(CommandSender sender, String label);

    /** Whether the short alias of a sub-command may be typed. */
    protected boolean shortAliases() {
        return plugin.config().commandsShortAliases();
    }

    // -------------------------------------------------------------- dispatch

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) && !plugin.config().commandsAllowConsole()) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            onNoArgs(sender, label);
            return true;
        }
        SubCommand sub = find(args[0]);
        if (sub == null) {
            plugin.messages().send(sender, "unknown-subcommand", Messages.ph("input", args[0],
                    "label", label));
            sendUsage(sender, rootUsage());
            return true;
        }
        if (!sub.allowed(sender)) {
            plugin.messages().send(sender, "no-permission");
            plugin.sounds().play(sender, "command-error");
            return true;
        }
        String[] rest = args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
        try {
            sub.run(sender, label, rest);
        } catch (SubCommand.PlayersOnlyException e) {
            plugin.messages().send(sender, "players-only");
        } catch (UsageException e) {
            sendUsage(sender, e.usage());
            plugin.sounds().play(sender, "command-error");
        } catch (Exception e) {
            plugin.getLogger().severe("Command /" + label + " " + String.join(" ", args) + " failed: " + e);
            plugin.messages().send(sender, "internal-error");
        }
        return true;
    }

    /** Thrown by a sub-command when the caller's arguments are unusable. */
    protected static final class UsageException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String usage;

        UsageException(String usage) {
            super(usage);
            this.usage = usage;
        }

        String usage() {
            return usage;
        }
    }

    protected SubCommand find(String token) {
        if (token == null) {
            return null;
        }
        for (SubCommand sub : subCommands) {
            if (sub.matches(token, true)) {
                return sub;
            }
        }
        return null;
    }

    /** All sub-command names the sender may use, for help and completion. */
    protected List<SubCommand> visible(CommandSender sender) {
        List<SubCommand> out = new ArrayList<>();
        for (SubCommand sub : subCommands) {
            if (sub.allowed(sender)) {
                out.add(sub);
            }
        }
        return out;
    }

    // -------------------------------------------------------------- completion

    @Override
    public final List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        String last = args[args.length - 1].toLowerCase(Locale.US);
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (SubCommand sub : visible(sender)) {
                if (sub.startsWith(last, shortAliases())) {
                    options.add(sub.name());
                }
                if (shortAliases()) {
                    for (String alias : sub.aliases()) {
                        if (alias.startsWith(last) && !options.contains(alias)) {
                            options.add(alias);
                        }
                    }
                }
            }
            return limit(sort(options));
        }
        SubCommand sub = find(args[0]);
        if (sub == null || !sub.allowed(sender)) {
            return List.of();
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        List<String> options = new ArrayList<>(sub.complete(sender, rest, last));
        return limit(sort(options));
    }

    private List<String> limit(List<String> options) {
        int max = plugin.config().commandsTabLimit();
        if (options.isEmpty()) {
            return List.of();
        }
        // Bukkit completes the last typed token itself, so strip nothing here.
        return options.size() <= max ? options : new ArrayList<>(options.subList(0, max));
    }

    private static List<String> sort(List<String> options) {
        List<String> copy = new ArrayList<>(options);
        copy.sort(String.CASE_INSENSITIVE_ORDER);
        return copy;
    }

    // ---------------------------------------------------------------- helpers

    protected void sendUsage(CommandSender sender, String usage) {
        if (usage == null || usage.isBlank()) {
            plugin.messages().send(sender, "usage", Messages.ph("usage", rootUsage()));
            return;
        }
        plugin.messages().send(sender, "usage", Messages.ph("usage", usage));
    }

    /** Prints the help lines this command offers to this sender. */
    protected void sendHelp(CommandSender sender) {
        plugin.messages().sendList(sender, helpKey(), Messages.ph("label", rootUsage()));
    }

    protected Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.messages().send(sender, "players-only");
        return null;
    }

    /** Resolves a required argument into a record, or throws {@link UsageException}. */
    protected PlaytimeRecord requireRecord(CommandSender sender, String name, String usage) {
        if (name == null || name.isBlank()) {
            throw new UsageException(usage);
        }
        PlaytimeRecord record = plugin.playtime().resolve(name);
        if (record == null) {
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                record = plugin.playtime().getOrCreate(online.getUniqueId(), online.getName());
            }
        }
        if (record == null) {
            plugin.messages().send(sender, "player-not-found", Messages.ph("input", name));
            plugin.sounds().play(sender, "command-error");
            return null;
        }
        return record;
    }

    /** Parses a duration argument, or throws {@link UsageException}. */
    protected long requireDuration(CommandSender sender, String raw, String usage) {
        OptionalLong parsed = plugin.config().timeFormat().parseWithSettings(raw);
        if (parsed.isEmpty() || parsed.getAsLong() < 0L) {
            plugin.messages().send(sender, "invalid-duration", Messages.ph("input", String.valueOf(raw)));
            plugin.sounds().play(sender, "command-error");
            throw new UsageException(usage);
        }
        return parsed.getAsLong();
    }

    /** Parses an optional duration; {@code null} when the token is absent. */
    protected Long optionalDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return plugin.config().timeFormat().parseWithSettings(raw).orElse(0L);
    }

    /** The record this command line names, or the viewer's own when omitted. */
    protected PlaytimeRecord recordOrSelf(CommandSender sender, String name, String usage) {
        if (name == null || name.isBlank()) {
            if (sender instanceof Player player) {
                return plugin.playtime().getOrCreate(player.getUniqueId(), player.getName());
            }
            throw new UsageException(usage);
        }
        return requireRecord(sender, name, usage);
    }

    /** Formats seconds with the configured style. */
    protected String pretty(long seconds) {
        return plugin.config().timeFormat().format(seconds);
    }

    /** Tab-completes a player name: online players first, then tracked names. */
    protected List<String> completeNames(String token, int limit, boolean onlineOnly) {
        String prefix = token == null ? "" : token.toLowerCase(Locale.US);
        List<String> out = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            String name = online.getName();
            if (name != null && name.toLowerCase(Locale.US).startsWith(prefix)) {
                out.add(name);
            }
        }
        if (!onlineOnly) {
            for (String name : plugin.playtime().knownNames(0)) {
                if (name.toLowerCase(Locale.US).startsWith(prefix) && !out.contains(name)) {
                    out.add(name);
                }
            }
            if (out.size() < limit && !prefix.isEmpty()) {
                for (OfflineEntry entry : cached(prefix, limit - out.size())) {
                    if (!out.contains(entry.name())) {
                        out.add(entry.name());
                    }
                }
            }
        }
        return out.size() <= limit ? out : new ArrayList<>(out.subList(0, limit));
    }

    /** A name known to the server but not necessarily tracked yet. */
    private record OfflineEntry(String name) {
    }

    /**
     * Last-resort offline names from the server's own user cache. Wrapped in a
     * try/catch because that lookup touches disk on some platforms.
     */
    private List<OfflineEntry> cached(String prefix, int wanted) {
        List<OfflineEntry> out = new ArrayList<>();
        if (wanted <= 0) {
            return out;
        }
        try {
            for (org.bukkit.OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
                String name = offline.getName();
                if (name != null && name.toLowerCase(Locale.US).startsWith(prefix)) {
                    out.add(new OfflineEntry(name));
                    if (out.size() >= wanted) {
                        break;
                    }
                }
            }
        } catch (RuntimeException | UnsupportedOperationException e) {
            if (plugin.config().debug()) {
                plugin.getLogger().fine("Offline name lookup unavailable: " + e.getMessage());
            }
        }
        return out;
    }
}
