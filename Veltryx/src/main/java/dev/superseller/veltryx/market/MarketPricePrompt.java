package dev.superseller.veltryx.market;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.PlatformScheduler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** One private, cancellable price entry per player, shared by both market menus. */
public final class MarketPricePrompt implements Listener {
    private static final class Pending {
        final Consumer<String> accepted;
        final Runnable cancelled;
        final java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean();
        Pending(Consumer<String> accepted, Runnable cancelled) { this.accepted = accepted; this.cancelled = cancelled; }
    }
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Messages messages;
    private Consumer<Player> cancelOtherPrompt = player -> { };

    public MarketPricePrompt(Messages messages) { this.messages = messages; }
    public void attachPromptCancellation(Consumer<Player> cancel) { this.cancelOtherPrompt = cancel; }

    /** Schedule the close outside an inventory click; no item or funds move until confirmation. */
    public void ask(Player player, Consumer<String> accepted, Runnable cancelled) {
        PlatformScheduler.runForPlayer(player, () -> {
            cancelOtherPrompt.accept(player);
            cancelPending(player);
            player.closeInventory();
            pending.put(player.getUniqueId(), new Pending(accepted, cancelled));
            messages.send(player, "market.price-prompt");
        });
    }

    public void cancelPending(Player player) {
        if (pending.remove(player.getUniqueId()) != null) messages.send(player, "market.price-cancelled");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Pending session = pending.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true); // do not broadcast the amount, even if another chat raced it
        if (!session.claimed.compareAndSet(false, true)) return;
        String input = event.getMessage().trim();
        PlatformScheduler.runForPlayer(player, () -> {
            // A command, another prompt, or a disconnect can cancel before this task runs.
            if (!pending.remove(player.getUniqueId(), session)) return;
            if (input.equalsIgnoreCase("cancel")) {
                messages.send(player, "market.price-cancelled");
                session.cancelled.run();
            } else session.accepted.accept(input);
        });
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { pending.remove(event.getPlayer().getUniqueId()); }
}
