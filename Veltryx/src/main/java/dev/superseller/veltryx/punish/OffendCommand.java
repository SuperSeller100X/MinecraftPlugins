package dev.superseller.veltryx.punish;

import dev.superseller.veltryx.anticheat.AntiCheatService;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.model.BanEntry;
import dev.superseller.veltryx.model.FlagEntry;
import dev.superseller.veltryx.util.Players;
import dev.superseller.veltryx.util.TimeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * {@code /offend <player> <reason> [details]} — the DonutSMP-style one-hit ban
 * for staff. {@code xray/esp} bans for 30 days, {@code hacking} (flying,
 * speed, killaura, …) bans permanently, each with the custom Veltryx ban
 * screen and the player's recent anti-cheat flags attached as evidence.
 */
public final class OffendCommand implements CommandExecutor, TabCompleter {

    private final VeltryxConfig config;
    private final Messages messages;
    private final BanService bans;
    private final AntiCheatService anticheat;

    public OffendCommand(VeltryxConfig config, Messages messages, BanService bans,
                         AntiCheatService anticheat) {
        this.config = config;
        this.messages = messages;
        this.bans = bans;
        this.anticheat = anticheat;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veltryx.offend")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "offend.usage", Map.of("command", "/" + label));
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
            return true;
        }
        Optional<VeltryxConfig.OffendReason> resolved = config.resolveOffendReason(args[1]);
        if (resolved.isEmpty()) {
            messages.send(sender, "offend.unknown-reason",
                    Map.of("input", args[1], "reasons", reasonList()));
            return true;
        }
        VeltryxConfig.OffendReason reason = resolved.get();

        String details = args.length > 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                : "";
        String name = Players.name(target, args[0]);

        // Evidence: the target's recent anti-cheat flags, when we have any.
        StringBuilder evidence = new StringBuilder();
        if (!details.isEmpty()) {
            evidence.append(details);
        }
        List<FlagEntry> flags = anticheat.storedFlags(target.getUniqueId(), 8);
        for (FlagEntry flag : flags) {
            if (evidence.length() > 0) {
                evidence.append(" | ");
            }
            evidence.append(flag.check()).append(' ')
                    .append(String.format(Locale.ROOT, "%.1f", flag.vl()))
                    .append(" (").append(flag.detail()).append(')');
        }

        long expires = TimeUtil.expiryMillis(System.currentTimeMillis(), reason.durationSeconds());
        BanEntry entry = bans.ban(target.getUniqueId(), name,
                reason.category(), reason.display(), evidence.toString(),
                sender.getName(), expires, false);

        String duration = reason.durationSeconds() == Long.MAX_VALUE
                ? "permanent"
                : TimeUtil.format(reason.durationSeconds());
        messages.send(sender, "offend.done", Map.of(
                "player", name,
                "reason", reason.display(),
                "duration", duration,
                "id", entry.id()));

        Component staffMessage = messages.prefixed("offend.staff-note", Map.of(
                "staff", sender.getName(),
                "player", name,
                "reason", reason.display(),
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

    private String reasonList() {
        List<String> names = new ArrayList<>();
        for (VeltryxConfig.OffendReason reason : config.offendReasons()) {
            names.add(reason.key());
        }
        return String.join(", ", names);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matchingOnline(args[0]);
        }
        if (args.length == 2) {
            List<String> keys = new ArrayList<>();
            for (VeltryxConfig.OffendReason reason : config.offendReasons()) {
                keys.add(reason.key());
                keys.addAll(reason.aliases());
            }
            return matching(keys, args[1]);
        }
        return List.of();
    }

    private static List<String> matchingOnline(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return matching(names, prefix);
    }

    private static List<String> matching(List<String> options, String prefix) {
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
