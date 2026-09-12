package dev.superseller.minecraftwiki.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Guards the startup path that only runs against a live server.
 *
 * <p>These tests do not capture registries - that needs Bukkit - but they pin the two invariants
 * whose absence disabled the plugin on a real server: a shared immutable fallback list must
 * never be sorted in place, and a snapshot must be constructible with no content at all.</p>
 */
class RegistrySnapshotTest {

    @Test
    void sortingTheSharedEmptyFallbackThrows() {
        // This is the exact call that took the plugin down at startup: a material with no tags
        // falls back to List.of(), and the JDK refuses to sort an immutable list. Any capture code
        // that sorts must copy first.
        boolean threw = false;
        try {
            Collections.sort(List.of());
        } catch (UnsupportedOperationException expected) {
            threw = true;
        }
        assertTrue(threw, "Collections.sort on an immutable list must fail, so capture must copy first");
    }

    @Test
    void anEmptySnapshotIsUsable() {
        RegistrySnapshot snapshot = RegistrySnapshot.empty();
        assertEquals("", snapshot.minecraftVersion());
        assertEquals(0, snapshot.materials().size());
        assertEquals(0, snapshot.entities().size());
        assertEquals(0, snapshot.enchantments().size());
        assertEquals(0, snapshot.effects().size());
        assertEquals(0, snapshot.potions().size());
        assertEquals(0, snapshot.biomes().size());
        assertEquals(0, snapshot.structures().size());
        assertEquals(0, snapshot.particles().size());
        assertEquals(0, snapshot.sounds().size());
        assertEquals(0, snapshot.attributes().size());
        assertEquals(0, snapshot.damageTypes().size());
        assertEquals(0, snapshot.gameEvents().size());
        assertEquals(0, snapshot.villagerProfessions().size());
        assertEquals(0, snapshot.advancements().size());
        assertEquals(0, snapshot.gamerules().size());
        assertEquals(0, snapshot.tags().size());
        assertEquals(0, snapshot.commands().size());
        assertEquals(0, snapshot.dimensions().size());
        assertEquals(0, snapshot.recipeCount());
        assertEquals(0, snapshot.recipeResults().size());
    }

    @Test
    void lookupsOnAnEmptySnapshotReturnEmptyRatherThanNull() {
        RegistrySnapshot snapshot = RegistrySnapshot.empty();
        assertEquals(0, snapshot.recipesFor("diamond_sword").size());
        assertNull(snapshot.material("diamond"), "an unknown material must not produce an entry");
        assertNull(snapshot.entity("zombie"), "an unknown entity must not produce an entry");
    }
}
