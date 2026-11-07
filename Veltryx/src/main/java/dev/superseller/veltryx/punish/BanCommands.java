package dev.superseller.veltryx.punish;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.model.BanEntry;
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
 * {@code /bans [player]} — list active bans or a player's ban history.
 * {@code /unban <player|ban-id>} — lift an active ban.
 */
public final class BanCommands implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final BanService bans;

    public BanCommands(Messages messages, BanService bans) {
        this.messages = messages;
        this.bans = bans;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veltryx.bans.manage")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (command.getName().equalsIgnoreCase("unban")) {
            unban(sender, label, args);
            return true;
        }
        list(sender, args);
        return true;
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            OfflinePlayer target = Players.offline(args[0]);
            if (target == null) {
                messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
                return;
            }
            List<BanEntry> history = bans.history(target.getUniqueId());
            if (history.isEmpty()) {
                messages.send(sender, "bans.no-history", Map.of("player", args[0]));
                return;
            }
            messages.send(sender, "bans.history-header",
                    Map.of("player", args[0], "count", String.valueOf(history.size())));
            for (BanEntry entry : history) {
                sendEntry(sender, entry);
            }
            return;
        }
        List<BanEntry> active = bans.activeBans();
        if (active.isEmpty()) {
            messages.send(sender, "bans.none");
            return;
        }
        messages.send(sender, "bans.header", Map.of("count", String.valueOf(active.size())));
        for (BanEntry entry : active) {
            sendEntry(sender, entry);
        }
    }

    private void sendEntry(CommandSender sender, BanEntry entry) {
        String expires = entry.expires() == TimeUtil.NEVER
                ? "perm"
                : TimeUtil.formatTimestamp(entry.expires());
        sender.sendMessage(messages.prefixed("bans.entry", Map.of(
                "id", entry.id(),
                "player", entry.name(),
                "category", entry.category(),
                "reason", entry.reason(),
                "expires", expires,
                "staff", entry.staff())));
    }

    private void unban(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            messages.send(sender, "unban.usage", Map.of("command", "/" + label));
            return;
        }
        Optional<BanEntry> lifted = bans.unban(args[0]);
        if (lifted.isEmpty()) {
            messages.send(sender, "unban.not-found", Map.of("input", args[0]));
            return;
        }
        messages.send(sender, "unban.done", Map.of(
                "player", lifted.get().name(),
                "id", lifted.get().id()));
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
