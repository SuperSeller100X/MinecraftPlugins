package dev.superseller.easymending.model;

/**
 * Detailed estimate for repairing a collection of items.
 */
public record RepairEstimate(
        int eligibleItemsCount,
        int totalMissingDurability,
        int totalXpCost,
        int availableXp,
        boolean canAffordFull,
        boolean canAffordPartial,
        int affordableDurability
) {
    public boolean hasRepairableItems() {
        return eligibleItemsCount > 0 && totalMissingDurability > 0;
    }
}
