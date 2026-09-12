package dev.superseller.minecraftwiki.session;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;

import dev.superseller.minecraftwiki.gui.WikiMenu;

/**
 * One player's wiki state: where they are, where they came from and which language they use.
 *
 * <p>Sessions are dropped when a player disconnects, and the history is a bounded deque, so
 * neither can grow without limit. All access happens on the player's own thread.</p>
 */
public final class WikiSession {

    private final UUID playerId;
    private final int historyLimit;
    private final Deque<WikiMenu> history = new ArrayDeque<>();
    private WikiMenu current;
    private String language;
    private long openedAt;

    public WikiSession(UUID playerId, int historyLimit, String language) {
        this.playerId = playerId;
        this.historyLimit = Math.max(0, historyLimit);
        this.language = language == null ? "" : language.toLowerCase(Locale.ROOT);
        this.openedAt = System.currentTimeMillis();
    }

    public UUID playerId() {
        return playerId;
    }

    /** Records the menu the player is leaving so Back can return to it. */
    public void push(WikiMenu previous) {
        if (previous == null || historyLimit == 0) {
            return;
        }
        while (history.size() >= historyLimit) {
            history.pollFirst();
        }
        history.addLast(previous);
    }

    /** The menu Back should return to, or null when there is nowhere to go back to. */
    public WikiMenu pop() {
        return history.isEmpty() ? null : history.pollLast();
    }

    /** The menu Back would return to, without removing it. */
    public WikiMenu peekHistory() {
        return history.isEmpty() ? null : history.peekLast();
    }

    public boolean hasHistory() {
        return !history.isEmpty();
    }

    public int historySize() {
        return history.size();
    }

    public WikiMenu current() {
        return current;
    }

    public void current(WikiMenu menu) {
        this.current = menu;
    }

    public String language() {
        return language;
    }

    public void language(String language) {
        this.language = language == null ? "" : language.toLowerCase(Locale.ROOT);
    }

    public long openedAt() {
        return openedAt;
    }

    /** Drops all navigation state, used when the wiki is reloaded or the player quits. */
    public void clear() {
        history.clear();
        current = null;
    }
}
