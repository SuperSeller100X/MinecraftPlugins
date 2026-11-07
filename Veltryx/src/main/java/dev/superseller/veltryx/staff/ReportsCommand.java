package dev.superseller.veltryx.staff;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.model.ReportEntry;
import dev.superseller.veltryx.util.TimeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * {@code /reports [page]} — review open player reports.
 * {@code /reports resolve <id>} — mark one handled.
 */
public final class ReportsCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 8;

    private final Messages messages;
    private final ReportService reports;

    public ReportsCommand(Messages messages, ReportService reports) {
        this.messages = messages;
        this.reports = reports;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veltryx.moderation.reports")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("resolve")) {
            var resolved = reports.resolve(args[1]);
            if (resolved.isEmpty()) {
                messages.send(sender, "reports.not-found", Map.of("input", args[1]));
            } else {
                messages.send(sender, "reports.resolved", Map.of(
                        "id", resolved.get().id(),
                        "player", resolved.get().targetName()));
            }
            return true;
        }
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                messages.send(sender, "reports.usage", Map.of("command", "/" + label));
                return true;
            }
        }
        List<ReportEntry> open = reports.openReports();
        if (open.isEmpty()) {
            messages.send(sender, "reports.none");
            return true;
        }
        int pages = (open.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.min(page, pages);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(open.size(), start + PAGE_SIZE);
        messages.send(sender, "reports.header", Map.of(
                "page", String.valueOf(page),
                "pages", String.valueOf(pages),
                "count", String.valueOf(open.size())));
        for (ReportEntry entry : open.subList(start, end)) {
            sender.sendMessage(messages.render("reports.entry", Map.of(
                    "id", entry.id(),
                    "player", entry.targetName(),
                    "reporter", entry.reporterName(),
                    "reason", entry.reason(),
                    "issued", TimeUtil.formatTimestamp(entry.issued()))));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("resolve"));
            String lower = args[0].toLowerCase(Locale.ROOT);
            options.removeIf(option -> !option.startsWith(lower));
            return options;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resolve")) {
            List<String> ids = new ArrayList<>();
            for (ReportEntry entry : reports.openReports()) {
                ids.add(entry.id());
            }
            String lower = args[1].toLowerCase(Locale.ROOT);
            ids.removeIf(id -> !id.toLowerCase(Locale.ROOT).startsWith(lower));
            return ids;
        }
        return List.of();
    }
}
