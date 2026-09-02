package dev.superseller.combattag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.superseller.combattag.combat.BlockedAction;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockedActionTest {

    @Test
    @DisplayName("every action maps to a declared combattag.bypass.* permission")
    void permissions() {
        Set<String> expected = Set.of(
                "combattag.bypass.shop",
                "combattag.bypass.teleport",
                "combattag.bypass.easymending",
                "combattag.bypass.command");
        Set<String> actual = new HashSet<>();
        for (BlockedAction action : BlockedAction.values()) {
            assertTrue(action.bypassPermission().startsWith("combattag.bypass."),
                    action + " must use a combattag.bypass.* permission");
            actual.add(action.bypassPermission());
        }
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("pearls reuse the teleport bypass so one toggle covers both")
    void pearlSharesTeleport() {
        assertEquals(BlockedAction.TELEPORT.bypassPermission(), BlockedAction.PEARL.bypassPermission());
    }
}
