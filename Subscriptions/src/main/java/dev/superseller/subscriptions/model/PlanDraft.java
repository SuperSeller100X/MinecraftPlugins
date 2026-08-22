package dev.superseller.subscriptions.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** In-memory wizard state while a player creates or edits a plan. */
public final class PlanDraft {

    public String editingPlanId;
    public String name = "Untitled plan";
    public String description = "";
    public String category = "CUSTOM";
    public double price = 1000d;
    public double signupFee = 0d;
    public long intervalMs = 3_600_000L;
    public ChargePolicy policy = ChargePolicy.PAUSE;
    public int maxSubscribers = 50;
    public int maxCycles = 0;
    public int trialCycles = 0;
    public boolean infiniteStock;
    public final List<Reward> rewards = new ArrayList<>();
    public String icon = "CHEST";
    public UUID ownerId;
    public String ownerName;

    public static PlanDraft from(Plan plan) {
        PlanDraft draft = new PlanDraft();
        draft.editingPlanId = plan.id();
        draft.name = plan.name();
        draft.description = plan.description();
        draft.category = plan.category();
        draft.price = plan.price();
        draft.signupFee = plan.signupFee();
        draft.intervalMs = plan.intervalMs();
        draft.policy = plan.policy();
        draft.maxSubscribers = plan.maxSubscribers();
        draft.maxCycles = plan.maxCycles();
        draft.trialCycles = plan.trialCycles();
        draft.infiniteStock = plan.infiniteStock();
        draft.rewards.addAll(plan.rewards());
        draft.icon = plan.icon();
        draft.ownerId = plan.ownerId();
        draft.ownerName = plan.ownerName();
        return draft;
    }

    public void applyTo(Plan plan) {
        plan.name(name);
        plan.description(description);
        plan.category(category);
        plan.price(price);
        plan.signupFee(signupFee);
        plan.intervalMs(intervalMs);
        plan.policy(policy);
        plan.maxSubscribers(maxSubscribers);
        plan.maxCycles(maxCycles);
        plan.trialCycles(trialCycles);
        plan.infiniteStock(infiniteStock);
        plan.rewards().clear();
        plan.rewards().addAll(rewards);
        plan.icon(icon);
    }
}
