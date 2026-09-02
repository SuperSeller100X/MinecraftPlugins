package dev.superseller.combattag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.superseller.combattag.util.CommandMatcher;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommandMatcherTest {

    @Test
    @DisplayName("labels strip slashes, namespaces and arguments")
    void labels() {
        assertEquals("home", CommandMatcher.label("/home base"));
        assertEquals("home", CommandMatcher.label("/essentials:home"));
        assertEquals("shop", CommandMatcher.label("shop"));
        assertEquals("", CommandMatcher.label(null));
    }

    @Test
    @DisplayName("plain, namespaced and wildcard entries all match")
    void matching() {
        Set<String> entries = Set.of("home", "essentials:warp", "sell*");
        assertTrue(CommandMatcher.matches("/home", entries));
        assertTrue(CommandMatcher.matches("/essentials:home 1", entries));
        assertTrue(CommandMatcher.matches("/warp pvp", entries));
        assertTrue(CommandMatcher.matches("/sellall hand", entries));
        assertFalse(CommandMatcher.matches("/msg friend hi", entries));
    }

    @Test
    @DisplayName("sub-command entries only match with their argument")
    void subCommands() {
        Set<String> entries = Set.of("em hand");
        assertTrue(CommandMatcher.matches("/em hand", entries));
        assertFalse(CommandMatcher.matches("/em info", entries));
    }
}
