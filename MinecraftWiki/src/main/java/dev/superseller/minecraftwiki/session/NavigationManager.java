package dev.superseller.minecraftwiki.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import dev.superseller.minecraftwiki.gui.WikiMenu;
import dev.superseller.minecraftwiki.scheduler.PlatformScheduler;

/**
 * Owns the wiki sessions and every navigation move.
 *
 * <p>The map is the only place a session lives; disconnects, closes and reloads all funnel
 * through here, which is what keeps the plugin from holding on to a player who is gone.</p>
 */
public final class NavigationManager {

    private final Map<UUID, WikiSession> sessions = new ConcurrentHashMap<>();
    // Volatile rather than final: a reload reconfigures this instance instead of replacing it,
    // because listeners and menus hold a reference to it.
    private volatile int historyLimit;
    private volatile boolean keepSessionOpen;
    private volatile String defaultLanguage;

    public NavigationManager(int historyLimit, boolean keepSessionOpen, String defaultLanguage) {
        this.historyLimit = historyLimit;
        this.keepSessionOpen = keepSessionOpen;
        this.defaultLanguage = defaultLanguage == null ? "en" : defaultLanguage;
    }

    /**
     * Re-applies navigation settings after a reload.
     *
     * <p>The instance stays the same on purpose - the GUI listener and every open menu reference
     * it - but sessions are dropped because they hold menus built from the previous catalogue.</p>
     */
    public void configure(int historyLimit, boolean keepSessionOpen, String defaultLanguage) {
        this.historyLimit = historyLimit;
        this.keepSessionOpen = keepSessionOpen;
        this.defaultLanguage = defaultLanguage == null ? "en" : defaultLanguage;
    }

    /** The player's session, created on first use. */
    public WikiSession session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(),
                id -> new WikiSession(id, historyLimit, defaultLanguage));
    }

    public WikiSession peek(UUID playerId) {
        return sessions.get(playerId);
    }

    /**
     * Opens a menu, remembering the one the player is leaving.
     *
     * <p>Always executed on the entity's own thread so it is correct on Folia.</p>
     */
    public void open(Player player, WikiMenu menu) {
        if (player == null || menu == null) {
            return;
        }
        PlatformScheduler.runEntity(player, () -> {
            WikiSession session = session(player);
            WikiMenu previous = session.current();
            if (previous != null && previous != menu) {
                session.push(previous);
            }
            session.current(menu);
            menu.open();
        });
    }

    /** Opens a menu without recording history, used for the very first menu. */
    public void openRoot(Player player, WikiMenu menu) {
        if (player == null || menu == null) {
            return;
        }
        PlatformScheduler.runEntity(player, () -> {
            WikiSession session = session(player);
            session.clear();
            session.current(menu);
            menu.open();
        });
    }

    /** Goes back one step, or returns false when there is nowhere to go. */
    public boolean back(Player player) {
        WikiSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        WikiMenu previous = session.pop();
        if (previous == null) {
            return false;
        }
        session.current(previous);
        previous.open();
        return true;
    }

    /** Called when a wiki inventory closes. */
    public void closed(Player player) {
        WikiSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.current(null);
        if (!keepSessionOpen) {
            sessions.remove(player.getUniqueId());
        }
    }

    /** Called on disconnect; always removes the session. */
    public void forget(UUID playerId) {
        sessions.remove(playerId);
    }

    /** Drops every session, used on reload so no menu points at a stale catalogue. */
    public void clear() {
        sessions.clear();
    }

    public int size() {
        return sessions.size();
    }
}
