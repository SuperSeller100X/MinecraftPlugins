package dev.superseller.hourglass.integration;

import java.util.List;
import java.util.Locale;

import dev.superseller.hourglass.HourGlassPlugin;
import dev.superseller.hourglass.data.Milestone;
import dev.superseller.hourglass.data.PlaytimeRecord;
import dev.superseller.hourglass.service.Leaderboard;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Optional <a href="https://www.spigotmc.org/resources/6245/">PlaceholderAPI</a>
 * bridge. The class is only loaded when PlaceholderAPI is installed, so
 * HourGlass has no runtime dependency on it.
 *
 * <p>Every alias in {@code placeholders.aliases} gets its own expansion, so
 * {@code %hourglass_total%}, {@code %playtime_total%} and {@code %hg_total%}
 * all work while the config decides which prefixes exist.
 *
 * <p>Unknown params return {@code null} (never an empty string) so other
 * plugins' placeholders are left alone.
 */
public final class PlaceholderApiHook {

    private PlaceholderApiHook() {
    }

    /** Registers one expansion per configured alias. Returns how many were added. */
    public static int register(HourGlassPlugin plugin) {
        List<String> aliases = plugin.config().placeholderAliases();
        int registered = 0;
        for (String alias : aliases) {
            Expansion expansion = new Expansion(plugin, alias);
            try {
                if (expansion.register()) {
                    registered++;
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not register PlaceholderAPI expansion '" + alias + "': "
                        + e.getMessage());
            }
        }
        return registered;
    }

    /** Unregisters every expansion this plugin added. */
    public static void unregister(HourGlassPlugin plugin) {
        List<String> aliases = plugin.config().placeholderAliases();
        for (String alias : aliases) {
            try {
                new Expansion(plugin, alias).unregister();
            } catch (RuntimeException ignored) {
                // PAPI already dropped the expansion during its own shutdown.
            }
        }
    }

    static final class Expansion extends PlaceholderExpansion {

        private final HourGlassPlugin plugin;
        private final String identifier;

        Expansion(HourGlassPlugin plugin, String identifier) {
            this.plugin = plugin;
            this.identifier = identifier.toLowerCase(Locale.US);
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @Override
        public String getAuthor() {
            return "SuperSeller100X";
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            if (player == null || params == null || params.isBlank()) {
                return null;
            }
            long now = System.currentTimeMillis();
            PlaytimeRecord record = plugin.playtime().of(player.getUniqueId());
            String key = params.trim().toLowerCase(Locale.US);

            if (key.startsWith("top_")) {
                return top(key);
            }
            if (record == null) {
                // Never-seen player (or the plugin does not know them): numbers are 0,
                // text falls back to the configured zero format, so a scoreboard line
                // stays readable instead of showing the raw %placeholder%.
                return switch (key) {
                    case "total", "total_seconds", "total_plain", "total_minutes", "total_hours",
                         "total_days", "active", "active_seconds", "active_plain", "active_hours",
                         "session", "session_seconds", "session_active", "primary", "primary_seconds",
                         "rank", "rank_plain", "sessions", "milestones_reached", "milestones_total",
                         "milestones_remaining" -> "0";
                    case "idle", "frozen", "online" -> "no";
                    case "total_formatted", "active_formatted", "session_formatted", "primary_formatted" ->
                            plugin.config().timeFormat().format(0L);
                    case "first_join", "last_seen", "milestones_next", "milestones_next_formatted",
                         "display" -> "never";
                    default -> null;
                };
            }

            Milestone next = plugin.milestones().nextFor(record);
            int rank = plugin.leaderboards().rankOf(record.id());
            return switch (key) {
                case "total", "total_formatted" -> format(record.totalSeconds());
                case "total_seconds", "total_plain" -> String.valueOf(record.totalSeconds());
                case "total_minutes" -> String.valueOf(record.totalSeconds() / 60L);
                case "total_hours" -> String.valueOf(record.totalSeconds() / 3_600L);
                case "total_days" -> String.valueOf(record.totalSeconds() / 86_400L);
                case "active", "active_formatted" -> format(record.activeSeconds());
                case "active_seconds", "active_plain" -> String.valueOf(record.activeSeconds());
                case "active_hours" -> String.valueOf(record.activeSeconds() / 3_600L);
                case "session", "session_formatted" -> format(record.sessionTotalSeconds(now));
                case "session_seconds" -> String.valueOf(record.sessionTotalSeconds(now));
                case "session_active" -> format(record.sessionActiveSeconds());
                case "primary", "primary_formatted" -> format(plugin.playtime().primary(record, now));
                case "primary_seconds" -> String.valueOf(plugin.playtime().primary(record, now));
                case "first_join" -> plugin.playtime().dates().format(record.firstJoin(), "never");
                case "last_seen" -> record.online() ? "online"
                        : plugin.playtime().dates().format(record.lastSeen(), "never");
                case "online" -> record.online() ? "yes" : "no";
                case "idle" -> plugin.tracking().idle(record.id()) ? "yes" : "no";
                case "frozen" -> record.frozen() ? "yes" : "no";
                case "rank" -> rank > 0 ? "#" + rank : "-";
                case "rank_plain" -> String.valueOf(rank);
                case "sessions" -> String.valueOf(record.historyCount());
                case "milestones_reached" -> String.valueOf(plugin.milestones().reached(record));
                case "milestones_total" -> String.valueOf(plugin.config().milestones().size());
                case "milestones_remaining" -> String.valueOf(Math.max(0,
                        plugin.config().milestones().size() - plugin.milestones().reached(record)));
                case "milestones_next" -> next == null ? "-" : next.name();
                case "milestones_next_formatted" -> next == null ? "-" : format(next.seconds());
                case "milestones_next_seconds" -> next == null ? "-1" : String.valueOf(next.seconds());
                case "milestones_next_percent" -> next == null ? "100"
                        : String.valueOf(Math.round(plugin.milestones().progress(record, next) * 100.0d));
                case "display" -> plugin.display().effectiveMode(record);
                default -> null;
            };
        }

        private String format(long seconds) {
            return plugin.config().timeFormat().format(seconds);
        }

        /** {@code top_3_name} / {@code top_3_time} / {@code top_3_seconds}. */
        private String top(String key) {
            String[] parts = key.split("_");
            if (parts.length < 3) {
                return null;
            }
            int position;
            try {
                position = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            List<Leaderboard.Entry> entries = plugin.leaderboards().top(Math.max(position, 1));
            if (position < 1 || position > entries.size()) {
                return parts[2].equals("name") ? "-" : "0";
            }
            Leaderboard.Entry entry = entries.get(position - 1);
            return switch (parts[2]) {
                case "time", "formatted" -> format(entry.seconds());
                case "seconds" -> String.valueOf(entry.seconds());
                case "uuid" -> entry.id().toString();
                default -> entry.displayName();
            };
        }
    }
}
