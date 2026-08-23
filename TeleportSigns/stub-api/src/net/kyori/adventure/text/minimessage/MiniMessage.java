package net.kyori.adventure.text.minimessage;
import net.kyori.adventure.text.Component;
public interface MiniMessage {
    static MiniMessage miniMessage() { return new MiniMessage() {
        public Component deserialize(String input) { return new Component() {}; }
    }; }
    Component deserialize(String input);
}
