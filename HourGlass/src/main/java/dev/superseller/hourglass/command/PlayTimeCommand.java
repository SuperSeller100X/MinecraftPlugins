package dev.superseller.hourglass.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.config.Messages;
import dev.superseller.hourglass.data.Milestone;
import dev.superseller.hourglass.data.PlaytimeRecord;
import dev.superseller.hourglass.data.Session;
import dev.superseller.hourglass.gui.GuiService;
import dev.superseller.hourglass.service.Leaderboard;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /playtime} — the player-facing command.
 *
 * <p>Every sub-command has a one-or-two-letter short alias (toggle with
 * {@code commands.short-aliases}), and the no-argument form shows the metric
 * configured in {@code general.primary-metric}, so {@code /pt} alone is enough
 * for the everyday question "how long have I been here?".
 */
public final class PlayTimeCommand extends CommandBase {

    public PlayTimeCommand(HourGlassPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String helpKey() {
        return "help.player";
    }

    @Override
    protected String rootUsage() {
        return "/playtime [total|active|session|top|rank|player|history|first|milestones|gui|display|info]";
    }

    @Override
    protected void onNoArgs(CommandSender sender, String label) {
        Player player = sender instanceof Player p ? p : null;
        if (player == null) {
            // Console: show the same thing the player would see, without a GUI.
            plugin.messages().send(sender, "help.intro", Messages.ph("label", label));
            sendHelp(sender);
            return;
        }
        total(player, label);
    }

    @Override
    protected List<SubCommand> build() {
        List<SubCommand> subCommands = new ArrayList<>();

        subCommands.add(SubCommand.of("total")
                .aliases("t")
                .permission("hourglass.total")
                .usage("/playtime total")
                .playersOnly()
                .onExecute((sender, label, args) -> {
                    PlaytimeRecord record = plugin.playtime().getOrCreate(((Player) sender).getUniqueId(),
                            sender.getName());
                    plugin.messages().send(sender, "stats.total", plugin.gui().placeholdersFor(record));
                    plugin.sounds().play(sender, "command-ok");
                })
                .build());

        subCommands.add(SubCommand.of("active")
                .aliases("a")
                .permission("hourglass.active")
                .usage("/playtime active")
                .onExecute((sender, label, args) -> {
                    PlaytimeRecord record = recordFor(sender);
                    if (record == null) {
                        return;
                    }
                    plugin.messages().send(sender, "stats.active", plugin.gui().placeholdersFor(record));
                })
                .build());

        subCommands.add(SubCommand.of("session")
                .aliases("s")
                .permission("hourglass.session")
                .usage("/playtime session")
                .onExecute((sender, label, args) -> {
                    PlaytimeRecord record = recordFor(sender);
                    if (record == null) {
                        return;
                    }
                    plugin.messages().send(sender, "stats.session", plugin.gui().placeholdersFor(record));
                })
                .build());

        subCommands.add(SubCommand.of("first")
                .aliases("fi", "joined")
                .permission("hourglass.first-join")
                .usage("/playtime first [player]")
                .onExecute((sender, label, args) -> {
                    PlaytimeRecord record = args.length >= 1 && sender.hasPermission("hourglass.see.others")
                            ? requireRecord(sender, args[0], "/playtime first <player>")
                            : recordFor(sender);
                    if (record == null) {
                        return;
                    }
                    plugin.messages().send(sender, "stats.first-join", plugin.gui().placeholdersFor(record));
                })
                .onTab((sender, args, last) -> args.length <= 1 && sender.hasPermission("hourglass.see.others")
                        ? completeNames(last, plugin.config().commandsTabLimit(), false)
                        : List.of())
                .build());

        subCommands.add(SubCommand.of("top")
                .aliases("lb", "lead", "leaderboard")
                .permission("hourglass.top")
                .usage("/playtime top [page|self]")
                .onExecute((sender, label, args) -> showLeaderboard(sender, args))
                .onTab((sender, args, last) -> args.length <= 1 ? pageOptions(last) : List.of())
                .build());

        subCommands.add(SubCommand.of("rank")
                .aliases("r")
                .permission("hourglass.rank")
                .usage("/playtime rank [player]")
                .onExecute((sender, label, args) -> {
                    PlaytimeRecord record = args.length >= 1 && sender.hasPermission("hourglass.see.others")
                            ? requireRecord(sender, args[0], "/playtime rank <player>")
                            : recordFor(sender);
                    if (record == null) {
                        return;
                    }
                    plugin.messages().send(sender, "stats.rank", plugin.gui().placeholdersFor(record));
                })
                .onTab((sender, args, last) -> args.length <= 1 && sender.hasPermission("hourglass.see.others")
                        ? completeNames(last, plugin.config().commandsTabLimit(), false)
                        : List.of())
                .build());

        subCommands.add(SubCommand.of("player")
                .aliases("p", "o", "other")
                .permission("hourglass.see.others")
                .usage("/playtime player <player>")
                .onExecute((sender, label, args) -> {
                    if (args.length < 1) {
                        sendUsage(sender, "/playtime player <player>");
                        return;
                    }
                    PlaytimeRecord record = requireRecord(sender, args[0], "/playtime player <player>");
                    if (record == null) {
                        return;
                    }
                    plugin.messages().send(sender, "stats.other", plugin.gui().placeholdersFor(record));
                })
                .onTab((sender, args, last) -> args.length <= 1
                        ? completeNames(last, plugin.config().commandsTabLimit(), false)
                        : List.of())
                .build());

        subCommands.add(SubCommand.of("history")
                .aliases("h")
                .permission("hourglass.history")
                .usage("/playtime history [player] [count]")
                .onExecute((sender, label, args) -> showHistory(sender, args))
                .onTab((sender, args, last) -> args.length <= 1
                        ? completeNames(last, plugin.config().commandsTabLimit(), false)
                        : List.of("5", "10", "20"))
                .build());

        subCommands.add(SubCommand.of("milestones")
                .aliases("m")
                .permission("hourglass.milestones")
                .usage("/playtime milestones [player]")
                .onExecute((sender, label, args) -> {
                    PlaytimeRecord record = args.length >= 1 && sender.hasPermission("hourglass.see.others")
                            ? requireRecord(sender, args[0], "/playtime milestones <player>")
                            : recordFor(sender);
                    if (record == null) {
                        return;
                    }
                    showMilestones(sender, record);
                })
                .onTab((sender, args, last) -> args.length <= 1 && sender.hasPermission("hourglass.see.others")
                        ? completeNames(last, plugin.config().commandsTabLimit(), false)
                        : List.of())
                .build());

        subCommands.add(SubCommand.of("gui")
                .aliases("g", "menu")
                .permission("hourglass.gui")
                .usage("/playtime gui [stats|top|milestones]")
                .playersOnly()
                .onExecute((sender, label, args) -> {
                    String screen = args.length >= 1 ? args[0].toLowerCase() : plugin.config().guiDefaultPage();
                    String id = switch (screen) {
                        case "top", "lead", "leaderboard" -> GuiService.LEADERBOARD;
                        case "milestone", "milestones" -> GuiService.MILESTONES;
                        case "admin", "a" -> GuiService.ADMIN;
                        default -> GuiService.STATS;
                    };
                    if (GuiService.ADMIN.equals(id) && !sender.hasPermission("hourglass.admin.gui")) {
                        id = GuiService.STATS;
                    }
                    plugin.gui().open((Player) sender, id, 0, null);
                })
                .onTab((sender, args, last) -> args.length <= 1
                        ? filter(List.of("stats", "top", "milestones", "admin"), last) : List.of())
                .build());

        subCommands.add(SubCommand.of("display")
                .aliases("d")
                .permission("hourglass.display")
                .usage("/playtime display [bossbar|actionbar|off|reset]")
                .playersOnly()
                .onExecute((sender, label, args) -> {
                    Player player = (Player) sender;
                    String mode = args.length >= 1 ? args[0].toLowerCase() : "toggle";
                    PlaytimeRecord.DisplayMode applied;
                    switch (mode) {
                        case "bossbar", "bar" -> applied = plugin.display().set(player, PlaytimeRecord.DisplayMode.BOSSBAR);
                        case "actionbar", "action" ->
                                applied = plugin.display().set(player, PlaytimeRecord.DisplayMode.ACTIONBAR);
                        case "off", "none", "hide" -> applied = plugin.display().set(player, PlaytimeRecord.DisplayMode.OFF);
                        case "reset", "auto", "default" ->
                                applied = plugin.display().set(player, PlaytimeRecord.DisplayMode.INHERIT);
                        case "toggle" -> applied = plugin.display().cycle(player);
                        default -> {
                            sendUsage(sender, "/playtime display [bossbar|actionbar|off|reset|toggle]");
                            return;
                        }
                    }
                    plugin.messages().send(sender, "display.changed",
                            Messages.ph("display", plugin.display().label(applied), "player", player.getName()));
                    plugin.sounds().play(sender, applied == PlaytimeRecord.DisplayMode.OFF
                            ? "display-off" : "display-on");
                })
                .onTab((sender, args, last) -> args.length <= 1
                        ? filter(List.of("bossbar", "actionbar", "off", "reset", "toggle"), last) : List.of())
                .build());

        subCommands.add(SubCommand.of("info")
                .aliases("i", "about", "version")
                .usage("/playtime info")
                .onExecute((sender, label, args) -> plugin.messages().sendList(sender, "info", infoPlaceholders()))
                .build());

        subCommands.add(SubCommand.of("reload")
                .aliases("rl")
                .permission("hourglass.admin.reload")
                .usage("/playtime reload")
                .onExecute((sender, label, args) -> {
                    boolean ok = plugin.reload();
                    plugin.messages().send(sender, ok ? "reloaded" : "reload-failed");
                    plugin.sounds().play(sender, ok ? "command-ok" : "command-error");
                })
                .build());

        subCommands.add(SubCommand.of("help")
                .aliases("?")
                .usage("/playtime help")
                .onExecute((sender, label, args) -> sendHelp(sender))
                .build());

        return List.copyOf(subCommands);
    }

    // ------------------------------------------------------------------ parts

    private void total(Player player, String label) {
        PlaytimeRecord record = plugin.playtime().getOrCreate(player.getUniqueId(), player.getName());
        String key = plugin.config().primaryIsActive() ? "stats.active" : "stats.total";
        plugin.messages().send(player, key, plugin.gui().placeholdersFor(record));
        plugin.sounds().play(player, "command-ok");
    }

    private PlaytimeRecord recordFor(CommandSender sender) {
        if (sender instanceof Player player) {
            return plugin.playtime().getOrCreate(player.getUniqueId(), player.getName());
        }
        sendUsage(sender, rootUsage());
        return null;
    }

    private void showLeaderboard(CommandSender sender, String[] args) {
        if (!plugin.config().leaderboardEnabled()) {
            plugin.messages().send(sender, "leaderboard.disabled");
            return;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("self") || args[0].equalsIgnoreCase("me"))) {
            PlaytimeRecord record = recordFor(sender);
            if (record != null) {
                plugin.messages().send(sender, "stats.rank", plugin.gui().placeholdersFor(record));
            }
            return;
        }
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0].trim()));
            } catch (NumberFormatException e) {
                page = 1;
            }
        }
        Leaderboard board = plugin.leaderboards().current();
        if (board.isEmpty()) {
            plugin.messages().send(sender, "leaderboard.empty");
            return;
        }
        int perPage = plugin.config().leaderboardPerPage();
        int pages = board.pages(perPage);
        page = Math.min(page, pages);
        List<Leaderboard.Entry> rows = board.page(page - 1, perPage);
        Map<String, String> header = Messages.ph("page", String.valueOf(page), "pages", String.valueOf(pages),
                "entries", String.valueOf(board.size()), "metric",
                java.util.Objects.toString(Leaderboard.Metric.parse(plugin.config().leaderboardMetric(),
                        plugin.config().primaryIsActive()).name().toLowerCase(java.util.Locale.US)));
        plugin.messages().send(sender, "leaderboard.header", header);
        int rank = (page - 1) * perPage;
        Player viewer = sender instanceof Player asPlayer ? asPlayer : null;
        boolean highlight = plugin.config().leaderboardSelfHighlight() && viewer != null;
        for (Leaderboard.Entry entry : rows) {
            rank++;
            Map<String, String> line = Messages.ph("rank", String.valueOf(rank),
                    "player", entry.displayName(),
                    "time", pretty(entry.seconds()),
                    "seconds", String.valueOf(entry.seconds()));
            boolean self = highlight && viewer != null && viewer.getUniqueId().equals(entry.id());
            String key = self ? "leaderboard.line-self" : "leaderboard.line";
            if (!plugin.messages().has(key)) {
                key = "leaderboard.line";
            }
            plugin.messages().send(sender, key, line);
        }
        plugin.messages().send(sender, "leaderboard.footer", header);
    }

    private void showHistory(CommandSender sender, String[] args) {
        String name = args.length >= 1 ? args[0] : null;
        PlaytimeRecord record;
        if (name == null) {
            record = recordFor(sender);
        } else if (sender.hasPermission("hourglass.see.others")) {
            record = requireRecord(sender, name, "/playtime history <player>");
        } else {
            record = recordFor(sender);
        }
        if (record == null) {
            return;
        }
        int wanted = Integer.MAX_VALUE;
        if (args.length >= 2) {
            try {
                wanted = Math.max(1, Integer.parseInt(args[1].trim()));
            } catch (NumberFormatException ignored) {
                // a non-numeric second argument is simply ignored
            }
        }
        List<Session> history = record.history();
        if (history.isEmpty()) {
            plugin.messages().send(sender, "history.empty",
                    Messages.ph("player", record.name() == null ? "?" : record.name()));
            return;
        }
        int shown = Math.min(history.size(), wanted);
        plugin.messages().send(sender, "history.header", Messages.ph("player",
                record.name() == null ? "?" : record.name(), "count", String.valueOf(shown),
                "total", String.valueOf(history.size())));
        for (int i = 0; i < shown; i++) {
            Session session = history.get(i);
            plugin.messages().send(sender, "history.line", Messages.ph(
                    "index", String.valueOf(i + 1),
                    "start", plugin.playtime().dates().format(session.start(), "-"),
                    "end", session.running() ? "now" : plugin.playtime().dates().format(session.end(), "-"),
                    "time", pretty(session.totalSeconds()),
                    "active", pretty(session.activeSeconds()),
                    "duration", pretty(session.totalSeconds())));
        }
        plugin.messages().send(sender, "history.footer", Messages.ph("count", String.valueOf(shown),
                "total", String.valueOf(history.size()),
                "total-time", pretty(record.totalSeconds())));
    }

    private void showMilestones(CommandSender sender, PlaytimeRecord record) {
        List<Milestone> configured = plugin.config().milestones();
        if (configured.isEmpty()) {
            plugin.messages().send(sender, "milestones.empty");
            return;
        }
        plugin.messages().send(sender, "milestones.header", Messages.ph(
                "player", record.name() == null ? "?" : record.name(),
                "reached", String.valueOf(plugin.milestones().reached(record)),
                "total", String.valueOf(configured.size())));
        for (Milestone milestone : configured) {
            boolean reached = record.awardedMilestones().contains(milestone.name());
            double progress = reached ? 1.0d : plugin.milestones().progress(record, milestone);
            plugin.messages().send(sender, reached ? "milestones.line-reached" : "milestones.line", Messages.ph(
                    "milestone", milestone.name(),
                    "threshold", pretty(milestone.seconds()),
                    "percent", String.valueOf((int) Math.round(progress * 100.0d)),
                    "bar", progressBar(progress),
                    "state", reached ? "done" : "open",
                    "remaining", reached ? "-" : pretty(Math.max(0L,
                            milestone.seconds() - plugin.playtime().metric(record,
                                    plugin.config().milestoneMetric(), System.currentTimeMillis())))));
        }
    }

    private String progressBar(double progress) {
        int length = plugin.config().progressBarLength();
        int filled = (int) Math.round(Math.max(0.0d, Math.min(1.0d, progress)) * length);
        return plugin.config().progressBarFilled().repeat(Math.max(0, filled))
                + plugin.config().progressBarEmpty().repeat(Math.max(0, length - filled));
    }

    private Map<String, String> infoPlaceholders() {
        return Messages.ph(
                "version", plugin.getDescription().getVersion(),
                "players", String.valueOf(plugin.playtime().size()),
                "tracked", String.valueOf(plugin.tracking().trackedCount()),
                "metric", plugin.config().primaryIsActive() ? "active" : "total",
                "storage", "yaml",
                "leaderboard", String.valueOf(plugin.config().leaderboardEnabled()),
                "milestones", String.valueOf(plugin.config().milestones().size()),
                "display", plugin.config().displayMode(),
                "format", plugin.config().timeFormat().settings().style().name().toLowerCase(java.util.Locale.US),
                "server", plugin.getServer().getVersion());
    }

    private static List<String> pageOptions(String last) {
        return filter(List.of("1", "2", "3", "self"), last);
    }

    private static List<String> filter(List<String> options, String token) {
        String prefix = token == null ? "" : token.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }
}
