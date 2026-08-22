package dev.superseller.subscriptions.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.superseller.subscriptions.SubscriptionsPlugin;
import dev.superseller.subscriptions.model.Plan;
import dev.superseller.subscriptions.model.Subscription;

/** Public entry point other plugins can call after {@code softdepend: [Subscriptions]}. */
public final class SubscriptionsAPI {

    private static SubscriptionsAPI instance;

    private final SubscriptionsPlugin plugin;

    public SubscriptionsAPI(SubscriptionsPlugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static SubscriptionsAPI get() {
        return instance;
    }

    public Optional<Plan> plan(String id) {
        return Optional.ofNullable(plugin.database().plan(id));
    }

    public Collection<Plan> plans() {
        return new ArrayList<>(plugin.database().plans());
    }

    public Optional<Subscription> subscription(String id) {
        return Optional.ofNullable(plugin.database().subscription(id));
    }

    public List<Subscription> subscriptions(UUID player) {
        return plugin.database().subscriptionsOf(player);
    }

    public int inboxSize(UUID player) {
        return plugin.database().inboxSize(player);
    }

    public double spent(UUID player) {
        return plugin.database().spent(player);
    }

    public double earned(UUID player) {
        return plugin.database().earned(player);
    }

    public boolean economyEnabled() {
        return plugin.economy().isEnabled();
    }

    public String economyName() {
        return plugin.economy().providerName();
    }
}
