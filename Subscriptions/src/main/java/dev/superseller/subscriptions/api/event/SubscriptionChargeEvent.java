package dev.superseller.subscriptions.api.event;

import dev.superseller.subscriptions.model.Plan;
import dev.superseller.subscriptions.model.Subscription;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired just before a successful charge is applied. Cancel to skip the cycle. */
public final class SubscriptionChargeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Subscription subscription;
    private final Plan plan;
    private final double amount;
    private final boolean trial;
    private boolean cancelled;

    public SubscriptionChargeEvent(Subscription subscription, Plan plan, double amount, boolean trial) {
        super(true);
        this.subscription = subscription;
        this.plan = plan;
        this.amount = amount;
        this.trial = trial;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public Plan getPlan() {
        return plan;
    }

    public double getAmount() {
        return amount;
    }

    public boolean isTrial() {
        return trial;
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
