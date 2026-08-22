package dev.superseller.subscriptions.model;

/**
 * Pure billing decision. No Bukkit, no I/O — easy to unit-test.
 *
 * <p>Anti-scam rule: a cycle is charged if and only if the subscriber can pay
 * <em>and</em> the seller can fulfil every reward. Otherwise the effective
 * {@link ChargePolicy} (plan default or the subscriber's personal override)
 * decides between pause / skip / cancel. Nothing is charged on those paths.</p>
 */
public final class BillingDecision {

    public enum Action {
        CHARGE,
        TRIAL,
        PAUSE,
        SKIP,
        CANCEL
    }

    private BillingDecision() {
    }

    public static Action decide(boolean canPay, boolean canFulfill, ChargePolicy policy, boolean inTrial) {
        ChargePolicy used = policy == null ? ChargePolicy.PAUSE : policy;
        if (!canPay || !canFulfill) {
            return apply(used);
        }
        return inTrial ? Action.TRIAL : Action.CHARGE;
    }

    /**
     * Subscriber override wins when present; otherwise the plan default is used.
     */
    public static ChargePolicy effective(ChargePolicy planDefault, ChargePolicy subscriberOverride) {
        return subscriberOverride != null ? subscriberOverride : (planDefault == null ? ChargePolicy.PAUSE : planDefault);
    }

    private static Action apply(ChargePolicy policy) {
        return switch (policy) {
            case PAUSE -> Action.PAUSE;
            case SKIP -> Action.SKIP;
            case CANCEL -> Action.CANCEL;
        };
    }
}
