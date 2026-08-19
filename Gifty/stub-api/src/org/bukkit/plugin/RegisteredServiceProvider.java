package org.bukkit.plugin;

public class RegisteredServiceProvider<T> {
    private final T provider;
    private final Plugin plugin;

    public RegisteredServiceProvider(T provider, Plugin plugin) {
        this.provider = provider;
        this.plugin = plugin;
    }

    public T getProvider() {
        return provider;
    }

    public Plugin getPlugin() {
        return plugin;
    }
}
