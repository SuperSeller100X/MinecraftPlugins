package net.kyori.adventure.text.minimessage.tag.resolver;

public interface TagResolver {
    static TagResolver resolver(Iterable<? extends TagResolver> resolvers) { return null; }
    static TagResolver resolver(TagResolver... resolvers) { return null; }
}
