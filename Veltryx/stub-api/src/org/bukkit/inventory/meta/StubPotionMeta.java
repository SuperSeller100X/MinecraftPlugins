package org.bukkit.inventory.meta;
public class StubPotionMeta extends StubItemMeta implements PotionMeta {
    private org.bukkit.potion.PotionType base;
    private final java.util.List<org.bukkit.potion.PotionEffect> effects = new java.util.ArrayList<>();
    public void setColor(org.bukkit.Color color) { }
    public boolean addCustomEffect(org.bukkit.potion.PotionEffect effect, boolean override) { effects.add(effect); return true; }
    public boolean hasCustomEffects() { return !effects.isEmpty(); }
    public java.util.List<org.bukkit.potion.PotionEffect> getCustomEffects() { return java.util.List.copyOf(effects); }
    public void setBasePotionType(org.bukkit.potion.PotionType type) { base = type; }
    public org.bukkit.potion.PotionType getBasePotionType() { return base; }
}
