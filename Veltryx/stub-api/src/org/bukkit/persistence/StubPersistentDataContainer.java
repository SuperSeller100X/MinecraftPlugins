package org.bukkit.persistence;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
/** Simple test-only PDC; never packaged in the plugin jar. */
public final class StubPersistentDataContainer implements PersistentDataContainer {
    public final Map<NamespacedKey,Object> values = new HashMap<>();
    public <T,Z> void set(NamespacedKey key, PersistentDataType<T,Z> type, Z value){values.put(key,value);}
    @SuppressWarnings("unchecked") public <T,Z> Z get(NamespacedKey key, PersistentDataType<T,Z> type){return (Z)values.get(key);}
    @SuppressWarnings("unchecked") public <T,Z> T getOrDefault(NamespacedKey key, PersistentDataType<T,Z> type, T fallback){return (T)values.getOrDefault(key,fallback);}
    public void remove(NamespacedKey key){values.remove(key);}
    public <T,Z> boolean has(NamespacedKey key, PersistentDataType<T,Z> type){return values.containsKey(key);}
}
