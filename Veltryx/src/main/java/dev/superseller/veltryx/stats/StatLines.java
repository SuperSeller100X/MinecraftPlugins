package dev.superseller.veltryx.stats;

import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.TimeUtil;

/**
 * The scoreboard and nametag line texts: cash, shards, deaths, kills and
 * playtime with their icons. Pure string building so the smoke suite can
 * verify the exact layout.
 */
public final class StatLines {

    /** Purple star for shards, orange skull for deaths, red sword for kills. */
    public static final String STAR = "\u2726";    // ✦
    public static final String SKULL = "\u2620";   // ☠
    public static final String SWORD = "\u2694";   // ⚔
    public static final String CLOCK = "\u23F1";   // ⏱

    private StatLines() {
    }

    /** Sidebar row: {@code $ 235M} in the money colors. */
    public static String money(long cents) { return money(java.math.BigInteger.valueOf(cents)); }
    public static String money(java.math.BigInteger cents) {
        return "§a$ §f" + Money.formatCompact(cents);
    }

    /** Sidebar/nametag row: purple star + compact shard count. */
    public static String shards(long shards) {
        return "§d" + STAR + " §f" + compactNumber(shards);
    }

    /** Sidebar/nametag row: orange skull + death count. */
    public static String deaths(long deaths) {
        return "§6" + SKULL + " §f" + compactNumber(deaths);
    }

    /** Sidebar/nametag row: red sword + kill count. */
    public static String kills(long kills) {
        return "§c" + SWORD + " §f" + compactNumber(kills);
    }

    /** Sidebar row: clock + playtime like {@code 5d 13h}. */
    public static String playtime(long playtimeMillis) {
        return "§e" + CLOCK + " §f" + TimeUtil.formatPlaytime(playtimeMillis);
    }

    /**
     * The only stat displayed below another player's name is money.
     */
    public static String[] nametagLines(long cents, long shards, long deaths, long kills) {
        return new String[] {money(cents)};
    }

    /** The sidebar rows top to bottom: money, shards, deaths, kills, playtime. */
    public static String[] sidebarRows(long cents, long shards, long deaths, long kills, long playtimeMillis) {
        return sidebarRows(java.math.BigInteger.valueOf(cents), shards, deaths, kills, playtimeMillis);
    }
    public static String[] sidebarRows(java.math.BigInteger cents, long shards, long deaths, long kills, long playtimeMillis) {
        return new String[] {
                money(cents), shards(shards), deaths(deaths), kills(kills), playtime(playtimeMillis)};
    }

    /**
     * Sidebar entries must be unique strings — two identical rows would
     * share one score. Every row gets a different invisible trailing color
     * code so the texts can never collide.
     */
    public static String uniqueEntry(String row, int index) {
        String[] hidden = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
                "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"};
        return row + hidden[Math.floorMod(index, hidden.length)];
    }

    /** Compact plain numbers: 849 → "849", 10600 → "10.6k", 2350000 → "2.4M". */
    public static String compactNumber(long value) {
        long v = Math.max(0, value);
        if (v >= 1_000_000) {
            return trimDecimal(v / 1_000_000.0) + "M";
        }
        if (v >= 1_000) {
            return trimDecimal(v / 1_000.0) + "k";
        }
        return String.valueOf(v);
    }

    private static String trimDecimal(double tenth) {
        long whole = (long) tenth;
        long frac = Math.round((tenth - whole) * 10);
        if (frac >= 10) {
            whole += 1;
            frac = 0;
        }
        return frac == 0 ? String.valueOf(whole) : whole + "." + frac;
    }
}
