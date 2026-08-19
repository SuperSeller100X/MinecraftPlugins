package dev.superseller.gifty.input;

import dev.superseller.gifty.GuiSessionManager;
import dev.superseller.gifty.config.GiftyConfig;
import dev.superseller.gifty.scheduler.PlatformScheduler;
import dev.superseller.gifty.util.Colors;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

/**
 * Handles chat-based input prompts (money amount / gift message).
 * The chat event fires on an async thread, so input is captured there and
 * completed on the player's own thread one tick later.
 */
public final class ChatInputManager {

    public enum Kind {
        MONEY,
        MESSAGE
    }

    private static final long TIMEOUT_MS = 60_000L;

    private final GiftyConfig config;
    private GuiSessionManager sessionRef;

    private final Map<UUID, Prompt> pending = new ConcurrentHashMap<>();

    public ChatInputManager(GiftyConfig config) {
        this.config = config;
    }

    /** Breaks the circular construction with GuiSessionManager. */
    public void setSessions(GuiSessionManager sessions) {
        this.sessionRef = sessions;
    }

    private static final class Prompt {
        final Kind kind;
        final UUID target;
        final long started;

        Prompt(Kind kind, UUID target, long started) {
            this.kind = kind;
            this.target = target;
            this.started = started;
        }
    }

    public boolean hasPending(UUID player) {
        return pending.containsKey(player);
    }

    public void prompt(Player player, Kind kind, UUID target) {
        pending.put(player.getUniqueId(), new Prompt(kind, target, System.currentTimeMillis()));
        if (kind == Kind.MONEY) {
            player.sendMessage(Colors.parse(config.msg("prompt.money")));
        } else {
            player.sendMessage(Colors.parse(config.msg("prompt.message")));
        }
        player.closeInventory();
    }

    /**
     * Called from the async chat listener. Captures the input and schedules
     * completion on the player's owning thread.
     */
    public void onChatAsync(Player player, String message) {
        UUID uuid = player.getUniqueId();
        Prompt prompt = pending.remove(uuid);
        if (prompt == null) {
            return;
        }
        PlatformScheduler.runEntitySync(player, () -> complete(player, prompt, message));
    }

    private void complete(Player player, Prompt prompt, String message) {
        String input = message == null ? "" : message.trim();
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("c")
                || input.equalsIgnoreCase("exit")) {
            player.sendMessage(Colors.parse(config.msg("prompt.cancelled")));
            return;
        }
        if (sessionRef != null) {
            sessionRef.handlePromptResult(player, prompt.kind, prompt.target, input);
        }
    }

    /** Called periodically from the global timer — data-only cleanup. */
    public void cleanupTimeouts() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Prompt> e : pending.entrySet()) {
            if (now - e.getValue().started > TIMEOUT_MS) {
                pending.remove(e.getKey());
                notifyTimeout(e.getKey());
            }
        }
    }

    private void notifyTimeout(UUID uuid) {
        org.bukkit.Bukkit.getServer();
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null) {
            PlatformScheduler.runEntitySync(player, () ->
                    player.sendMessage(Colors.parse(config.msg("prompt.timeout"))));
        }
    }

    public void clear(UUID uuid) {
        pending.remove(uuid);
    }
}
