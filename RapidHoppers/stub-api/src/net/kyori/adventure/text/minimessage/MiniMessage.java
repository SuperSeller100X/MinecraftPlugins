package net.kyori.adventure.text.minimessage;
import net.kyori.adventure.text.Component;
public interface MiniMessage {
    static MiniMessage miniMessage() {
        return input -> new Component() { public String toString() { return input; } };
    }
    Component deserialize(String input);
}
