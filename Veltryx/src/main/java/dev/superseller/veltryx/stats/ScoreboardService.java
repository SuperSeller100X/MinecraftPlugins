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
import org.bukkit.scoreboard.Team;
import io.papermc.paper.scoreboard.numbers.NumberFormat;

import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.shards.ShardService;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;

/** Per-player stats sidebar and mirrored player nametags. */
public final class ScoreboardService implements Listener {
    private static final String OBJECTIVE = "veltryx";
    private final StatsService stats;
    private final EconomyService economy;
    private final ShardService shards;
    private final long updateSeconds;
    private final boolean nametags;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, String[]> lastRows = new ConcurrentHashMap<>();
    private final Map<UUID, String> teamNames = new ConcurrentHashMap<>();

    public ScoreboardService(StatsService stats, EconomyService economy, ShardService shards,
                             long updateSeconds, boolean nametags) {
        this.stats = stats;
        this.economy = economy;
        this.shards = shards;
        this.updateSeconds = Math.max(1L, updateSeconds);
        this.nametags = nametags;
    }

    /** Periodic playtime/fallback refresh. Balance changes also trigger refreshChanged. */
    public void startTimer() {
        PlatformScheduler.runTimer(this::refreshAll, updateSeconds * 20L);
    }

    /** Schedule every board on its viewer's region, not on the global timer thread. */
    public void refreshAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PlatformScheduler.runForPlayer(viewer, () -> {
                refreshSidebar(viewer);
                if (nametags) {
                    for (Player target : Bukkit.getOnlinePlayers()) {
                        updateTeam(boardFor(viewer), target);
                    }
                }
            });
        }
    }

    /** Update a changed balance and its visible nametag on the next player tick. */
    public void refreshChanged(UUID id) {
        Player target = Bukkit.getPlayer(id);
        if (target == null || !target.isOnline()) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!nametags && !viewer.getUniqueId().equals(id)) continue;
            PlatformScheduler.runForPlayer(viewer, () -> {
                if (viewer.getUniqueId().equals(id)) refreshSidebar(viewer);
                if (nametags && target.isOnline()) updateTeam(boardFor(viewer), target);
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { refreshAll(); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        boards.remove(id);
        lastRows.remove(id);
        String teamName = teamNames.remove(id);
        if (teamName == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(id)) continue;
            PlatformScheduler.runForPlayer(viewer, () -> {
                Scoreboard board = boards.get(viewer.getUniqueId());
                if (board != null) {
                    Team team = board.getTeam(teamName);
                    if (team != null) team.unregister();
                }
            });
        }
    }

    private void refreshSidebar(Player player) {
        Scoreboard board = boardFor(player);
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                    Txt.legacy(player.getName()), RenderType.INTEGER);
        } else {
            objective.displayName(Txt.legacy(player.getName()));
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        UUID id = player.getUniqueId();
        String[] rows = StatLines.sidebarRows(economy.getBalance(id), shards.balance(id),
                stats.deaths(id), stats.kills(id), stats.playtimeMillis(id));
        String[] entries = new String[rows.length];
        String[] previous = lastRows.get(id);
        for (int i = 0; i < rows.length; i++) {
            entries[i] = StatLines.uniqueEntry(rows[i], i);
            if (previous != null && i < previous.length && !previous[i].equals(entries[i])) {
                board.resetScores(previous[i]);
            }
            Score score = objective.getScore(entries[i]);
            score.setScore(rows.length - i); // sorting only; never rendered
            score.numberFormat(NumberFormat.blank());
        }
        lastRows.put(id, entries);
    }

    private void updateTeam(Scoreboard board, Player target) {
        String name = teamNames.computeIfAbsent(target.getUniqueId(), id ->
                // UUID gives unique names even for players with similar names.
                "vx" + id.toString().replace("-", "").substring(0, 14));
        Team team = board.getTeam(name);
        if (team == null) team = board.registerNewTeam(name);
        UUID id = target.getUniqueId();
        StringBuilder suffix = new StringBuilder();
        for (String line : StatLines.nametagLines(economy.getBalance(id), shards.balance(id),
                stats.deaths(id), stats.kills(id))) suffix.append('\n').append(line);
        team.suffix(Txt.legacy(suffix.toString()));
        team.addEntry(target.getName());
    }

    private Scoreboard boardFor(Player player) {
        return boards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
            return board;
        });
    }
}
