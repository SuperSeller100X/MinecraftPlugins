package dev.superseller.veltryx.punish;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.model.WarnEntry;
import dev.superseller.veltryx.util.Players;
import dev.superseller.veltryx.util.TimeUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /warn <player> <reason>} and {@code /warns <player>}.
 * When a player reaches the configured number of active warnings, the
 * configured escalation fires (kick, temp-mute or temp-ban).
 */
public final class WarnCommand implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final VeltryxConfig config;
    private final WarnService warns;
    private final MuteService mutes;
    private final BanService bans;

    public WarnCommand(Messages messages, VeltryxConfig config, WarnService warns,
                       MuteService mutes, BanService bans) {
        this.messages = messages;
        this.config = config;
        this.warns = warns;
        this.mutes = mutes;
        this.bans = bans;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veltryx.moderation.warn")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (command.getName().equalsIgnoreCase("warns")) {
            listWarns(sender, args);
            return true;
        }
        warn(sender, label, args);
        return true;
    }

    private void warn(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "warn.usage", Map.of("command", "/" + label));
            return;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
            return;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String name = Players.name(target, args[0]);
        int active = warns.warn(target.getUniqueId(), name, reason, sender.getName());

        messages.send(sender, "warn.done", Map.of(
                "player", name, "reason", reason, "count", String.valueOf(active)));
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            messages.send(online, "warn.notify", Map.of(
                    "reason", reason,
                    "count", String.valueOf(active),
                    "threshold", String.valueOf(config.warnThreshold()),
                    "staff", sender.getName()));
        }

        if (active >= config.warnThreshold()) {
            escalate(target, name, active);
        }
    }

    private void escalate(OfflinePlayer target, String name, int active) {
        String action = config.warnAction();
        switch (action) {
            case "mute" -> {
                long expires = TimeUtil.expiryMillis(System.currentTimeMillis(), 3_600L);
                mutes.mute(target.getUniqueId(), name, "Warning threshold reached",
                        "Veltryx", expires);
                notifyAll("warn.threshold-muted", Map.of("player", name,
                        "count", String.valueOf(active)));
            }
            case "ban" -> {
                long expires = TimeUtil.expiryMillis(System.currentTimeMillis(), 86_400L);
                bans.ban(target.getUniqueId(), name, "warns", "Warning threshold reached",
                        active + " active warnings", "Veltryx", expires, true);
                notifyAll("warn.threshold-banned", Map.of("player", name,
                        "count", String.valueOf(active)));
            }
            default -> {
                Player online = Bukkit.getPlayerExact(name);
                if (online != null) {
                    online.kick(messages.render("warn.threshold-kick",
                            Map.of("count", String.valueOf(active))));
                }
                notifyAll("warn.threshold-kicked", Map.of("player", name,
                        "count", String.valueOf(active)));
            }
        }
    }

    private void notifyAll(String key, Map<String, String> values) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("veltryx.moderation.warn")) {
                messages.send(staff, key, values);
            }
        }
        messages.send(Bukkit.getConsoleSender(), key, values);
    }

    private void listWarns(CommandSender sender, String[] args) {
        if (args.length < 1) {
            messages.send(sender, "warns.usage");
            return;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
            return;
        }
        List<WarnEntry> active = warns.activeWarns(target.getUniqueId());
        if (active.isEmpty()) {
            messages.send(sender, "warns.none", Map.of("player", args[0]));
            return;
        }
        messages.send(sender, "warns.header",
                Map.of("player", args[0], "count", String.valueOf(active.size())));
        for (WarnEntry entry : active) {
            sender.sendMessage(messages.render("warns.entry", Map.of(
                    "id", entry.id(),
                    "reason", entry.reason(),
                    "staff", entry.staff(),
                    "issued", TimeUtil.formatTimestamp(entry.issued()))));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
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
}
