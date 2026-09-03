package org.bukkit.inventory.meta;
import java.util.List;
import net.kyori.adventure.text.Component;
public interface ItemMeta {
    void displayName(Component component);
    void lore(List<Component> lore);
}
