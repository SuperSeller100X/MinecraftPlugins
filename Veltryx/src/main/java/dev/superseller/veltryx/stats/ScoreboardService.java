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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.shards.ShardService;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;

/**
 * The always-on stats display:
 * <ul>
 *   <li><b>Sidebar</b> (right side of the screen): cash, shards, deaths,
 *       kills and playtime — the player's own numbers.</li>
 *   <li><b>Nametag</b>: four lines above every player's username — money,
 *       shards, deaths (skull) and kills (sword), via scoreboard team
 *       suffixes so every viewer sees them.</li>
 * </ul>
 * Every player gets their own scoreboard (their own sidebar); the nametag
 * teams of all online players are mirrored into every board so the tags
 * render for everyone.
 */
public final class ScoreboardService implements Listener {

    private static final String OBJECTIVE = "veltryx";

    private final StatsService stats;
    private final EconomyService economy;
    private final ShardService shards;
    private final long updateSeconds;

    /** One board per player (their personal sidebar). */
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    /** Last sidebar entry strings per player (to reset renamed rows). */
    private final Map<UUID, String[]> lastRows = new ConcurrentHashMap<>();
    /** Team name per player (for nametag mirroring and cleanup). */
    private final Map<UUID, String> teamNames = new ConcurrentHashMap<>();

    public ScoreboardService(StatsService stats, EconomyService economy, ShardService shards,
                             long updateSeconds) {
        this.stats = stats;
        this.economy = economy;
        this.shards = shards;
        this.updateSeconds = Math.max(5L, updateSeconds);
    }

    /** Builds/refreshes every visible line (Folia-safe periodic timer). */
    public void startTimer() {
        PlatformScheduler.runTimer(this::refreshAll, updateSeconds * 20L);
    }

    /** Refreshes all sidebars and nametags. */
    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshSidebar(player);
        }
        syncTeams();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshAll();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boards.remove(id);
        lastRows.remove(id);
        String teamName = teamNames.remove(id);
        if (teamName != null) {
            for (Scoreboard board : boards.values()) {
                Team team = board.getTeam(teamName);
                if (team != null) {
                    team.unregister();
                }
            }
        }
    }

    /** Rebuilds one player's sidebar: money, shards, deaths, kills, playtime. */
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

        String[] rows = StatLines.sidebarRows(
                economy.getBalance(player.getUniqueId()),
                shards.balance(player.getUniqueId()),
                stats.deaths(player.getUniqueId()),
                stats.kills(player.getUniqueId()),
                stats.playtimeMillis(player.getUniqueId()));
        String[] entries = new String[rows.length];
        String[] previous = lastRows.get(player.getUniqueId());
        for (int i = 0; i < rows.length; i++) {
            entries[i] = StatLines.uniqueEntry(rows[i], i);
            if (previous != null && i < previous.length && !previous[i].equals(entries[i])) {
                board.resetScores(previous[i]);
            }
            // highest score on top: money 5 … playtime 1
            objective.getScore(entries[i]).setScore(rows.length - i);
        }
        lastRows.put(player.getUniqueId(), entries);
    }

    /** Mirrors every online player's nametag lines into every board. */
    private void syncTeams() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boardFor(viewer);
            for (Player target : Bukkit.getOnlinePlayers()) {
                String teamName = teamNameFor(target);
                Team team = board.getTeam(teamName);
                if (team == null) {
                    team = board.registerNewTeam(teamName);
                }
                team.suffix(Txt.legacy(nametagSuffix(target)));
                team.addEntry(target.getName());
            }
        }
    }

    /** The nametag lines below the username: money, shards, deaths, kills. */
    private String nametagSuffix(Player player) {
        StringBuilder suffix = new StringBuilder();
        for (String line : StatLines.nametagLines(
                economy.getBalance(player.getUniqueId()),
                shards.balance(player.getUniqueId()),
                stats.deaths(player.getUniqueId()),
                stats.kills(player.getUniqueId()))) {
            suffix.append('\n').append(line);
        }
        return suffix.toString();
    }

    /** Team names are limited in length: "vx" + up to 14 name characters. */
    private String teamNameFor(Player player) {
        String known = teamNames.get(player.getUniqueId());
        if (known != null) {
            return known;
        }
        String base = "vx" + player.getName().substring(0, Math.min(14, player.getName().length()));
        String candidate = base;
        int counter = 1;
        while (teamNames.containsValue(candidate)) {
            candidate = base.substring(0, Math.min(14, base.length())) + counter++;
        }
        teamNames.put(player.getUniqueId(), candidate);
        return candidate;
    }

    private Scoreboard boardFor(Player player) {
        return boards.computeIfAbsent(player.getUniqueId(),
                id -> {
                    Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
                    player.setScoreboard(board);
                    return board;
                });
    }
}
