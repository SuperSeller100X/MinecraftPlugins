package dev.superseller.homesgui.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HomeLocationTest {

    @Test
    void rejectsBlankWorldNames() {
        assertThrows(IllegalArgumentException.class, () -> new HomeLocation("  ", 1, 2, 3, 0, 0));
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new HomeLocation("world", Double.NaN, 2, 3, 0, 0));
    }
}
