package org.bukkit.inventory.meta;
import net.kyori.adventure.text.Component;
public class StubBookMeta extends StubItemMeta implements BookMeta {
    public Component title, author;
    public final java.util.List<Component> pages = new java.util.ArrayList<>();
    public BookMeta title(Component value) { title=value; return this; }
    public BookMeta author(Component value) { author=value; return this; }
    public void addPages(Component... value) { pages.addAll(java.util.List.of(value)); }
}
