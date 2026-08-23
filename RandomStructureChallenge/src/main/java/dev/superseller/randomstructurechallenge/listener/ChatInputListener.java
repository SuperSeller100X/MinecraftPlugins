package dev.superseller.randomstructurechallenge.listener;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.superseller.randomstructurechallenge.RandomStructureChallengePlugin;
import dev.superseller.randomstructurechallenge.challenge.ChallengeManager;
import dev.superseller.randomstructurechallenge.scheduler.PlatformScheduler;
import dev.superseller.randomstructurechallenge.util.DurationParser;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * After {@code /challenge start}, the next chat message is the interval.
 */
public final class ChatInputListener implements Listener {

    private static final Set<String> CANCEL_WORDS = Set.of("cancel", "stop", "abort", "no");

    private final RandomStructureChallengePlugin plugin;

    public ChatInputListener(RandomStructureChallengePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ChallengeManager manager = plugin.manager();
        if (!manager.isAwaiting(player.getUniqueId())) {
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        event.setCancelled(true);

        String lower = text.toLowerCase(Locale.ROOT);
        if (CANCEL_WORDS.contains(lower)) {
            PlatformScheduler.runGlobal(() -> manager.cancelAwait(player, true));
            return;
        }

        DurationParser.Result parsed = DurationParser.parse(text);
        PlatformScheduler.runGlobal(() -> {
            if (!manager.isAwaiting(player.getUniqueId())) {
                return;
            }
            if (!parsed.ok()) {
                plugin.messages().send(player, parsed.errorKey(), Map.of(
                        "input", parsed.input(),
                        "min", Integer.toString(plugin.settings().minInterval()),
                        "max", Integer.toString(plugin.settings().maxInterval())
                ));
                return;
            }
            manager.start(player, parsed.seconds());
        });
    }
}
