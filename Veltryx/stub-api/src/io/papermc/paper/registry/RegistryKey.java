package io.papermc.paper.registry;
public final class RegistryKey<T> {
    public static final RegistryKey<org.bukkit.enchantments.Enchantment> ENCHANTMENT = new RegistryKey<>();
    public static final RegistryKey<org.bukkit.Sound> SOUND_EVENT = new RegistryKey<>();
    private RegistryKey() {}
}
