package dev.superseller.veltryx.staff;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.model.MuteEntry;
import dev.superseller.veltryx.punish.MuteService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Chat moderation: mutes and slowchat. Runs on the async chat thread; all
 * lookups are thread-safe.
 */
public final class ChatListener implements Listener {

    private final Messages messages;
    private final MuteService mutes;

    private final Map<UUID, Long> lastChatMillis = new ConcurrentHashMap<>();
    private volatile long slowchatSeconds = 0L;

    public ChatListener(Messages messages, MuteService mutes) {
        this.messages = messages;
        this.mutes = mutes;
    }

    /** Sets the slowchat interval in seconds (0 disables slowchat). */
    public void setSlowchatSeconds(long seconds) {
        this.slowchatSeconds = Math.max(0L, seconds);
    }

    public long slowchatSeconds() {
        return slowchatSeconds;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        Optional<MuteEntry> mute = mutes.activeMute(player.getUniqueId());
        if (mute.isPresent()) {
            event.setCancelled(true);
            long remaining = mute.get().expires() == -1L
                    ? -1L
                    : Math.max(0L, (mute.get().expires() - System.currentTimeMillis()) / 1000L);
            messages.send(player, "mute.blocked", Map.of(
                    "reason", mute.get().reason(),
                    "remaining", remaining == -1L ? "never" : dev.superseller.veltryx.util.TimeUtil.format(remaining)));
            return;
        }

        if (slowchatSeconds > 0 && !player.hasPermission("veltryx.admin.chat")
                && !player.hasPermission("veltryx.staff.chat")) {
            long now = System.currentTimeMillis();
            Long last = lastChatMillis.get(player.getUniqueId());
            if (last != null && now - last < slowchatSeconds * 1000L) {
                event.setCancelled(true);
                long waitSeconds = (slowchatSeconds * 1000L - (now - last)) / 1000L + 1;
                messages.send(player, "slowchat.waiting", Map.of("seconds", String.valueOf(waitSeconds)));
                return;
            }
            lastChatMillis.put(player.getUniqueId(), now);
        }
    }
}
