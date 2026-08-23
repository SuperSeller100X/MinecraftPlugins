package org.bukkit.plugin;
public class RegisteredServiceProvider<T> {
    private final T provider;
    public RegisteredServiceProvider(T provider) { this.provider = provider; }
    public T getProvider() { return provider; }
}
