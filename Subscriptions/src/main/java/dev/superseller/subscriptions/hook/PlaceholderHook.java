package dev.superseller.subscriptions.hook;

import dev.superseller.subscriptions.SubscriptionsPlugin;
import dev.superseller.subscriptions.storage.Database;

import org.bukkit.Bukkit;

public final class PlaceholderHook {

    private final SubscriptionsPlugin plugin;
    private final Database database;

    public PlaceholderHook(SubscriptionsPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void register() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
            new SubscriptionsExpansion(plugin, database).register();
            plugin.getLogger().info("PlaceholderAPI hook registered (%subscriptions_...%).");
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not register PlaceholderAPI expansion: " + t.getMessage());
        }
    }
}
