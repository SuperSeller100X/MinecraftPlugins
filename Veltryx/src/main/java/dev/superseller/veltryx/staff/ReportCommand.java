package dev.superseller.veltryx.staff;

import dev.superseller.veltryx.config.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /report <player> <reason>} — players report rule-breakers to the
 * staff team (delivery via {@link ReportService}).
 */
public final class ReportCommand implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final ReportService reports;

    public ReportCommand(Messages messages, ReportService reports) {
        this.messages = messages;
        this.reports = reports;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veltryx.report")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (!(sender instanceof Player reporter)) {
            messages.send(sender, "general.players-only");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "report.usage", Map.of("command", "/" + label));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
            return true;
        }
        if (target.getUniqueId().equals(reporter.getUniqueId())) {
            messages.send(sender, "report.self");
            return true;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        var entry = reports.report(target.getUniqueId(), target.getName(), reporter, reason);
        messages.send(reporter, "report.filed", Map.of(
                "player", target.getName(), "reason", reason, "id", entry.id()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player self && player.getUniqueId().equals(self.getUniqueId())) {
                continue;
            }
            names.add(player.getName());
        }
        String lower = args[0].toLowerCase(Locale.ROOT);
        names.removeIf(name -> !name.toLowerCase(Locale.ROOT).startsWith(lower));
        return names;
    }
}
