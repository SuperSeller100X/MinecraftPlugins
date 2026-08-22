package dev.superseller.subscriptions.model;

import java.util.UUID;

public final class Subscription {

    private final String id;
    private final String planId;
    private final UUID subscriberId;
    private String subscriberName;
    private SubscriptionStatus status;
    private ChargePolicy policyOverride;
    private int cycles;
    private long nextChargeAt;
    private long createdAt;
    private long cancelledAt;
    private String cancelReason;
    private boolean autoRenew = true;

    public Subscription(String id, String planId, UUID subscriberId) {
        this.id = id;
        this.planId = planId;
        this.subscriberId = subscriberId;
        this.status = SubscriptionStatus.ACTIVE;
        this.createdAt = System.currentTimeMillis();
        this.nextChargeAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String planId() {
        return planId;
    }

    public UUID subscriberId() {
        return subscriberId;
    }

    public String subscriberName() {
        return subscriberName == null ? "Unknown" : subscriberName;
    }

    public void subscriberName(String subscriberName) {
        this.subscriberName = subscriberName;
    }

    public SubscriptionStatus status() {
        return status == null ? SubscriptionStatus.ACTIVE : status;
    }

    public void status(SubscriptionStatus status) {
        this.status = status;
    }

    public ChargePolicy policyOverride() {
        return policyOverride;
    }

    public void policyOverride(ChargePolicy policyOverride) {
        this.policyOverride = policyOverride;
    }

    public ChargePolicy effectivePolicy(Plan plan) {
        return BillingDecision.effective(plan == null ? ChargePolicy.PAUSE : plan.policy(), policyOverride);
    }

    public int cycles() {
        return cycles;
    }

    public void cycles(int cycles) {
        this.cycles = Math.max(0, cycles);
    }

    public void incrementCycles() {
        this.cycles++;
    }

    public long nextChargeAt() {
        return nextChargeAt;
    }

    public void nextChargeAt(long nextChargeAt) {
        this.nextChargeAt = nextChargeAt;
    }

    public void bump(long intervalMs) {
        long base = Math.max(System.currentTimeMillis(), nextChargeAt);
        this.nextChargeAt = base + Math.max(1_000L, intervalMs);
    }

    public long createdAt() {
        return createdAt;
    }

    public void createdAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long cancelledAt() {
        return cancelledAt;
    }

    public void cancelledAt(long cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String cancelReason() {
        return cancelReason == null ? "" : cancelReason;
    }

    public void cancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public boolean autoRenew() {
        return autoRenew;
    }

    public void autoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public boolean inTrial(Plan plan) {
        return plan != null && plan.trialCycles() > 0 && cycles < plan.trialCycles();
    }

    public boolean reachedMaxCycles(Plan plan) {
        return plan != null && plan.maxCycles() > 0 && cycles >= plan.maxCycles();
    }
}
