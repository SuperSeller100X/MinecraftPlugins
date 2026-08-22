package dev.superseller.subscriptions.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void planIdsAreStableSlugs() {
        String id = Ids.planId("Diamond Kit!!!");
        assertTrue(id.startsWith("diamond-kit-"));
        assertTrue(id.length() > "diamond-kit-".length());
        assertFalse(Ids.subscriptionId().isBlank());
    }
}
