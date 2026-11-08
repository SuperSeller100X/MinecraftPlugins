package io.papermc.paper.registry;
public interface RegistryAccess {
    final class TestState { public static RegistryAccess access; }
    static RegistryAccess registryAccess() { return TestState.access; }
    <T extends org.bukkit.Keyed> org.bukkit.Registry<T> getRegistry(RegistryKey<T> key);
}
