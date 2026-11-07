package net.kyori.adventure.text.minimessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class MiniMessage {
    private MiniMessage() {}
    public static MiniMessage miniMessage() { return new MiniMessage(); }
    public Component deserialize(String input, TagResolver... resolvers) { return null; }
}
