package net.kyori.adventure.text.serializer.legacy;

import net.kyori.adventure.text.Component;

public interface LegacyComponentSerializer {
    static LegacyComponentSerializer legacySection() { return null; }
    String serialize(Component component);
}
