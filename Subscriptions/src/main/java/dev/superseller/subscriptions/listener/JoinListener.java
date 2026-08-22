package dev.superseller.subscriptions.listener;

import java.util.Map;

import dev.superseller.subscriptions.config.PluginSettings;
import dev.superseller.subscriptions.gui.GuiManager;
import dev.superseller.subscriptions.model.Subscription;
import dev.superseller.subscriptions.model.SubscriptionStatus;
import dev.superseller.subscriptions.storage.Database;
import dev.superseller.subscriptions.util.Colors;
import dev.superseller.subscriptions.util.Text;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JoinListener implements Listener {

    private final Database database;
    private final PluginSettings settings;
    private final GuiManager gui;

    public JoinListener(Database database, PluginSettings settings, GuiManager gui) {
        this.database = database;
        this.settings = settings;
        this.gui = gui;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!settings.notifyJoin()) {
            return;
        }
        int waiting = database.inboxSize(event.getPlayer().getUniqueId());
        if (waiting > 0) {
            event.getPlayer().sendMessage(Colors.parse(settings.prefix()
                    + Text.apply(settings.msg("inbox.delivered"), Map.of("name", waiting + " plan(s)"))));
        }
        for (Subscription sub : database.subscriptionsOf(event.getPlayer().getUniqueId())) {
            if (sub.status() == SubscriptionStatus.PAUSED_STOCK || sub.status() == SubscriptionStatus.PAUSED_FUNDS) {
                event.getPlayer().sendMessage(Colors.parse(settings.prefix()
                        + Text.apply(settings.msg("sub.auto-paused"), Map.of(
                        "name", sub.planId(),
                        "reason", sub.status().name()))));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gui.clear(event.getPlayer().getUniqueId());
    }
}
