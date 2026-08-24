package io.papermc.paper.registry;
public interface RegistryAccess {
    static RegistryAccess registryAccess() { throw new UnsupportedOperationException(); }
    <T extends org.bukkit.Keyed> org.bukkit.Registry<T> getRegistry(RegistryKey<T> key);
}
