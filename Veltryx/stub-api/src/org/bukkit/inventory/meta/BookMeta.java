package org.bukkit.inventory.meta;
import net.kyori.adventure.text.Component;
public interface BookMeta extends ItemMeta {
    BookMeta title(Component title);
    BookMeta author(Component author);
    void addPages(Component... pages);
}
