package dev.superseller.minecraftwiki.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class TextTest {

    @Test
    void sanitizeRemovesTagsButKeepsOrdinaryText() {
        assertEquals("hi x", Text.sanitize("<red>hi</red> <click:run_command:'/op me'>x</click>"));
        assertEquals("a & b", Text.sanitize("a & b"));
        assertEquals("open", Text.sanitize("<gradient:#fff>open"));
        assertEquals("", Text.sanitize(null));
    }

    @Test
    void sanitizeStripsEverythingAMaliciousQueryCouldInject() {
        String rendered = PlainTextComponentSerializer.plainText()
                .serialize(Text.mini(Text.sanitize("<red>x</red> <hover:show_text:'pwned'>y</hover>"), Map.of()));
        assertFalse(rendered.contains("red"));
        assertFalse(rendered.contains("hover"));
    }

    @Test
    void foldNormalisesCaseAndWhitespace() {
        assertEquals("diamond sword", Text.fold("  Diamond   SWORD "));
        assertEquals("", Text.fold(null));
        assertEquals(Text.fold("Diamond Sword"), Text.fold("diamond  sword"));
    }

    @Test
    void prettifyTurnsMachineKeysIntoWords() {
        assertEquals("Max Level", Text.prettify("max_level"));
        assertEquals("Blast Resistance", Text.prettify("blast_resistance"));
    }

    @Test
    void joinRendersLists() {
        assertEquals("", Text.join(List.of(), ", "));
        assertEquals("a", Text.join(List.of("a"), ", "));
        assertEquals("a, b, c", Text.join(List.of("a", "b", "c"), ", "));
    }

    @Test
    void miniResolvesPlaceholders() {
        assertEquals("2 of 3", PlainTextComponentSerializer.plainText()
                .serialize(Text.mini("<page> of <pages>", Map.of("page", "2", "pages", "3"))));
    }

    @Test
    void stripNamespaceReducesAnyKeyToTheBareNameUsedByTheSnapshots() {
        assertEquals("diamond", Text.stripNamespace("minecraft:diamond"));
        assertEquals("thing", Text.stripNamespace("mymod:thing"));
        assertEquals("diamond", Text.stripNamespace("diamond"));
        assertNull(Text.stripNamespace(null));
    }
}
