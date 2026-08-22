package dev.superseller.subscriptions.api.event;

import dev.superseller.subscriptions.model.Plan;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class PlanCreateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Plan plan;

    public PlanCreateEvent(Plan plan) {
        this.plan = plan;
    }

    public Plan getPlan() {
        return plan;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
