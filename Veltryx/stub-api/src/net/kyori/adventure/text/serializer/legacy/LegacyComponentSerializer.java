package net.kyori.adventure.text.serializer.legacy;

import net.kyori.adventure.text.Component;

/** Compile stub of the Adventure legacy (&/§) text serializer. */
public interface LegacyComponentSerializer {
    static LegacyComponentSerializer legacySection() {
        return null;
    }
    Component deserialize(String input);
    String serialize(Component component);
}
