package org.bukkit.inventory.meta;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.persistence.PersistentDataContainer;
public interface ItemMeta {
    void displayName(Component name);
    void lore(List<Component> lore);
    List<Component> lore();
    boolean addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestrictions);
    PersistentDataContainer getPersistentDataContainer();
    void setItemModel(NamespacedKey itemModel);
    NamespacedKey getItemModel();
}
