package org.bukkit.inventory.meta;

public interface Damageable extends ItemMeta {
    int getDamage();
    void setDamage(int damage);
    boolean hasMaxDamage();
    int getMaxDamage();
    void setMaxDamage(Integer maxDamage);
}
