package dev.superseller.teleportsigns.storage;

import dev.superseller.teleportsigns.model.SignCodec;
import dev.superseller.teleportsigns.model.SignKey;
import dev.superseller.teleportsigns.model.TeleportSign;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PersistentDataContainer on the sign is the source of truth. {@code signs.yml}
 * is a listing index so {@code /ts list} does not have to scan the world.
 */
public final class SignStore {

    private final JavaPlugin plugin;
    private final NamespacedKey dataKey;
    private final File file;
    private final Map<SignKey, TeleportSign> index = new ConcurrentHashMap<SignKey, TeleportSign>();

    public SignStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataKey = new NamespacedKey(plugin, "destination");
        this.file = new File(plugin.getDataFolder(), "signs.yml");
    }

    public void load() {
        index.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.isConfigurationSection("signs")) {
            return;
        }
        for (String id : yaml.getConfigurationSection("signs").getKeys(false)) {
            String path = "signs." + id;
            String world = yaml.getString(path + ".world", "");
            int x = yaml.getInt(path + ".x");
            int y = yaml.getInt(path + ".y");
            int z = yaml.getInt(path + ".z");
            String encoded = yaml.getString(path + ".data", "");
            SignKey key = new SignKey(world, x, y, z);
            TeleportSign sign = SignCodec.decode(key, encoded);
            if (sign != null) {
                index.put(key, sign);
            }
        }
        plugin.getLogger().info("Loaded " + index.size() + " teleport sign(s) from signs.yml.");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (TeleportSign sign : index.values()) {
            SignKey key = sign.key();
            String path = "signs.s" + i;
            yaml.set(path + ".world", key.worldName());
            yaml.set(path + ".x", key.x());
            yaml.set(path + ".y", key.y());
            yaml.set(path + ".z", key.z());
            yaml.set(path + ".data", SignCodec.encode(sign));
            i++;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save signs.yml: " + e.getMessage());
        }
    }

    public SignKey keyOf(Block block) {
        if (block == null || block.getWorld() == null) {
            return null;
        }
        return new SignKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public TeleportSign read(Sign sign) {
        if (sign == null) {
            return null;
        }
        SignKey key = keyOf(sign.getBlock());
        PersistentDataContainer pdc = sign.getPersistentDataContainer();
        String encoded = pdc.get(dataKey, PersistentDataType.STRING);
        TeleportSign fromPdc = SignCodec.decode(key, encoded);
        if (fromPdc != null) {
            if (!index.containsKey(key)) {
                index.put(key, fromPdc);
            }
            return fromPdc;
        }
        return index.get(key);
    }

    public void write(Sign sign, TeleportSign data, boolean wax) {
        if (sign == null || data == null) {
            return;
        }
        PersistentDataContainer pdc = sign.getPersistentDataContainer();
        pdc.set(dataKey, PersistentDataType.STRING, SignCodec.encode(data));
        if (wax) {
            try {
                sign.setWaxed(true);
            } catch (Throwable ignored) {
                // older API without wax
            }
        }
        sign.update(true, false);
        index.put(data.key(), data);
        save();
    }

    public boolean remove(Sign sign) {
        if (sign == null) {
            return false;
        }
        SignKey key = keyOf(sign.getBlock());
        PersistentDataContainer pdc = sign.getPersistentDataContainer();
        boolean had = pdc.has(dataKey, PersistentDataType.STRING) || index.containsKey(key);
        pdc.remove(dataKey);
        sign.update(true, false);
        if (key != null) {
            index.remove(key);
        }
        if (had) {
            save();
        }
        return had;
    }

    public boolean remove(Block block) {
        if (block == null) {
            return false;
        }
        SignKey key = keyOf(block);
        boolean had = key != null && index.remove(key) != null;
        if (block.getState() instanceof Sign) {
            Sign sign = (Sign) block.getState();
            PersistentDataContainer pdc = sign.getPersistentDataContainer();
            if (pdc.has(dataKey, PersistentDataType.STRING)) {
                pdc.remove(dataKey);
                sign.update(true, false);
                had = true;
            }
        }
        if (had) {
            save();
        }
        return had;
    }

    public Collection<TeleportSign> all() {
        return index.values();
    }

    public List<TeleportSign> nearby(Location origin, double radius, int limit) {
        List<TeleportSign> matches = new ArrayList<TeleportSign>();
        if (origin == null || origin.getWorld() == null) {
            return matches;
        }
        World world = origin.getWorld();
        double r2 = radius * radius;
        for (TeleportSign sign : index.values()) {
            SignKey key = sign.key();
            if (!world.getName().equalsIgnoreCase(key.worldName())) {
                continue;
            }
            double dx = (key.x() + 0.5d) - origin.getX();
            double dy = (key.y() + 0.5d) - origin.getY();
            double dz = (key.z() + 0.5d) - origin.getZ();
            if (dx * dx + dy * dy + dz * dz <= r2) {
                matches.add(sign);
                if (matches.size() >= limit) {
                    break;
                }
            }
        }
        return matches;
    }
}
