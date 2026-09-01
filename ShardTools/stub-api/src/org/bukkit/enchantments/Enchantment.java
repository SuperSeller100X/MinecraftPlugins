package org.bukkit.enchantments;
import org.bukkit.Keyed;
public abstract class Enchantment implements Keyed {
    public static final Enchantment SILK_TOUCH = new Enchantment() {
        @Override
        public org.bukkit.NamespacedKey getKey() {
            throw new UnsupportedOperationException();
        }
    };
}
