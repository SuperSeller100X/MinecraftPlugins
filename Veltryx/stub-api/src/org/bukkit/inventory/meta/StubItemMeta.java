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
public class StubItemMeta implements ItemMeta {
    public Component name;
    private List<Component> lore = List.of();
    private int damage;
    private final Map<Enchantment,Integer> enchants = new HashMap<>();
    private final StubPersistentDataContainer pdc = new StubPersistentDataContainer();
    public void displayName(Component name) { this.name = name; }
    public void lore(List<Component> lines) { this.lore = lines; }
    public List<Component> lore() { return lore; }
    public boolean addEnchant(Enchantment e, int level, boolean ignore) { enchants.put(e,level); return true; }
    public Map<Enchantment,Integer> getEnchants() { return new HashMap<>(enchants); }
    public PersistentDataContainer getPersistentDataContainer() { return pdc; }
    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = damage; }
    public StubItemMeta copy() {
        StubItemMeta result;
        try { result = getClass().getDeclaredConstructor().newInstance(); }
        catch (ReflectiveOperationException e) { result = new StubItemMeta(); }
        result.name=name; result.lore=new ArrayList<>(lore); result.damage=damage;
        result.enchants.putAll(enchants); result.pdc.values.putAll(pdc.values);
        return result;
    }
    public boolean same(StubItemMeta other) {
        String a=name instanceof StubComponent c ? c.content : String.valueOf(name);
        String b=other.name instanceof StubComponent c ? c.content : String.valueOf(other.name);
        return a.equals(b) && damage==other.damage && enchants.equals(other.enchants) && pdc.values.equals(other.pdc.values);
    }
}
