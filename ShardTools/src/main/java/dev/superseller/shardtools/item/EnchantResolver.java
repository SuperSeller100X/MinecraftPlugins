package dev.superseller.shardtools.item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

/**
 * Resolves config enchant ids ("efficiency", "silk_touch", "wind_burst") to
 * vanilla enchantment instances through the modern Paper registry API.
 * Unknown enchants are logged and skipped, never fatal.
 */
public final class EnchantResolver {

    private final Logger logger;
    private final Map<String, Enchantment> cache = new HashMap<>();
    private final Set<String> warned = new HashSet<>();

    public EnchantResolver(Logger logger) {
        this.logger = logger;
    }

    public Enchantment resolve(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String key = id.replace("minecraft:", "");
        Enchantment cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
            Enchantment enchantment = registry.get(NamespacedKey.minecraft(key));
            if (enchantment != null) {
                cache.put(key, enchantment);
                return enchantment;
            }
        } catch (Throwable error) {
            logger.warning("Could not access enchantment registry: " + error.getMessage());
            return null;
        }
        if (warned.add(key)) {
            logger.warning("Unknown enchantment '" + key + "' (skipped)");
        }
        return null;
    }
}
