package org.bukkit.inventory.meta;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.StubComponent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.StubPersistentDataContainer;
/** In-memory metadata for offline GUI tests. */
public class StubItemMeta implements Damageable {
    public Component name;
    private List<Component> lore = List.of();
    private int damage;
    private org.bukkit.NamespacedKey itemModel;
    private Integer customModelData;
    private final Map<Enchantment,Integer> enchants = new HashMap<>();
    private final StubPersistentDataContainer pdc = new StubPersistentDataContainer();
    public void displayName(Component name) { this.name = name; }
    public Component displayName() { return name; }
    public void lore(List<Component> lines) { this.lore = lines; }
    public List<Component> lore() { return lore; }
    public boolean hasItemModel() { return itemModel != null; }
    public org.bukkit.NamespacedKey getItemModel() { return itemModel; }
    public void setItemModel(org.bukkit.NamespacedKey model) { itemModel = model; }
    public boolean hasCustomModelDataComponent() { return customModelData != null; }
    public void setCustomModelData(Integer data) { customModelData = data; }
    public boolean addEnchant(Enchantment e, int level, boolean ignore) { enchants.put(e,level); return true; }
    public boolean removeEnchant(Enchantment e) { return enchants.remove(e) != null; }
    public Map<Enchantment,Integer> getEnchants() { return new HashMap<>(enchants); }
    public PersistentDataContainer getPersistentDataContainer() { return pdc; }
    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = damage; }
    public StubItemMeta copy() {
        StubItemMeta result;
        try { result = getClass().getDeclaredConstructor().newInstance(); }
        catch (ReflectiveOperationException e) { result = new StubItemMeta(); }
        result.name=name; result.lore=new ArrayList<>(lore); result.damage=damage; result.itemModel=itemModel;
        result.customModelData=customModelData;
        result.enchants.putAll(enchants); result.pdc.values.putAll(pdc.values);
        return result;
    }
    @Override public boolean equals(Object value) {
        return value instanceof StubItemMeta other && same(other) && lore.equals(other.lore);
    }
    @Override public int hashCode() { return java.util.Objects.hash(damage, itemModel, customModelData, enchants, pdc.values, lore); }
    public boolean same(StubItemMeta other) {
        String a=name instanceof StubComponent c ? c.content : String.valueOf(name);
        String b=other.name instanceof StubComponent c ? c.content : String.valueOf(other.name);
        return a.equals(b) && damage==other.damage && java.util.Objects.equals(itemModel, other.itemModel)
                && java.util.Objects.equals(customModelData, other.customModelData)
                && enchants.equals(other.enchants) && pdc.values.equals(other.pdc.values);
    }
}
