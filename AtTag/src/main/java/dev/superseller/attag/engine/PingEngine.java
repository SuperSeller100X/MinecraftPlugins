package dev.superseller.attag.engine;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure @-mention parsing engine — no Bukkit types, fully unit-testable.
 *
 * Recognised tokens (case-insensitive, word-boundary aware):
 * <ul>
 *   <li>{@code @playername}  — pings that player (player sound).</li>
 *   <li>{@code @here}        — replaced with the sender's block coordinates
 *       as {@code [x, y, z]}. No sound.</li>
 *   <li>{@code @everyone} / {@code @all} — pings every online player
 *       except the sender (everyone sound).</li>
 * </ul>
 *
 * Rules:
 * <ul>
 *   <li>Special tokens ({@code here}, {@code everyone}, {@code all}) take
 *       precedence over a player who happens to have the same name.</li>
 *   <li>The sender never hears their own ping sound.</li>
 *   <li>A player is never pinged twice per message (explicit mention +
 *       {@code @everyone} yields one sound, the explicit one).</li>
 *   <li>Offline/unknown names are left in the message untouched.</li>
 * </ul>
 */
public final class PingEngine {

    private static final Pattern HERE_TOKEN = tokenPattern("here");
    private static final Pattern EVERYONE_TOKEN = tokenPattern("everyone");
    private static final Pattern ALL_TOKEN = tokenPattern("all");

    private PingEngine() {
    }

    /** Result of parsing one chat message. Immutable. */
    public static final class Result {

        private final String message;
        private final Set<String> namedTargets;
        private final Set<String> everyoneTargets;

        Result(String message, Set<String> namedTargets, Set<String> everyoneTargets) {
            this.message = message;
            this.namedTargets = namedTargets;
            this.everyoneTargets = everyoneTargets;
        }

        /** The chat message after @-processing (may differ from the input). */
        public String message() {
            return message;
        }

        /** Players explicitly mentioned with {@code @playername} (player sound). */
        public Set<String> namedTargets() {
            return namedTargets;
        }

        /** Players reached through {@code @everyone}/{@code @all} (everyone sound). */
        public Set<String> everyoneTargets() {
            return everyoneTargets;
        }

        /** True when at least one player should hear a ping sound. */
        public boolean hasPing() {
            return !namedTargets.isEmpty() || !everyoneTargets.isEmpty();
        }
    }

    /**
     * Parses one message.
     *
     * @param message     the raw chat message (may be null/empty)
     * @param onlineNames names of all online players (case-insensitive match)
     * @param senderName  name of the sender (never pinged)
     * @param x           sender block X for @here
     * @param y           sender block Y for @here
     * @param z           sender block Z for @here
     * @return the parse result
     */
    public static Result parse(String message, Collection<String> onlineNames,
                               String senderName, int x, int y, int z) {
        if (message == null || message.indexOf('@') < 0) {
            return new Result(message == null ? "" : message,
                    new LinkedHashSet<>(), new LinkedHashSet<>());
        }

        String rewritten = message;
        if (HERE_TOKEN.matcher(message).find()) {
            rewritten = HERE_TOKEN.matcher(message)
                    .replaceAll(Matcher.quoteReplacement("[" + x + ", " + y + ", " + z + "]"));
        }
        boolean everyone = EVERYONE_TOKEN.matcher(message).find()
                || ALL_TOKEN.matcher(message).find();

        Set<String> named = new LinkedHashSet<>();
        if (onlineNames != null) {
            for (String name : onlineNames) {
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (name.equalsIgnoreCase(senderName)) {
                    continue; // the sender never pings themself
                }
                if (isSpecialName(name)) {
                    continue; // @here/@everyone/@all take precedence
                }
                if (tokenPattern(name).matcher(message).find()) {
                    named.add(name);
                }
            }
        }

        Set<String> everyoneTargets = new LinkedHashSet<>();
        if (everyone && onlineNames != null) {
            for (String name : onlineNames) {
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (name.equalsIgnoreCase(senderName)) {
                    continue;
                }
                if (named.contains(name)) {
                    continue; // already pinged via explicit mention
                }
                everyoneTargets.add(name);
            }
        }

        return new Result(rewritten, named, everyoneTargets);
    }

    private static boolean isSpecialName(String name) {
        return name.equalsIgnoreCase("here")
                || name.equalsIgnoreCase("everyone")
                || name.equalsIgnoreCase("all");
    }

    /**
     * Matches {@code @token} at a word boundary: not preceded/followed by
     * {@code [a-zA-Z0-9_]}, so {@code "@alex123"} never matches player
     * {@code alex} and {@code "@here"} never matches {@code "@heretic"}.
     */
    private static Pattern tokenPattern(String token) {
        return Pattern.compile("(?i)(?<![a-z0-9_])@" + Pattern.quote(token) + "(?![a-z0-9_])");
    }
}
