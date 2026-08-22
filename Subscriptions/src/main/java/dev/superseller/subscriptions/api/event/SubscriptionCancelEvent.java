package dev.superseller.subscriptions.api.event;

import dev.superseller.subscriptions.model.Plan;
import dev.superseller.subscriptions.model.Subscription;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class SubscriptionCancelEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Subscription subscription;
    private final Plan plan;
    private final String reason;

    public SubscriptionCancelEvent(Subscription subscription, Plan plan, String reason) {
        super(true);
        this.subscription = subscription;
        this.plan = plan;
        this.reason = reason;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public Plan getPlan() {
        return plan;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
