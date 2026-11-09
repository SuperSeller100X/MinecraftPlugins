package dev.superseller.veltryx.punish;

import dev.superseller.veltryx.anticheat.AntiCheatService;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.model.BanEntry;
import dev.superseller.veltryx.model.FlagEntry;
import dev.superseller.veltryx.util.Players;
import dev.superseller.veltryx.util.TimeUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * {@code /ban <player> [duration] <reason>} — the plugin's own ban command,
 * backed by the Veltryx ban system (custom ban screen, ban ids, storage,
 * {@code /bans} and {@code /unban}). Minecraft's vanilla ban list is never
 * used; registering this command shadows vanilla {@code /ban}.
 *
 * <p>Syntax: with a duration ({@code 30m}, {@code 12h}, {@code 7d}, {@code 2w},
 * {@code perm}) the ban lasts that long; without one the ban is permanent.
 */
public final class BanCommand implements CommandExecutor, TabCompleter {

    /** Result of {@link #parse}: the ban duration and free-text reason. */
    public record Parsed(long seconds, String reason) {
    }

    private final Messages messages;
    private final BanService bans;
    private final AntiCheatService anticheat;

    public BanCommand(Messages messages, BanService bans, AntiCheatService anticheat) {
        this.messages = messages;
        this.bans = bans;
        this.anticheat = anticheat;
    }

    /**
     * Parses {@code [player] [duration] <reason...>} command arguments.
     *
     * <p>When a second argument parses as a duration it is used as such and the
     * rest is the reason; otherwise everything after the player is a
     * permanent-ban reason. A lone duration without a reason is rejected.
     *
     * @return the parsed ban, or {@code null} when the arguments are invalid
     */
    public static Parsed parse(String[] args) {
        if (args.length < 2) {
            return null;
        }
        if (args.length == 2) {
            if (TimeUtil.parseSeconds(args[1]) != TimeUtil.INVALID) {
                return null; // duration given but no reason
            }
            return new Parsed(Long.MAX_VALUE, args[1]);
        }
        long duration = TimeUtil.parseSeconds(args[1]);
        int reasonStart = duration == TimeUtil.INVALID ? 1 : 2;
        long seconds = duration == TimeUtil.INVALID ? Long.MAX_VALUE : duration;
        String reason = String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length)).trim();
        return reason.isEmpty() ? null : new Parsed(seconds, reason);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veltryx.bans.ban")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        Parsed parsed = parse(args);
        if (parsed == null) {
            messages.send(sender, "ban.usage", Map.of("command", "/" + label));
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
            return true;
        }
        String name = Players.name(target, args[0]);

        // Evidence: the target's recent anti-cheat flags, when we have any.
        StringBuilder evidence = new StringBuilder();
        for (FlagEntry flag : anticheat.storedFlags(target.getUniqueId(), 8)) {
            if (evidence.length() > 0) {
                evidence.append(" | ");
            }
            evidence.append(flag.check()).append(' ')
                    .append(String.format(Locale.ROOT, "%.1f", flag.vl()))
                    .append(" (").append(flag.detail()).append(')');
        }

        long expires = TimeUtil.expiryMillis(System.currentTimeMillis(), parsed.seconds());
        BanEntry entry = bans.ban(target.getUniqueId(), name, "manual", parsed.reason(),
                evidence.toString(), sender.getName(), expires, false);

        String duration = parsed.seconds() == Long.MAX_VALUE
                ? "permanent" : TimeUtil.format(parsed.seconds());
        messages.send(sender, "ban.done", Map.of(
                "player", name,
                "reason", parsed.reason(),
                "duration", duration,
                "id", entry.id()));

        Component staffMessage = messages.render("ban.staff-note", Map.of(
                "staff", sender.getName(),
                "player", name,
                "reason", parsed.reason(),
                "duration", duration,
                "id", entry.id()));
        java.util.UUID senderId = sender instanceof Player senderPlayer
                ? senderPlayer.getUniqueId() : null;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.getUniqueId().equals(senderId)
                    && staff.hasPermission("veltryx.anticheat.alerts")) {
                staff.sendMessage(staffMessage);
            }
        }
        Bukkit.getConsoleSender().sendMessage(staffMessage);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            String lower = args[0].toLowerCase(Locale.ROOT);
            names.removeIf(n -> !n.toLowerCase(Locale.ROOT).startsWith(lower));
            return names;
        }
        if (args.length == 2) {
            List<String> options = new ArrayList<>(
                    List.of("30m", "1h", "12h", "1d", "7d", "30d", "perm"));
            String lower = args[1].toLowerCase(Locale.ROOT);
            options.removeIf(o -> !o.startsWith(lower));
            return options;
        }
        return List.of();
    }
}
