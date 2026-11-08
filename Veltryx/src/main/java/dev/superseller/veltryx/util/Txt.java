package dev.superseller.veltryx.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Tiny text helpers. GUI titles and item lore use the familiar {@code §}
 * legacy color codes; this turns them into real Adventure components so they
 * render colored on Paper instead of showing raw symbols.
 */
public final class Txt {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Txt() {
    }

    /** Renders a legacy-coded string (§-colors) as a styled component. */
    public static Component legacy(String text) {
        return LEGACY.deserialize(text);
    }
}
