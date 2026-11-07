package dev.superseller.veltryx.staff;

import dev.superseller.veltryx.anticheat.AntiCheatService;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.BanEntry;
import dev.superseller.veltryx.model.MuteEntry;
import dev.superseller.veltryx.model.Profile;
import dev.superseller.veltryx.model.WarnEntry;
import dev.superseller.veltryx.punish.BanService;
import dev.superseller.veltryx.punish.MuteService;
import dev.superseller.veltryx.punish.WarnService;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Players;
import dev.superseller.veltryx.util.TimeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /lookup <player>} (alias {@code /whois}) — one screen about a player:
 * identity, playtime, money, ping and the moderation record.
 */
public final class LookupCommand implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final DataStore store;
    private final EconomyService economy;
    private final BanService bans;
    private final MuteService mutes;
    private final WarnService warns;
    private final AntiCheatService anticheat;

    public LookupCommand(Messages messages, DataStore store, EconomyService economy,
                         BanService bans, MuteService mutes, WarnService warns,
                         AntiCheatService anticheat) {
        this.messages = messages;
        this.store = store;
        this.economy = economy;
        this.bans = bans;
        this.mutes = mutes;
        this.warns = warns;
        this.anticheat = anticheat;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veltryx.moderation.lookup")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 1) {
            messages.send(sender, "lookup.usage", Map.of("command", "/" + label));
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
            return true;
        }
        String name = Players.name(target, args[0]);
        Profile profile = store.getProfile(target.getUniqueId());

        sender.sendMessage(messages.prefixed("lookup.header", Map.of("player", name)));
        sender.sendMessage(messages.prefixed("lookup.identity", Map.of(
                "uuid", target.getUniqueId().toString(),
                "name", name)));
        if (profile != null) {
            sender.sendMessage(messages.prefixed("lookup.activity", Map.of(
                    "first", TimeUtil.formatTimestamp(profile.firstSeen()),
                    "last", TimeUtil.formatTimestamp(profile.lastSeen()),
                    "playtime", TimeUtil.format(profile.playtimeMs() / 1000L),
                    "joins", String.valueOf(profile.joins()))));
        } else {
            sender.sendMessage(messages.prefixed("lookup.unknown"));
        }
        sender.sendMessage(messages.prefixed("lookup.money", Map.of(
                "amount", economy.format(economy.getBalance(target.getUniqueId())))));

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            sender.sendMessage(messages.prefixed("lookup.online", Map.of(
                    "ping", String.valueOf(online.getPing()),
                    "world", online.getWorld().getName(),
                    "gamemode", String.valueOf(online.getGameMode()))));
        }

        List<BanEntry> activeBans = bans.history(target.getUniqueId()).stream()
                .filter(b -> b.isActiveAt(System.currentTimeMillis())).toList();
        List<MuteEntry> activeMutes = mutes.history(target.getUniqueId()).stream()
                .filter(m -> m.isActiveAt(System.currentTimeMillis())).toList();
        List<WarnEntry> activeWarns = warns.activeWarns(target.getUniqueId());
        sender.sendMessage(messages.prefixed("lookup.moderation", Map.of(
                "bans", String.valueOf(activeBans.size()),
                "mutes", String.valueOf(activeMutes.size()),
                "warns", String.valueOf(activeWarns.size()),
                "flags", String.valueOf(anticheat.storedFlags(target.getUniqueId(), 100).size()))));
        return true;
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
        String lower = args[0].toLowerCase(java.util.Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (name.toLowerCase(java.util.Locale.ROOT).startsWith(lower)) {
                out.add(name);
            }
        }
        return out;
    }
}
