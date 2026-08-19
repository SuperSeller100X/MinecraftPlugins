package org.bukkit;

public class NamespacedKey {
    private final String key;

    public NamespacedKey(String namespace, String key) {
        this.key = namespace + ":" + key;
    }

    public String getNamespace() {
        int i = key.indexOf(':');
        return key.substring(0, i);
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }
}
