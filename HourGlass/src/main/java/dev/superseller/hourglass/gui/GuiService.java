package dev.superseller.hourglass.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.config.GuiConfig;
import dev.superseller.hourglass.config.HourGlassConfig;
import dev.superseller.hourglass.config.Messages;
import dev.superseller.hourglass.data.Milestone;
import dev.superseller.hourglass.data.PlaytimeRecord;
import dev.superseller.hourglass.service.Leaderboard;
import dev.superseller.hourglass.service.PlaytimeService;
import dev.superseller.hourglass.util.Dates;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Builds and re-renders every screen defined in {@code gui.yml}
 * ({@code stats}, {@code leaderboard}, {@code milestones}, {@code admin},
 * {@code admin-player}).
 *
 * <p>Nothing about a screen is hard-coded except what a button <i>does</i>:
 * titles, sizes, materials, slots, names, lore and the action bound to each slot
 * all come from config, so the interface can be completely re-skinned without a
 * rebuild. Every inventory is tagged with a {@link GuiHolder}, which is how the
 * click listener recognises its own windows and knows what a slot means.
 */
public final class GuiService {

    /** Screen ids, mirrored in {@code gui.yml}. */
    public static final String STATS = "stats";
    public static final String LEADERBOARD = "leaderboard";
    public static final String MILESTONES = "milestones";
    public static final String ADMIN = "admin";
    public static final String ADMIN_PLAYER = "admin-player";

    private final HourGlassPlugin plugin;

    public GuiService(HourGlassPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ click

    /**
     * Entry point for the click listener: plays the configured click sound and
     * runs whatever the clicked slot is bound to. The action runs on the
     * viewer's region thread, which is what Folia requires before a GUI or a
     * sound is touched, and a throwing action is logged instead of taking the
     * server down with it.
     */
    public void handleClick(Player viewer, GuiHolder holder, int slot, ClickType click) {
        if (viewer == null || holder == null) {
            return;
        }
        GuiHolder.ClickAction action = holder.action(slot);
        plugin.sounds().play(viewer, action == null ? "gui-error" : "gui-click");
        if (action == null) {
            return;
        }
        dev.superseller.hourglass.scheduler.PlatformScheduler.runForPlayer(viewer, () -> {
            try {
                action.run(viewer, click);
            } catch (RuntimeException e) {
                plugin.getLogger().severe("GUI action for slot " + slot + " in " + holder.screenId()
                        + " failed: " + e);
                plugin.messages().send(viewer, "internal-error");
            }
        });
    }

    // ------------------------------------------------------------------ open

    /** Opens a screen for a viewer, choosing a sensible starting page. */
    public void open(Player viewer, String screenId) {
        open(viewer, screenId, 0, null);
    }

    /**
     * Opens a screen.
     *
     * @param target the player the screen is about, {@code null} for "the viewer"
     */
    public void open(Player viewer, String screenId, int page, UUID target) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        String id = normalize(screenId);
        GuiConfig.Screen screen = plugin.guiConfig().screen(id);
        UUID subject = target != null ? target : viewer.getUniqueId();
        PlaytimeRecord record = plugin.playtime().getOrCreate(subject, nameOf(subject));
        Map<String, String> placeholders = placeholders(viewer, record, page, 1);

        InventoryHolder holder = new GuiHolder(id, viewer.getUniqueId(), page);
        ((GuiHolder) holder).target(target);
        Inventory inventory = Bukkit.createInventory(holder, screen.size(),
                plugin.messages().render(screen.title(), placeholders));
        ((GuiHolder) holder).inventory(inventory);
        render((GuiHolder) holder, viewer, screen, record, page, target, placeholders);

        viewer.openInventory(inventory);
        plugin.sounds().play(viewer, "gui-open");
    }

    /** Opens the screen about another player that an admin is inspecting. */
    public void openAdminPlayer(Player viewer, UUID target) {
        open(viewer, ADMIN_PLAYER, 0, target);
    }

    /** Rebuilds the screen a player is looking at, e.g. after an edit. */
    public void refresh(Player viewer) {
        if (viewer == null) {
            return;
        }
        InventoryView view = viewer.getOpenInventory();
        Inventory top = view == null ? null : view.getTopInventory();
        if (!(top != null && top.getHolder() instanceof GuiHolder holder)
                || !viewer.getUniqueId().equals(holder.viewer())) {
            return;
        }
        open(viewer, holder.screenId(), holder.page(), holder.target());
    }

    private String normalize(String screenId) {
        if (screenId == null || screenId.isBlank()) {
            return STATS;
        }
        String id = screenId.trim().toLowerCase(Locale.US);
        return GuiConfig.SCREENS.contains(id) ? id : STATS;
    }

    // ---------------------------------------------------------------- render

    private void render(GuiHolder holder, Player viewer, GuiConfig.Screen screen, PlaytimeRecord record,
                        int page, UUID target, Map<String, String> placeholders) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        if (screen.fill()) {
            for (int slot = 0; slot < screen.size(); slot++) {
                inventory.setItem(slot, GuiItems.filler(screen));
            }
        }
        switch (holder.screenId()) {
            case LEADERBOARD -> renderLeaderboard(holder, viewer, screen, page, placeholders);
            case MILESTONES -> renderMilestones(holder, viewer, screen, record, page, placeholders);
            case ADMIN -> renderAdmin(holder, viewer, screen, page, placeholders);
            case ADMIN_PLAYER -> renderAdminPlayer(holder, viewer, screen, record, placeholders);
            default -> renderStats(holder, viewer, screen, record, page, placeholders);
        }
    }

    /** A normal screen: every configured icon is placed and wired to its action. */
    private void placeStaticItems(GuiHolder holder, Player viewer, GuiConfig.Screen screen, int page,
                                   UUID target, Map<String, String> placeholders) {
        for (GuiConfig.Item item : screen.items().values()) {
            if ("entry".equals(item.key())) {
                continue;
            }
            int slot = item.slot();
            if (slot < 0 || slot >= screen.size()) {
                continue;
            }
            UUID headOf = item.playerHead() ? (target != null ? target : viewer.getUniqueId()) : null;
            holder.getInventory().setItem(slot, GuiItems.build(plugin, item, placeholders, headOf));
            bindAction(holder, viewer, screen, item, slot, page, target);
        }
    }

    /** Wires one slot to the action configured on its item. */
    private void bindAction(GuiHolder holder, Player viewer, GuiConfig.Screen screen, GuiConfig.Item item,
                             int slot, int page, UUID target) {
        if (item.action() == null || item.action().equals("none")) {
            return;
        }
        holder.bind(slot, (clicker, click) -> action(clicker, click, screen.id(), item, page, target));
    }

    // ------------------------------------------------------------ stat screen

    private void renderStats(GuiHolder holder, Player viewer, GuiConfig.Screen screen, PlaytimeRecord record,
                             int page, Map<String, String> placeholders) {
        if (record != null) {
            placeholders.putAll(snapshotPlaceholders(record));
        }
        placeStaticItems(holder, viewer, screen, page, holder.target(), placeholders);
    }

    // ---------------------------------------------------------- leaderboard

    private void renderLeaderboard(GuiHolder holder, Player viewer, GuiConfig.Screen screen, int page,
                                   Map<String, String> placeholders) {
        HourGlassConfig config = plugin.config();
        Leaderboard board = config.leaderboardEnabled() ? plugin.leaderboards().current() : Leaderboard.empty();
        int perPage = Math.max(1, screen.listCellsForSize() > 0 ? screen.listCellsForSize() : config.leaderboardPerPage());
        int pages = Math.max(1, board.pages(perPage));
        int clamped = Math.min(Math.max(0, page), pages - 1);
        placeholders.put("pages", String.valueOf(pages));
        placeholders.put("page", String.valueOf(clamped + 1));
        placeholders.put("entries", String.valueOf(board.size()));
        placeholders.put("rank", String.valueOf(board.rankOf(viewer.getUniqueId())));

        GuiConfig.Item template = screen.listTemplate();
        int start = Math.max(0, screen.listStartOrDefault());
        List<Leaderboard.Entry> rows = board.page(clamped, perPage);
        for (int i = 0; i < rows.size(); i++) {
            int slot = start + i;
            if (slot >= screen.size()) {
                break;
            }
            Leaderboard.Entry entry = rows.get(i);
            Map<String, String> row = new java.util.HashMap<>(placeholders);
            row.put("rank", String.valueOf(clamped * perPage + i + 1));
            row.put("player", entry.displayName());
            row.put("time", config.timeFormat().format(entry.seconds()));
            row.put("total", config.timeFormat().format(entry.seconds()));
            row.put("self", viewer.getUniqueId().equals(entry.id()) ? "true" : "false");
            PlaytimeRecord rowRecord = plugin.playtime().of(entry.id());
            if (rowRecord != null) {
                row.put("active", config.timeFormat().format(rowRecord.activeSeconds()));
                row.put("online", rowRecord.online() ? "yes" : "no");
                row.put("last_seen", dates().format(rowRecord.lastSeen(), "never"));
            }
            GuiConfig.Item item = template != null ? template
                    : new GuiConfig.Item("entry", slot, Material.PLAYER_HEAD, true,
                            "<white>{player}</white>", List.of(), "view-player", 0L, null);
            boolean heads = config.guiLeaderboardHeads() && item.playerHead();
            holder.getInventory().setItem(slot, GuiItems.build(plugin, item, row, heads ? entry.id() : null));
            bindAction(holder, viewer, screen, item, slot, clamped, entry.id());
        }
        placeStaticItems(holder, viewer, screen, clamped, null, placeholders);
    }

    // ----------------------------------------------------------- milestones

    private void renderMilestones(GuiHolder holder, Player viewer, GuiConfig.Screen screen,
                                  PlaytimeRecord record, int page, Map<String, String> placeholders) {
        List<Milestone> configured = plugin.config().milestones();
        int perPage = Math.max(1, screen.listCellsForSize());
        int pages = Math.max(1, (configured.size() + perPage - 1) / perPage);
        int clamped = Math.min(Math.max(0, page), pages - 1);
        placeholders.put("pages", String.valueOf(pages));
        placeholders.put("page", String.valueOf(clamped + 1));
        placeholders.put("milestones", String.valueOf(configured.size()));
        if (record != null) {
            placeholders.put("reached", String.valueOf(plugin.milestones().reached(record)));
        }

        GuiConfig.Item template = screen.listTemplate();
        int start = Math.max(0, screen.listStartOrDefault());
        int index = clamped * perPage;
        for (int i = 0; i < perPage && index + i < configured.size(); i++) {
            Milestone milestone = configured.get(index + i);
            int slot = start + i;
            if (slot >= screen.size()) {
                break;
            }
            boolean reached = record != null && record.awardedMilestones().contains(milestone.name());
            Map<String, String> row = new java.util.HashMap<>(placeholders);
            row.put("milestone", milestone.name());
            row.put("threshold", plugin.config().timeFormat().format(milestone.seconds()));
            row.put("reached", reached ? "yes" : "no");
            row.put("percent", percent(reached ? 1.0d : plugin.milestones().progress(record, milestone)));
            row.put("bar", bar(reached ? 1.0d : plugin.milestones().progress(record, milestone)));
            boolean reachedStyle = reached && template != null;
            GuiConfig.Item item = template == null
                    ? new GuiConfig.Item("entry", slot, reached ? Material.EMERALD : Material.GRAY_DYE, false,
                            "<white>{milestone}</white>", List.of(), "none", 0L, null)
                    : withMaterial(template, reachedStyle ? template.material() : fallbackUnreached(template));
            holder.getInventory().setItem(slot, GuiItems.build(plugin, item, row, null));
            bindAction(holder, viewer, screen, item, slot, clamped, null);
        }
        placeStaticItems(holder, viewer, screen, clamped, null, placeholders);
    }

    private static GuiConfig.Item withMaterial(GuiConfig.Item item, Material material) {
        return new GuiConfig.Item(item.key(), item.slot(), material, item.playerHead(), item.name(),
                item.lore(), item.action(), item.seconds(), item.target());
    }

    private Material fallbackUnreached(GuiConfig.Item template) {
        Material gray = Material.matchMaterial("GRAY_DYE");
        return gray == null ? template.material() : gray;
    }

    private String bar(double progress) {
        int length = plugin.config().progressBarLength();
        int filled = (int) Math.round(Math.max(0.0d, Math.min(1.0d, progress)) * length);
        StringBuilder sb = new StringBuilder();
        String on = "<green>";
        String off = "<dark_gray>";
        sb.append(on);
        sb.append(plugin.config().progressBarFilled().repeat(Math.max(0, filled)));
        sb.append(off);
        sb.append(plugin.config().progressBarEmpty().repeat(Math.max(0, length - filled)));
        return sb.toString();
    }

    private static String percent(double progress) {
        return String.format(Locale.US, "%.0f%%", Math.max(0.0d, Math.min(1.0d, progress)) * 100.0d);
    }

    // ---------------------------------------------------------- admin panel

    private void renderAdmin(GuiHolder holder, Player viewer, GuiConfig.Screen screen, int page,
                             Map<String, String> placeholders) {
        List<PlaytimeRecord> online = plugin.playtime().online();
        online.sort((a, b) -> Long.compare(b.totalSeconds(), a.totalSeconds()));
        int perPage = Math.max(1, screen.listCellsForSize());
        int pages = Math.max(1, (online.size() + perPage - 1) / perPage);
        int clamped = Math.min(Math.max(0, page), pages - 1);
        Map<String, String> stats = plugin.playtime().storagePlaceholders();
        placeholders.putAll(stats);
        placeholders.put("pages", String.valueOf(pages));
        placeholders.put("page", String.valueOf(clamped + 1));
        placeholders.put("tracked", String.valueOf(plugin.tracking().trackedCount()));
        placeholders.put("entries", String.valueOf(online.size()));
        placeholders.put("milestone-awards", String.valueOf(plugin.milestones().awardedTotal()));
        placeholders.put("display-mode", plugin.config().displayMode());

        GuiConfig.Item template = screen.listTemplate();
        int start = Math.max(0, screen.listStartOrDefault());
        for (int i = 0; i < perPage; i++) {
            int index = clamped * perPage + i;
            int slot = start + i;
            if (index >= online.size() || slot >= screen.size()) {
                continue;
            }
            PlaytimeRecord record = online.get(index);
            Map<String, String> row = new java.util.HashMap<>(placeholders);
            row.putAll(snapshotPlaceholders(record));
            row.put("player", record.name() == null ? "?" : record.name());
            GuiConfig.Item item = template == null
                    ? new GuiConfig.Item("entry", slot, Material.PLAYER_HEAD, true,
                            "<white>{player}</white>", List.of(), "view-player", 0L, null)
                    : template;
            holder.getInventory().setItem(slot, GuiItems.build(plugin, item, row, item.playerHead() ? record.id() : null));
            UUID targetId = record.id();
            holder.bind(slot, (clicker, click) -> open(clicker, ADMIN_PLAYER, 0, targetId));
        }
        placeStaticItems(holder, viewer, screen, clamped, null, placeholders);
    }

    private void renderAdminPlayer(GuiHolder holder, Player viewer, GuiConfig.Screen screen,
                                   PlaytimeRecord record, Map<String, String> placeholders) {
        if (record != null) {
            placeholders.putAll(snapshotPlaceholders(record));
        }
        placeholders.put("frozen", record != null && record.frozen() ? "yes" : "no");
        List<Integer> taken = new ArrayList<>();
        placeStaticItems(holder, viewer, screen, 0, record == null ? null : record.id(), placeholders);
    }

    // ---------------------------------------------------------------- actions

    private void action(Player viewer, ClickType click, String screenId, GuiConfig.Item item, int page, UUID target) {
        if (viewer == null || item == null) {
            return;
        }
        switch (item.action()) {
            case "close" -> viewer.closeInventory();
            case "back" -> open(viewer, backTo(screenId), 0, null);
            case "open-stats" -> open(viewer, STATS, 0, target);
            case "open-leaderboard" -> open(viewer, LEADERBOARD, 0, null);
            case "open-milestones" -> open(viewer, MILESTONES, 0, target);
            case "open-admin" -> {
                if (denied(viewer, "hourglass.admin.gui")) {
                    return;
                }
                open(viewer, ADMIN, 0, null);
            }
            case "page-back" -> open(viewer, screenId, Math.max(0, page - 1), target);
            case "page-forward" -> open(viewer, screenId, page + 1, target);
            case "page-first" -> open(viewer, screenId, 0, target);
            case "page-last" -> open(viewer, screenId, lastPage(screenId, viewer), target);
            case "page-self" -> {
                if (plugin.playtime().of(viewer.getUniqueId()) == null) {
                    plugin.messages().send(viewer, "leaderboard.unranked");
                    return;
                }
                open(viewer, LEADERBOARD, plugin.leaderboards().pageOf(viewer.getUniqueId()), null);
            }
            case "refresh" -> {
                if (LEADERBOARD.equals(screenId)) {
                    plugin.leaderboards().rebuild();
                }
                open(viewer, screenId, page, target);
                plugin.sounds().play(viewer, "gui-page");
            }
            case "toggle-display" -> {
                PlaytimeRecord.DisplayMode mode = plugin.display().cycle(viewer);
                Map<String, String> placeholders = Messages.ph("display", plugin.display().label(mode),
                        "player", viewer.getName());
                plugin.messages().send(viewer, "display.changed", placeholders);
                plugin.sounds().play(viewer, mode == PlaytimeRecord.DisplayMode.OFF ? "display-off" : "display-on");
                open(viewer, screenId, page, target);
            }
            case "copy-summary" -> {
                UUID subject = target != null ? target : viewer.getUniqueId();
                PlaytimeRecord record = plugin.playtime().of(subject);
                if (record == null) {
                    plugin.messages().send(viewer, "player-not-found", Messages.ph("input", String.valueOf(subject)));
                    return;
                }
                plugin.messages().send(viewer, "summary.chat", snapshotPlaceholders(record));
                plugin.sounds().play(viewer, "command-ok");
            }
            case "view-player" -> {
                if (target == null) {
                    return;
                }
                if (viewer.hasPermission("hourglass.admin.gui")) {
                    open(viewer, ADMIN_PLAYER, 0, target);
                } else {
                    viewOther(viewer, screenId, target);
                }
            }
            case "freeze-toggle", "reset-time", "add-time", "remove-time" -> adminEdit(viewer, click, screenId, item,
                    page, target);
            case "reload" -> {
                if (denied(viewer, "hourglass.admin.reload")) {
                    return;
                }
                boolean ok = plugin.reload();
                plugin.messages().send(viewer, ok ? "reloaded" : "reload-failed");
                plugin.sounds().play(viewer, ok ? "command-ok" : "command-error");
            }
            case "export" -> {
                if (denied(viewer, "hourglass.admin.export")) {
                    return;
                }
                exportFromGui(viewer);
            }
            default -> {
                // "none" or a custom action: clicking is still acknowledged.
            }
        }
    }

    private void viewOther(Player viewer, String screenId, UUID target) {
        if (!plugin.config().guiAllowViewOthers() || denied(viewer, "hourglass.see.others")) {
            return;
        }
        PlaytimeRecord record = plugin.playtime().of(target);
        if (record == null) {
            plugin.messages().send(viewer, "player-not-found", Messages.ph("input", nameOf(target)));
            return;
        }
        plugin.messages().send(viewer, "stats.other", snapshotPlaceholders(record));
        plugin.sounds().play(viewer, "command-ok");
    }

    private void adminEdit(Player viewer, ClickType click, String screenId, GuiConfig.Item item, int page, UUID target) {
        if (denied(viewer, "hourglass.admin.edit")) {
            return;
        }
        if (target == null) {
            plugin.messages().send(viewer, "gui.need-player");
            plugin.sounds().play(viewer, "gui-error");
            return;
        }
        PlaytimeRecord record = plugin.playtime().getOrCreate(target, nameOf(target));
        long delta = Math.max(0L, item.seconds());
        switch (item.action()) {
            case "freeze-toggle" -> {
                boolean now = !record.frozen();
                record.frozen(now);
                plugin.playtime().persist(record);
                plugin.messages().send(viewer, now ? "admin.freeze-on" : "admin.freeze-off",
                        Messages.ph("player", record.name() == null ? "?" : record.name()));
                plugin.sounds().play(viewer, "admin-edit");
            }
            case "reset-time" -> {
                // Destructive, so it needs the permission plus a deliberate shift-click:
                // one stray left click can never wipe somebody's record.
                if (!click.isShiftClick()) {
                    plugin.messages().send(viewer, "gui.reset-confirm");
                    plugin.sounds().play(viewer, "gui-error");
                    return;
                }
                record.reset(plugin.config().resetKeepsFirstJoin());
                plugin.playtime().persistNow(record);
                plugin.messages().send(viewer, "admin.reset-done",
                        Messages.ph("player", record.name() == null ? "?" : record.name()));
                plugin.sounds().play(viewer, "admin-reset");
            }
            case "remove-time" -> {
                record.shiftSeconds(-delta, plugin.config().adminEditsCountAsActive());
                plugin.playtime().persistNow(record);
                plugin.sounds().play(viewer, "admin-edit");
            }
            default -> {
                record.shiftSeconds(delta, plugin.config().adminEditsCountAsActive());
                plugin.playtime().persistNow(record);
                plugin.sounds().play(viewer, "admin-edit");
            }
        }
        plugin.milestones().check(record);
        open(viewer, screenId, page, target);
    }

    private void exportFromGui(Player viewer) {
        try {
            PlaytimeService.ExportResult result = plugin.playtime().exportCsv();
            plugin.messages().send(viewer, "admin.export-done", Messages.ph(
                    "file", result.file().getFileName().toString(),
                    "rows", String.valueOf(result.rows())));
            plugin.sounds().play(viewer, "admin-export");
        } catch (java.io.IOException e) {
            plugin.messages().send(viewer, "admin.export-failed", Messages.ph("error", String.valueOf(e.getMessage())));
            plugin.sounds().play(viewer, "command-error");
        }
    }

    /** Last usable page index for a screen's current list. */
    private int lastPage(String screenId, Player viewer) {
        GuiConfig.Screen screen = plugin.guiConfig().screen(screenId);
        int perPage = Math.max(1, screen.listCellsForSize());
        int items = switch (screenId) {
            case LEADERBOARD -> plugin.leaderboards().current().size();
            case MILESTONES -> plugin.config().milestones().size();
            case ADMIN -> plugin.playtime().online().size();
            default -> 0;
        };
        return Math.max(0, (items + perPage - 1) / perPage - 1);
    }

    private String backTo(String screenId) {
        return switch (screenId) {
            case ADMIN_PLAYER -> ADMIN;
            case LEADERBOARD, MILESTONES -> STATS;
            default -> STATS;
        };
    }

    private boolean denied(Player viewer, String permission) {
        if (viewer.hasPermission(permission)) {
            return false;
        }
        plugin.messages().send(viewer, "no-permission");
        plugin.sounds().play(viewer, "gui-error");
        return true;
    }

    // --------------------------------------------------------- placeholders

    /** Placeholders available to every screen. */
    private Map<String, String> placeholders(Player viewer, PlaytimeRecord record, int page, int pages) {
        HourGlassConfig config = plugin.config();
        long now = System.currentTimeMillis();
        Map<String, String> map = new java.util.HashMap<>();
        map.put("player", viewer.getName());
        map.put("uuid", String.valueOf(viewer.getUniqueId()));
        map.put("page", String.valueOf(page + 1));
        map.put("pages", String.valueOf(pages));
        map.put("version", plugin.getDescription().getVersion());
        map.put("metric", config.primaryIsActive() ? "active" : "total");
        map.put("display", plugin.display().effectiveMode(record));
        map.put("now", dates().format(now, "-"));
        map.put("leaderboard-enabled", String.valueOf(config.leaderboardEnabled()));
        if (record != null) {
            map.put("total", config.timeFormat().format(record.totalSeconds()));
            map.put("active", config.timeFormat().format(record.activeSeconds()));
            map.put("session", config.timeFormat().format(record.sessionTotalSeconds(now)));
            map.put("total-raw", String.valueOf(record.totalSeconds()));
            map.put("active-raw", String.valueOf(record.activeSeconds()));
            map.put("frozen", record.frozen() ? "yes" : "no");
            map.put("idle", String.valueOf(plugin.tracking().idle(record.id())));
            map.put("first_join", dates().format(record.firstJoin(), "unknown"));
            map.put("last_seen", dates().format(record.lastSeen(), "never"));
            map.put("history", String.valueOf(record.historyCount()));
            map.put("awarded", String.valueOf(record.awardedMilestones().size()));
            Milestone next = plugin.milestones().nextFor(record);
            map.put("milestones_next", next == null ? "-" : next.name());
            map.put("milestones_next_threshold", next == null ? "-" : config.timeFormat().format(next.seconds()));
            map.put("milestones_next_percent", next == null ? "100%" : percent(plugin.milestones().progress(record, next)));
            map.put("reached", String.valueOf(plugin.milestones().reached(record)));
            map.put("milestones", String.valueOf(config.milestones().size()));
            map.put("primary", config.timeFormat().format(plugin.playtime().primary(record, now)));
        }
        if (config.leaderboardEnabled()) {
            int rank = plugin.leaderboards().rankOf(viewer.getUniqueId());
            map.put("rank", rank > 0 ? "#" + rank : "-");
            map.put("rank-plain", String.valueOf(rank));
        } else {
            map.put("rank", "-");
            map.put("rank-plain", "-1");
        }
        return map;
    }

    /**
     * Placeholders describing one specific record — used for other players, GUI
     * rows, chat summaries, the join message and placeholders. Public so that
     * listeners and commands render text with exactly the same vocabulary.
     */
    public Map<String, String> placeholdersFor(PlaytimeRecord record) {
        return snapshotPlaceholders(record);
    }

    private Map<String, String> snapshotPlaceholders(PlaytimeRecord record) {
        HourGlassConfig config = plugin.config();
        long now = System.currentTimeMillis();
        Map<String, String> map = new java.util.HashMap<>();
        map.put("player", record.name() == null ? "?" : record.name());
        map.put("uuid", record.id().toString());
        map.put("total", config.timeFormat().format(record.totalSeconds()));
        map.put("active", config.timeFormat().format(record.activeSeconds()));
        map.put("total-raw", String.valueOf(record.totalSeconds()));
        map.put("active-raw", String.valueOf(record.activeSeconds()));
        map.put("session", config.timeFormat().format(record.sessionTotalSeconds(now)));
        map.put("session-active", config.timeFormat().format(record.sessionActiveSeconds()));
        map.put("first_join", dates().format(record.firstJoin(), "unknown"));
        map.put("last_seen", record.online() ? "online now" : dates().format(record.lastSeen(), "never"));
        map.put("online", record.online() ? "yes" : "no");
        map.put("frozen", record.frozen() ? "yes" : "no");
        map.put("idle", String.valueOf(plugin.tracking().idle(record.id())));
        map.put("history", String.valueOf(record.historyCount()));
        map.put("reached", String.valueOf(plugin.milestones().reached(record)));
        map.put("awarded", String.join(", ", record.awardedList()));
        map.put("milestones", String.valueOf(config.milestones().size()));
        Milestone next = plugin.milestones().nextFor(record);
        map.put("milestones_next", next == null ? "-" : next.name());
        map.put("milestones_next_threshold", next == null ? "-" : config.timeFormat().format(next.seconds()));
        map.put("milestones_next_percent", next == null ? "100%" : percent(plugin.milestones().progress(record, next)));
        int rank = plugin.leaderboards().rankOf(record.id());
        map.put("rank", rank > 0 ? "#" + rank : "-");
        map.put("rank-plain", String.valueOf(rank));
        map.put("primary", config.timeFormat().format(plugin.playtime().primary(record, now)));
        map.put("metric", config.primaryIsActive() ? "active" : "total");
        map.put("days", String.valueOf(record.totalSeconds() / 86_400L));
        map.put("hours", String.valueOf(record.totalSeconds() / 3_600L));
        return map;
    }

    private Dates dates() {
        return plugin.playtime().dates();
    }

    private String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        PlaytimeRecord record = plugin.playtime().of(id);
        if (record != null && record.name() != null) {
            return record.name();
        }
        try {
            String name = Bukkit.getOfflinePlayer(id).getName();
            return name == null ? id.toString().substring(0, 8) : name;
        } catch (RuntimeException e) {
            return id.toString().substring(0, 8);
        }
    }
}
