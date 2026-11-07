package dev.superseller.veltryx.punish;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.model.MuteEntry;
import dev.superseller.veltryx.util.Players;
import dev.superseller.veltryx.util.TimeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /mute <player> [duration] [reason]} and {@code /unmute <player>}.
 * Muted players cannot chat (see {@code staff/ChatListener}).
 */
public final class MuteCommand implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final VeltryxConfig config;
    private final MuteService mutes;

    public MuteCommand(Messages messages, VeltryxConfig config, MuteService mutes) {
        this.messages = messages;
        this.config = config;
        this.mutes = mutes;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veltryx.moderation.mute")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (command.getName().equalsIgnoreCase("unmute")) {
            unmute(sender, args);
            return true;
        }
        mute(sender, label, args);
        return true;
    }

    private void mute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            messages.send(sender, "mute.usage", Map.of("command", "/" + label));
            return;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
            return;
        }
        long durationSeconds = config.muteDefaultSeconds();
        int reasonStart = 1;
        if (args.length >= 2) {
            long parsed = TimeUtil.parseSeconds(args[1]);
            if (parsed != TimeUtil.INVALID) {
                durationSeconds = parsed == Long.MAX_VALUE ? Long.MAX_VALUE : parsed;
                reasonStart = 2;
            }
        }
        String reason = args.length > reasonStart
                ? String.join(" ", java.util.Arrays.copyOfRange(args, reasonStart, args.length))
                : "Muted";
        String name = Players.name(target, args[0]);
        long expires = TimeUtil.expiryMillis(System.currentTimeMillis(), durationSeconds);
        MuteEntry entry = mutes.mute(target.getUniqueId(), name, reason, sender.getName(), expires);

        String duration = expires == TimeUtil.NEVER ? "permanent" : TimeUtil.format(durationSeconds);
        messages.send(sender, "mute.done", Map.of(
                "player", name, "duration", duration, "reason", reason, "id", entry.id()));
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            messages.send(online, "mute.notify", Map.of(
                    "duration", duration, "reason", reason,
                    "staff", sender.getName()));
        }
    }

    private void unmute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            messages.send(sender, "unmute.usage");
            return;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
            return;
        }
        Optional<MuteEntry> lifted = mutes.unmute(target.getUniqueId());
        if (lifted.isEmpty()) {
            messages.send(sender, "unmute.none", Map.of("player", args[0]));
            return;
        }
        messages.send(sender, "unmute.done", Map.of("player", lifted.get().name()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            String lower = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String name : names) {
                if (name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                    out.add(name);
                }
            }
            return out;
        }
        if (args.length == 2 && command.getName().equalsIgnoreCase("mute")) {
            return filter(List.of("5m", "30m", "1h", "1d", "7d", "perm"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
