package org.bukkit.inventory.meta;
public class StubPotionMeta extends StubItemMeta implements PotionMeta {
    private org.bukkit.potion.PotionType base;
    public void setColor(org.bukkit.Color color) { }
    public boolean addCustomEffect(org.bukkit.potion.PotionEffect effect, boolean override) { return true; }
    public void setBasePotionType(org.bukkit.potion.PotionType type) { base = type; }
    public org.bukkit.potion.PotionType getBasePotionType() { return base; }
}
