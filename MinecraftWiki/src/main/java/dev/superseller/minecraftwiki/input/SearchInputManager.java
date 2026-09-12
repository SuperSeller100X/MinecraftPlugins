package dev.superseller.minecraftwiki.input;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import dev.superseller.minecraftwiki.config.GuiSettings;
import dev.superseller.minecraftwiki.config.PermissionNames;
import dev.superseller.minecraftwiki.config.PluginSettings;
import dev.superseller.minecraftwiki.gui.MenuFactory;
import dev.superseller.minecraftwiki.gui.WikiMenu;
import dev.superseller.minecraftwiki.message.Messages;
import dev.superseller.minecraftwiki.scheduler.PlatformScheduler;
import dev.superseller.minecraftwiki.search.SearchQuery;
import dev.superseller.minecraftwiki.search.SearchResult;
import dev.superseller.minecraftwiki.search.SearchService;
import dev.superseller.minecraftwiki.session.NavigationManager;
import dev.superseller.minecraftwiki.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;

/**
 * Collects a search string from the player.
 *
 * <p>Paper's dialog API is not part of the 26.2 API surface this plugin builds against
 * (there is no {@code Player#showDialog} and no dialog response event), so input is taken
 * through chat. That path is made reliable rather than left raw:</p>
 * <ul>
 *   <li>the player is told exactly what to type and how to cancel;</li>
 *   <li>the request expires after a configurable timeout and the previous screen is restored;</li>
 *   <li>a line starting with {@code /} is rejected instead of being treated as a query;</li>
 *   <li>the chat event is cancelled only while a request is pending, so normal chat is never
 *       swallowed;</li>
 *   <li>the query is scored on the chat thread and only the resulting screen is opened on the
 *       player's own thread.</li>
 * </ul>
 */
public final class SearchInputManager implements Listener {

    /** Used when the configured cancel word is blank. */
    private static final String DEFAULT_CANCEL_WORD = "cancel";

    /** One outstanding chat request. */
    private static final class Pending {

        private final WikiMenu returnTo;
        private final long expiresAt;
        private final PlatformScheduler.ScheduledTask timeoutTask;

        private Pending(WikiMenu returnTo, long expiresAt, PlatformScheduler.ScheduledTask timeoutTask) {
            this.returnTo = returnTo;
            this.expiresAt = expiresAt;
            this.timeoutTask = timeoutTask;
        }
    }

    // Volatile rather than final: a reload reconfigures this instance instead of replacing it,
    // because it is registered as a listener exactly once.
    private volatile PluginSettings settings;
    private volatile GuiSettings gui;
    private volatile Messages messages;
    private volatile PermissionNames permissions;
    private final SearchService search;
    private final NavigationManager navigation;
    private volatile MenuFactory menus;
    private volatile String cancelWord;
    private volatile long timeoutMillis;

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public SearchInputManager(PluginSettings settings, GuiSettings gui, Messages messages,
                              PermissionNames permissions, SearchService search,
                              NavigationManager navigation, MenuFactory menus,
                              String cancelWord, long timeoutSeconds) {
        this.settings = settings;
        this.gui = gui;
        this.messages = messages;
        this.permissions = permissions;
        this.search = search;
        this.navigation = navigation;
        this.menus = menus;
        applyInputSettings(cancelWord, timeoutSeconds);
    }

    /**
     * Re-applies configuration after a reload.
     *
     * <p>Any prompt still waiting is dropped: its timeout task, messages and return menu all came
     * from the previous configuration, and leaving one alive would restore a stale screen.</p>
     */
    public void configure(PluginSettings settings, GuiSettings gui, Messages messages,
                          PermissionNames permissions, MenuFactory menus) {
        for (UUID playerId : List.copyOf(pending.keySet())) {
            forget(playerId);
        }
        this.settings = settings;
        this.gui = gui;
        this.messages = messages;
        this.permissions = permissions;
        this.menus = menus;
        PluginSettings.InputSettings input = settings.input();
        applyInputSettings(input.cancelWord(), input.timeoutSeconds());
    }

    private void applyInputSettings(String word, long timeoutSeconds) {
        this.cancelWord = word == null || word.isBlank() ? DEFAULT_CANCEL_WORD : word.trim();
        this.timeoutMillis = Math.max(1L, timeoutSeconds) * 1000L;
    }

    /** Asks the player for a search string, remembering where to return on cancel. */
    public void prompt(Player player, WikiMenu returnTo) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String language = messages.resolve(navigation.session(player).language());
        if (!player.hasPermission(permissions.search())) {
            messages.send(player, language, "no-permission", Map.of());
            gui.sounds().error().play(player);
            return;
        }
        cancel(player, false);
        long expiresAt = System.currentTimeMillis() + timeoutMillis;
        PlatformScheduler.ScheduledTask task = PlatformScheduler.runEntityLater(player,
                () -> expire(player), Math.max(1L, timeoutMillis / 50L));
        pending.put(player.getUniqueId(), new Pending(returnTo, expiresAt, task));
        messages.send(player, language, "input-prompt", Map.of(
                "cancel", cancelWord,
                "timeout", Long.toString(Math.max(1L, timeoutMillis / 1000L))));
        gui.sounds().search().play(player);
    }

    /** True when a chat request is waiting for this player. */
    public boolean isWaiting(UUID playerId) {
        return pending.containsKey(playerId);
    }

    /** Drops a pending request without messaging anyone. */
    public void cancel(Player player, boolean restore) {
        if (player == null) {
            return;
        }
        Pending removed = pending.remove(player.getUniqueId());
        if (removed == null) {
            return;
        }
        removed.timeoutTask.cancel();
        if (restore && removed.returnTo != null && player.isOnline()) {
            navigation.open(player, removed.returnTo);
        }
    }

    /** Called on disconnect so a pending request cannot outlive the player. */
    public void forget(UUID playerId) {
        Pending removed = pending.remove(playerId);
        if (removed != null) {
            removed.timeoutTask.cancel();
        }
    }

    public int waitingCount() {
        return pending.size();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending request = pending.get(player.getUniqueId());
        if (request == null) {
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        String language = messages.resolve(navigation.session(player).language());

        if (text.startsWith("/") && !settings.input().rejectCommands()) {
            // Give up the prompt and leave the line untouched. Real commands never reach this
            // listener on Paper - they are preprocessed instead - so this is the safety net for
            // anything else that starts with a slash; the important part is that it is not
            // swallowed as a search query and not cancelled on the way past.
            finish(player, request);
            messages.send(player, language, "input-cancelled", Map.of());
            restore(player, request);
            return;
        }
        event.setCancelled(true);

        if (text.equalsIgnoreCase(cancelWord)) {
            finish(player, request);
            messages.send(player, language, "input-cancelled", Map.of());
            restore(player, request);
            return;
        }
        if (text.startsWith("/")) {
            // Never treat a command line as a query; keep waiting for real input.
            messages.send(player, language, "input-command-ignored", Map.of("cancel", cancelWord));
            return;
        }
        if (text.isEmpty()) {
            messages.send(player, language, "search-empty", Map.of());
            return;
        }
        if (request.expiresAt < System.currentTimeMillis()) {
            finish(player, request);
            messages.send(player, language, "input-timeout", Map.of());
            restore(player, request);
            return;
        }
        finish(player, request);
        submit(player, text, language, request);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    /** Scores the query on this thread, then opens the result screen on the player's thread. */
    private void submit(Player player, String text, String language, Pending request) {
        SearchService.Outcome outcome = search.search(player.getUniqueId(), text, null,
                player.hasPermission(permissions.bypassCooldown()), System.currentTimeMillis());
        switch (outcome.status()) {
            case COOLDOWN -> {
                messages.send(player, language, "search-cooldown", Map.of(
                        "seconds", formatSeconds(outcome.retryAfterMillis())));
                gui.sounds().error().play(player);
                restore(player, request);
            }
            case TOO_SHORT -> {
                messages.send(player, language, "search-too-short", Map.of(
                        "min", Integer.toString(settings.search().minQueryLength())));
                gui.sounds().error().play(player);
                restore(player, request);
            }
            case TOO_LONG -> {
                messages.send(player, language, "search-too-long", Map.of(
                        "max", Integer.toString(settings.search().maxQueryLength())));
                gui.sounds().error().play(player);
                restore(player, request);
            }
            case EMPTY -> {
                messages.send(player, language, "search-empty", Map.of());
                restore(player, request);
            }
            case NO_RESULTS -> {
                // Still open the result screen: its empty state is more useful than a chat line.
                gui.sounds().error().play(player);
                openResults(player, outcome);
            }
            case OK -> {
                gui.sounds().search().play(player);
                openResults(player, outcome);
            }
        }
    }

    private void openResults(Player player, SearchService.Outcome outcome) {
        SearchQuery query = outcome.query();
        java.util.List<SearchResult> results = outcome.results();
        navigation.open(player, menus.searchResults(player, query, results, 1));
    }

    private void expire(Player player) {
        Pending request = pending.remove(player.getUniqueId());
        if (request == null || !player.isOnline()) {
            return;
        }
        String language = messages.resolve(navigation.session(player).language());
        messages.send(player, language, "input-timeout", Map.of());
        gui.sounds().error().play(player);
        restore(player, request);
    }

    private void restore(Player player, Pending request) {
        if (request.returnTo != null && player.isOnline() && !request.returnTo.disposed()) {
            navigation.open(player, request.returnTo);
        }
    }

    private void finish(Player player, Pending request) {
        pending.remove(player.getUniqueId());
        request.timeoutTask.cancel();
    }

    private static String formatSeconds(long millis) {
        long seconds = (millis + 999L) / 1000L;
        return Long.toString(Math.max(1L, seconds));
    }

    /** The configured cancel word, exposed for the help text. */
    public String cancelWord() {
        return cancelWord;
    }

    /** Sanitises a query typed in chat before it is echoed anywhere. */
    public static String sanitize(String input) {
        return Text.sanitize(input);
    }
}
