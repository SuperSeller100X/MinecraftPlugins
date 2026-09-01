package dev.superseller.easymending.model;

/**
 * Encapsulates the outcome of a repair operation.
 */
public record RepairResult(
        boolean success,
        boolean partial,
        boolean bypassCost,
        int itemsRepaired,
        int totalDurabilityRestored,
        int xpSpent,
        int remainingXp,
        String failureReasonKey,
        String primaryItemName
) {
    public static RepairResult successful(int itemsRepaired, int totalDurabilityRestored, int xpSpent, int remainingXp, boolean bypassCost, String itemName) {
        return new RepairResult(true, false, bypassCost, itemsRepaired, totalDurabilityRestored, xpSpent, remainingXp, null, itemName);
    }

    public static RepairResult partiallySuccessful(int itemsRepaired, int totalDurabilityRestored, int xpSpent, int remainingXp, boolean bypassCost, String itemName) {
        return new RepairResult(true, true, bypassCost, itemsRepaired, totalDurabilityRestored, xpSpent, remainingXp, null, itemName);
    }

    public static RepairResult failure(String failureReasonKey) {
        return new RepairResult(false, false, false, 0, 0, 0, 0, failureReasonKey, null);
    }
}
