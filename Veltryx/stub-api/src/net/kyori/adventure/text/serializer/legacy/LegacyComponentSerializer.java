package net.kyori.adventure.text.serializer.legacy;

import net.kyori.adventure.text.Component;

/** Compile stub of the Adventure legacy (&/§) text serializer. */
public interface LegacyComponentSerializer {
    static LegacyComponentSerializer legacySection() {
        return new LegacyComponentSerializer() {
            public Component deserialize(String input) { return new net.kyori.adventure.text.StubComponent(input); }
            public String serialize(Component component) { return component.toString(); }
        };
    }
    Component deserialize(String input);
    String serialize(Component component);
}
