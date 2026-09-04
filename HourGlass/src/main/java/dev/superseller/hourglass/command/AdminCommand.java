package dev.superseller.hourglass.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.config.Messages;
import dev.superseller.hourglass.data.PlaytimeRecord;
import dev.superseller.hourglass.gui.GuiService;
import dev.superseller.hourglass.service.Leaderboard;
import dev.superseller.hourglass.service.PlaytimeService;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /playtimeadmin} — everything staff need: read, correct, freeze and
 * clean up tracked playtime, plus reload, stats, CSV export and the admin GUI.
 *
 * <p>Destructive actions are two-step by design: {@code purge} runs as a
 * preview unless {@code confirm} is given, and {@code reset} always reports what
 * it removed. All of it is permission-gated per action, not just on the root
 * command, so a server can give a build team {@code hourglass.admin.info}
 * without handing them the delete button.
 */
public final class AdminCommand extends CommandBase {

    public AdminCommand(HourGlassPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String helpKey() {
        return "help.admin";
    }

    @Override
    protected String rootUsage() {
        return "/playtimeadmin [info|set|add|remove|reset|freeze|list|top|purge|export|stats|gui|reload]";
    }

    @Override
    protected void onNoArgs(CommandSender sender, String label) {
        sendHelp(sender);
    }

    @Override
    protected List<SubCommand> build() {
        List<SubCommand> subCommands = new ArrayList<>();

        subCommands.add(SubCommand.of("info")
                .aliases("i", "inspect")
                .permission("hourglass.admin.info")
                .usage("/playtimeadmin info <player>")
                .onExecute((sender, label, args) -> {
                    if (args.length < 1) {
                        sendUsage(sender, "/playtimeadmin info <player>");
                        return;
                    }
                    PlaytimeRecord record = requireRecord(sender, args[0], "/playtimeadmin info <player>");
                    if (record == null) {
                        return;
                    }
                    plugin.messages().sendList(sender, "admin.info", plugin.gui().placeholdersFor(record));
                })
                .onTab((sender, args, last) -> args.length <= 1
                        ? completeNames(last, plugin.config().commandsTabLimit(), false) : List.of())
                .build());

        subCommands.add(SubCommand.of("set")
                .aliases("s")
                .permission("hourglass.admin.edit")
                .usage("/playtimeadmin set <player> <duration>")
                .onExecute((sender, label, args) -> edit(sender, args, "set", "/playtimeadmin set <player> <time>"))
                .onTab(this::editTab)
                .build());

        subCommands.add(SubCommand.of("add")
                .aliases("a", "give")
                .permission("hourglass.admin.edit")
                .usage("/playtimeadmin add <player> <duration>")
                .onExecute((sender, label, args) -> edit(sender, args, "add", "/playtimeadmin add <player> <time>"))
                .onTab(this::editTab)
                .build());

        subCommands.add(SubCommand.of("remove")
                .aliases("rm", "take")
                .permission("hourglass.admin.edit")
                .usage("/playtimeadmin remove <player> <duration>")
                .onExecute((sender, label, args) ->
                        edit(sender, args, "remove", "/playtimeadmin remove <player> <time>"))
                .onTab(this::editTab)
                .build());

        subCommands.add(SubCommand.of("reset")
                .aliases("rs", "clear")
                .permission("hourglass.admin.edit")
                .usage("/playtimeadmin reset <player>")
                .onExecute((sender, label, args) -> {
                    if (args.length < 1) {
                        sendUsage(sender, "/playtimeadmin reset <player>");
                        return;
                    }
                    PlaytimeRecord record = requireRecord(sender, args[0], "/playtimeadmin reset <player>");
                    if (record == null) {
                        return;
                    }
                    String before = pretty(record.totalSeconds());
                    long total = record.totalSeconds();
                    record.reset(plugin.config().resetKeepsFirstJoin());
                    plugin.playtime().persistNow(record);
                    plugin.messages().send(sender, "admin.reset-done", Messages.ph(
                            "player", record.name() == null ? "?" : record.name(),
                            "removed", before,
                            "removed-seconds", String.valueOf(total)));
                    plugin.sounds().play(sender, "admin-reset");
                    plugin.leaderboards().rebuild();
                })
                .onTab((sender, args, last) -> args.length <= 1
                        ? completeNames(last, plugin.config().commandsTabLimit(), false) : List.of())
                .build());

        subCommands.add(SubCommand.of("freeze")
                .aliases("f", "pause")
                .permission("hourglass.admin.freeze")
                .usage("/playtimeadmin freeze <player> [on|off|toggle]")
                .onExecute((sender, label, args) -> {
                    if (args.length < 1) {
                        sendUsage(sender, "/playtimeadmin freeze <player> [on|off|toggle]");
                        return;
                    }
                    PlaytimeRecord record = requireRecord(sender, args[0], "/playtimeadmin freeze <player>");
                    if (record == null) {
                        return;
                    }
                    String mode = args.length >= 2 ? args[1].toLowerCase(Locale.US) : "toggle";
                    boolean wanted = switch (mode) {
                        case "on", "true", "yes", "start" -> true;
                        case "off", "false", "no", "stop", "resume" -> false;
                        default -> !record.frozen();
                    };
                    record.frozen(wanted);
                    plugin.playtime().persist(record);
                    plugin.messages().send(sender, wanted ? "admin.freeze-on" : "admin.freeze-off",
                            Messages.ph("player", record.name() == null ? "?" : record.name(),
                                    "time", pretty(record.totalSeconds())));
                    plugin.sounds().play(sender, "admin-edit");
                })
                .onTab(this::freezeTab)
                .build());

        subCommands.add(SubCommand.of("list")
                .aliases("l", "online")
                .permission("hourglass.admin.list")
                .usage("/playtimeadmin list [page]")
                .onExecute((sender, label, args) -> list(sender, args))
                .onTab((sender, args, last) -> args.length <= 1 ? filter(List.of("1", "2", "3"), last) : List.of())
                .build());

        subCommands.add(SubCommand.of("top")
                .aliases("t", "leaderboard")
                .permission("hourglass.admin.leaderboard")
                .usage("/playtimeadmin top [refresh|page]")
                .onExecute((sender, label, args) -> {
                    if (args.length >= 1 && (args[0].equalsIgnoreCase("refresh") || args[0].equalsIgnoreCase("r"))) {
                        Leaderboard board = plugin.leaderboards().rebuild();
                        plugin.messages().send(sender, "admin.top-refreshed", Messages.ph(
                                "entries", String.valueOf(board.size()),
                                "builds", String.valueOf(plugin.leaderboards().builds())));
                        plugin.sounds().play(sender, "command-ok");
                        return;
                    }
                    plugin.leaderboards().rebuild();
                    showTopPage(sender, args);
                })
                .onTab((sender, args, last) -> args.length <= 1
                        ? filter(List.of("refresh", "1", "2", "3"), last) : List.of())
                .build());

        subCommands.add(SubCommand.of("purge")
                .aliases("p")
                .permission("hourglass.admin.purge")
                .usage("/playtimeadmin purge [days] [confirm]")
                .onExecute((sender, label, args) -> purge(sender, args))
                .onTab((sender, args, last) -> args.length <= 1
                        ? filter(List.of(String.valueOf(plugin.config().purgeDefaultDays()), "30", "90", "180", "365"), last)
                        : (args.length == 2 ? filter(List.of("confirm"), last) : List.of()))
                .build());

        subCommands.add(SubCommand.of("export")
                .aliases("e", "csv")
                .permission("hourglass.admin.export")
                .usage("/playtimeadmin export")
                .onExecute((sender, label, args) -> {
                    try {
                        PlaytimeService.ExportResult result = plugin.playtime().exportCsv();
                        plugin.messages().send(sender, "admin.export-done", Messages.ph(
                                "file", result.file().getFileName().toString(),
                                "path", result.file().toString(),
                                "rows", String.valueOf(result.rows())));
                        plugin.sounds().play(sender, "admin-export");
                    } catch (Exception e) {
                        plugin.messages().send(sender, "admin.export-failed",
                                Messages.ph("error", String.valueOf(e.getMessage())));
                        plugin.sounds().play(sender, "command-error");
                    }
                })
                .build());

        subCommands.add(SubCommand.of("stats")
                .aliases("st", "status")
                .permission("hourglass.admin.stats")
                .usage("/playtimeadmin stats")
                .onExecute((sender, label, args) ->
                        plugin.messages().sendList(sender, "admin.stats", statsPlaceholders()))
                .build());

        subCommands.add(SubCommand.of("gui")
                .aliases("g", "menu")
                .permission("hourglass.admin.gui")
                .usage("/playtimeadmin gui")
                .playersOnly()
                .onExecute((sender, label, args) -> {
                    String wanted = args.length >= 1 ? args[0].toLowerCase(Locale.US) : "admin";
                    String screen = switch (wanted) {
                        case "leaderboard", "top" -> GuiService.LEADERBOARD;
                        case "stats", "me" -> GuiService.STATS;
                        case "milestones" -> GuiService.MILESTONES;
                        default -> GuiService.ADMIN;
                    };
                    plugin.gui().open((Player) sender, screen, 0, null);
                })
                .onTab((sender, args, last) -> args.length <= 1
                        ? filter(List.of("admin", "leaderboard", "stats", "milestones"), last) : List.of())
                .build());

        subCommands.add(SubCommand.of("reload")
                .aliases("rl")
                .permission("hourglass.admin.reload")
                .usage("/playtimeadmin reload")
                .onExecute((sender, label, args) -> {
                    long started = System.currentTimeMillis();
                    boolean ok = plugin.reload();
                    plugin.messages().send(sender, ok ? "reloaded" : "reload-failed", Messages.ph(
                            "files", "config.yml, messages.yml, gui.yml",
                            "ms", String.valueOf(System.currentTimeMillis() - started)));
                    plugin.sounds().play(sender, ok ? "command-ok" : "command-error");
                })
                .build());

        subCommands.add(SubCommand.of("help")
                .aliases("?")
                .usage("/playtimeadmin help")
                .onExecute((sender, label, args) -> sendHelp(sender))
                .build());

        return List.copyOf(subCommands);
    }

    // ------------------------------------------------------------------ parts

    private void edit(CommandSender sender, String[] args, String mode, String usage) {
        if (args.length < 2) {
            sendUsage(sender, usage);
            return;
        }
        PlaytimeRecord record = requireRecord(sender, args[0], usage);
        if (record == null) {
            return;
        }
        long seconds = requireDuration(sender, args[1], usage);
        boolean alsoActive = plugin.config().adminEditsCountAsActive();
        long before = record.totalSeconds();
        String name = record.name() == null ? "?" : record.name();
        switch (mode) {
            case "set" -> record.setTotalSeconds(seconds);
            case "add" -> record.shiftSeconds(seconds, alsoActive);
            default -> record.shiftSeconds(-seconds, alsoActive);
        }
        plugin.playtime().persistNow(record);
        Map<String, String> placeholders = Messages.ph(
                "player", name,
                "amount", pretty(seconds),
                "amount-seconds", String.valueOf(seconds),
                "time", pretty(record.totalSeconds()),
                "old", pretty(before),
                "active", pretty(record.activeSeconds()));
        plugin.messages().send(sender, switch (mode) {
            case "set" -> "admin.set-done";
            case "add" -> "admin.add-done";
            default -> "admin.remove-done";
        }, placeholders);
        plugin.sounds().play(sender, "admin-edit");
        plugin.milestones().check(record);
        plugin.leaderboards().rebuild();
        if (plugin.config().debug()) {
            plugin.getLogger().info("[admin] " + mode + " " + name + " " + seconds + "s -> "
                    + record.totalSeconds() + "s");
        }
    }

    private List<String> editTab(org.bukkit.command.CommandSender sender, String[] args, String last) {
        if (args.length <= 1) {
            return completeNames(last, plugin.config().commandsTabLimit(), false);
        }
        if (args.length == 2) {
            return filter(List.of("1h", "1d", "1w", "30m", "10h", "100h", "1000h"), last);
        }
        return List.of();
    }

    private List<String> freezeTab(org.bukkit.command.CommandSender sender, String[] args, String last) {
        if (args.length <= 1) {
            return completeNames(last, plugin.config().commandsTabLimit(), false);
        }
        return args.length == 2 ? filter(List.of("on", "off", "toggle"), last) : List.of();
    }

    private void list(CommandSender sender, String[] args) {
        List<PlaytimeRecord> online = plugin.playtime().online();
        if (online.isEmpty()) {
            plugin.messages().send(sender, "admin.list-empty");
            return;
        }
        online.sort((a, b) -> Long.compare(b.totalSeconds(), a.totalSeconds()));
        int perPage = Math.max(1, plugin.config().leaderboardPerPage());
        int pages = (online.size() + perPage - 1) / perPage;
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0].trim()));
            } catch (NumberFormatException ignored) {
                // non-numeric page argument: stay on page 1
            }
        }
        page = Math.min(page, pages);
        Map<String, String> header = Messages.ph("page", String.valueOf(page), "pages", String.valueOf(pages),
                "count", String.valueOf(online.size()));
        plugin.messages().send(sender, "admin.list-header", header);
        long now = System.currentTimeMillis();
        for (int i = (page - 1) * perPage; i < Math.min(page * perPage, online.size()); i++) {
            PlaytimeRecord record = online.get(i);
            plugin.messages().send(sender, "admin.list-line", Messages.ph(
                    "index", String.valueOf(i + 1),
                    "player", record.name() == null ? "?" : record.name(),
                    "total", pretty(record.totalSeconds()),
                    "active", pretty(record.activeSeconds()),
                    "session", pretty(record.sessionTotalSeconds(now)),
                    "idle", plugin.tracking().idle(record.id()) ? "idle" : "active",
                    "frozen", record.frozen() ? "frozen" : "tracking"));
        }
        plugin.messages().send(sender, "admin.list-footer", header);
    }

    private void showTopPage(CommandSender sender, String[] args) {
        Leaderboard board = plugin.leaderboards().current();
        if (board.isEmpty()) {
            plugin.messages().send(sender, "leaderboard.empty");
            return;
        }
        int perPage = plugin.config().leaderboardPerPage();
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0].trim()));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        page = Math.min(page, board.pages(perPage));
        Map<String, String> header = Messages.ph("page", String.valueOf(page), "pages",
                String.valueOf(board.pages(perPage)), "entries", String.valueOf(board.size()),
                "metric", plugin.config().leaderboardMetric());
        plugin.messages().send(sender, "leaderboard.header", header);
        int rank = (page - 1) * perPage;
        for (Leaderboard.Entry entry : board.page(page - 1, perPage)) {
            rank++;
            plugin.messages().send(sender, "leaderboard.line", Messages.ph("rank", String.valueOf(rank),
                    "player", entry.displayName(), "time", pretty(entry.seconds()),
                    "seconds", String.valueOf(entry.seconds())));
        }
        plugin.messages().send(sender, "leaderboard.footer", header);
    }

    private void purge(CommandSender sender, String[] args) {
        int days = plugin.config().purgeDefaultDays();
        if (args.length >= 1) {
            try {
                days = Math.max(1, Integer.parseInt(args[0].trim()));
            } catch (NumberFormatException e) {
                Long parsed = optionalDuration(args[0]);
                if (parsed == null || parsed <= 0L) {
                    sendUsage(sender, "/playtimeadmin purge [days] [confirm]");
                    return;
                }
                days = (int) Math.max(1L, parsed / 86_400L);
            }
        }
        boolean confirm = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
        long cutoff = System.currentTimeMillis() - days * 86_400L;
        List<PlaytimeRecord> affected = plugin.playtime().purgeInactive(cutoff, !confirm);
        Map<String, String> placeholders = Messages.ph("count", String.valueOf(affected.size()),
                "days", String.valueOf(days));
        if (affected.isEmpty()) {
            plugin.messages().send(sender, "admin.purge-none", placeholders);
            return;
        }
        if (!confirm) {
            plugin.messages().send(sender, "admin.purge-preview", placeholders);
            plugin.sounds().play(sender, "command-error");
            return;
        }
        plugin.messages().send(sender, "admin.purge-done", placeholders);
        plugin.sounds().play(sender, "admin-purge");
        plugin.leaderboards().rebuild();
    }

    private Map<String, String> statsPlaceholders() {
        Map<String, String> map = new java.util.HashMap<>(plugin.playtime().storagePlaceholders());
        map.put("version", plugin.getDescription().getVersion());
        map.put("java", System.getProperty("java.version", "?"));
        map.put("server", plugin.getServer().getVersion());
        map.put("folia", String.valueOf(dev.superseller.hourglass.scheduler.PlatformScheduler.isFolia()));
        map.put("metric", plugin.config().primaryIsActive() ? "active" : "total");
        map.put("format", plugin.config().timeFormat().settings().style().name().toLowerCase(Locale.US));
        map.put("tracking", String.valueOf(plugin.tracking().trackedCount()));
        map.put("leaderboard-entries", String.valueOf(plugin.leaderboards().current().size()));
        map.put("leaderboard-builds", String.valueOf(plugin.leaderboards().builds()));
        map.put("leaderboard-refresh", plugin.playtime().dates()
                .format(plugin.leaderboards().lastRefreshMillis(), "never"));
        map.put("milestones", String.valueOf(plugin.config().milestones().size()));
        map.put("milestone-awards", String.valueOf(plugin.milestones().awardedTotal()));
        map.put("sounds", String.valueOf(plugin.sounds().enabledCount()));
        map.put("display", plugin.config().displayMode());
        map.put("autosave", String.valueOf(plugin.config().autosaveMinutes()));
        map.put("idle-seconds", String.valueOf(plugin.config().idleMillis() / 1_000L));
        map.put("last-write", plugin.playtime().dates()
                .format(plugin.playtime().storage().lastWriteMillis(), "never"));
        return map;
    }

    private static List<String> filter(List<String> options, String token) {
        String prefix = token == null ? "" : token.toLowerCase(Locale.US);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.US).startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }
}
