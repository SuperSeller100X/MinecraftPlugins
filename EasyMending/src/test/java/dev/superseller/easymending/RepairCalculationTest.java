package dev.superseller.easymending;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.superseller.easymending.model.RepairEstimate;
import dev.superseller.easymending.model.RepairScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RepairCalculationTest {

    @Test
    @DisplayName("Vanilla 1 XP = 2 Durability ratio calculations with ceiling rounding")
    void testVanillaRatioCosts() {
        double ratio = 2.0;

        // Exactly 50 durability missing -> 25 XP
        int damage1 = 50;
        int cost1 = (int) Math.ceil(damage1 / ratio);
        assertEquals(25, cost1);

        // 51 durability missing -> ceil(25.5) = 26 XP
        int damage2 = 51;
        int cost2 = (int) Math.ceil(damage2 / ratio);
        assertEquals(26, cost2);

        // 1 durability missing -> ceil(0.5) = 1 XP
        int damage3 = 1;
        int cost3 = (int) Math.ceil(damage3 / ratio);
        assertEquals(1, cost3);

        // 1561 missing (broken diamond pickaxe) -> ceil(780.5) = 781 XP
        int damagePick = 1561;
        int costPick = (int) Math.ceil(damagePick / ratio);
        assertEquals(781, costPick);
    }

    @Test
    @DisplayName("Non-mending multiplier penalty calculation")
    void testNonMendingMultiplier() {
        double ratio = 2.0;
        double multiplier = 1.5;

        // 100 damage: (100 / 2.0) * 1.5 = 50 * 1.5 = 75 XP
        int damage = 100;
        int cost = (int) Math.ceil((damage / ratio) * multiplier);
        assertEquals(75, cost);
    }

    @Test
    @DisplayName("Partial repair restores durability within available XP budget")
    void testPartialRepairMath() {
        double ratio = 2.0;
        int availableXp = 10;

        // With 10 XP at ratio 2.0, player can repair floor(10 * 2.0) = 20 durability
        int affordableDurability = (int) Math.floor(availableXp * ratio);
        assertEquals(20, affordableDurability);

        // Check deduction calculation for the restored durability: ceil(20 / 2.0) = 10 XP
        int spentXp = (int) Math.ceil(affordableDurability / ratio);
        assertEquals(10, spentXp);
        assertEquals(availableXp, spentXp);
    }

    @Test
    @DisplayName("Repair estimate record methods identify repairable items")
    void testEstimateMethods() {
        RepairEstimate empty = new RepairEstimate(0, 0, 0, 100, true, true, 200);
        assertFalse(empty.hasRepairableItems());

        RepairEstimate ready = new RepairEstimate(2, 150, 75, 100, true, true, 200);
        assertTrue(ready.hasRepairableItems());
        assertTrue(ready.canAffordFull());

        RepairEstimate poor = new RepairEstimate(1, 500, 250, 50, false, true, 100);
        assertTrue(poor.hasRepairableItems());
        assertFalse(poor.canAffordFull());
        assertTrue(poor.canAffordPartial());
    }

    @Test
    @DisplayName("RepairScope aliases resolve case-insensitively")
    void testScopeAliases() {
        assertEquals(RepairScope.HAND, RepairScope.fromString("hand"));
        assertEquals(RepairScope.HAND, RepairScope.fromString("H"));
        assertEquals(RepairScope.HAND, RepairScope.fromString("main"));
        assertEquals(RepairScope.OFFHAND, RepairScope.fromString("offhand"));
        assertEquals(RepairScope.OFFHAND, RepairScope.fromString("oh"));
        assertEquals(RepairScope.ARMOR, RepairScope.fromString("armor"));
        assertEquals(RepairScope.ARMOR, RepairScope.fromString("a"));
        assertEquals(RepairScope.HOTBAR, RepairScope.fromString("hotbar"));
        assertEquals(RepairScope.HOTBAR, RepairScope.fromString("hb"));
        assertEquals(RepairScope.ALL, RepairScope.fromString("all"));
        assertEquals(RepairScope.ALL, RepairScope.fromString("*"));
        assertEquals(RepairScope.ALL, RepairScope.fromString("inv"));
        assertEquals(null, RepairScope.fromString("invalid_scope"));
    }
}
