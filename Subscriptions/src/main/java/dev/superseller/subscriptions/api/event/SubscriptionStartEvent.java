package dev.superseller.subscriptions.api.event;

import dev.superseller.subscriptions.model.Plan;
import dev.superseller.subscriptions.model.Subscription;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class SubscriptionStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Subscription subscription;
    private final Plan plan;
    private boolean cancelled;

    public SubscriptionStartEvent(Subscription subscription, Plan plan) {
        this.subscription = subscription;
        this.plan = plan;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public Plan getPlan() {
        return plan;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
