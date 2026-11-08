package dev.superseller.veltryx.stats;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.shards.ShardService;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;

/** Per-viewer sidebar plus only other players' money below their names. */
public final class ScoreboardService implements Listener {
    private static final String SIDEBAR = "veltryx";
    private static final String MONEY = "vx_money";
    private final StatsService stats;
    private final EconomyService economy;
    private final ShardService shards;
    private final long updateSeconds;
    private final boolean nametags;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, String[]> lastRows = new ConcurrentHashMap<>();

    public ScoreboardService(StatsService stats, EconomyService economy, ShardService shards,
                             long updateSeconds, boolean nametags) {
        this.stats = stats; this.economy = economy; this.shards = shards;
        this.updateSeconds = Math.max(1L, updateSeconds); this.nametags = nametags;
    }
    public void startTimer() { PlatformScheduler.runTimer(this::refreshAll, updateSeconds * 20L); }

    /** All board mutations run on the viewer's entity scheduler. */
    public void refreshAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PlatformScheduler.runForPlayer(viewer, () -> {
                refreshSidebar(viewer);
                if (!nametags) return;
                Scoreboard board = boardFor(viewer);
                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (!target.getUniqueId().equals(viewer.getUniqueId())) {
                        updateMoney(board, target.getUniqueId(), target.getName());
                    }
                }
            });
        }
    }
    public void refreshChanged(UUID id) {
        Player target = Bukkit.getPlayer(id);
        if (target == null || !target.isOnline()) return;
        String name = target.getName();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean own = viewer.getUniqueId().equals(id);
            if (!nametags && !own) continue;
            PlatformScheduler.runForPlayer(viewer, () -> {
                if (own) refreshSidebar(viewer);
                else if (target.isOnline()) updateMoney(boardFor(viewer), id, name);
            });
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { refreshAll(); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        boards.remove(id); lastRows.remove(id);
        if (!nametags) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(id)) continue;
            PlatformScheduler.runForPlayer(viewer, () -> {
                Scoreboard board = boards.get(viewer.getUniqueId());
                Objective objective = board == null ? null : board.getObjective(MONEY);
                if (objective != null) objective.getScore(name).resetScore();
            });
        }
    }
    private void refreshSidebar(Player player) {
        Scoreboard board = boardFor(player);
        Objective objective = board.getObjective(SIDEBAR);
        if (objective == null) objective = board.registerNewObjective(SIDEBAR, Criteria.DUMMY,
                Txt.legacy(player.getName()), RenderType.INTEGER);
        else objective.displayName(Txt.legacy(player.getName()));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        UUID id = player.getUniqueId();
        String[] rows = StatLines.sidebarRows(economy.getBalanceExact(id), shards.balance(id),
                stats.deaths(id), stats.kills(id), stats.playtimeMillis(id));
        String[] entries = new String[rows.length];
        String[] previous = lastRows.get(id);
        for (int i = 0; i < rows.length; i++) {
            entries[i] = StatLines.uniqueEntry(rows[i], i);
            if (previous != null && i < previous.length && !previous[i].equals(entries[i])) board.resetScores(previous[i]);
            Score score = objective.getScore(entries[i]);
            score.setScore(rows.length - i);
            score.numberFormat(NumberFormat.blank());
        }
        lastRows.put(id, entries);
    }
    private void updateMoney(Scoreboard board, UUID id, String name) {
        Objective objective = board.getObjective(MONEY);
        if (objective == null) {
            objective = board.registerNewObjective(MONEY, Criteria.DUMMY,
                    net.kyori.adventure.text.Component.empty(), RenderType.INTEGER);
            objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        Score score = objective.getScore(name);
        score.setScore(0); // the score is replaced, not displayed as an int
        score.numberFormat(NumberFormat.fixed(Txt.legacy(StatLines.money(economy.getBalanceExact(id)))));
    }
    private Scoreboard boardFor(Player player) {
        return boards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
            return board;
        });
    }
}
