package dev.superseller.homesgui.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.homesgui.model.HomeLocation;
import dev.superseller.homesgui.model.PlayerHomes;
import dev.superseller.homesgui.scheduler.PlatformScheduler;

/** UTF-8 YAML storage preserving the UUID -> homes -> numeric slot schema. */
public final class HomesStorage {

    private static final int MAX_STORED_SLOT = HomesGUIStorageLimits.MAX_SLOT;
    private static final String HEADER = "HomesGUI player home data.\n"
            + "Schema: <uuid> -> homes -> <numeric slot> -> world/x/y/z/yaw/pitch.\n"
            + "purchased-homes and bonus-homes preserve unlocked slots separately from locations.\n";

    private final JavaPlugin plugin;
    private final Path file;
    private final Path temporaryFile;
    private final Map<UUID, PlayerHomes> profiles = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();
    private final AtomicLong revision = new AtomicLong();
    private final AtomicBoolean writing = new AtomicBoolean();
    private volatile long persistedRevision;
    private volatile boolean loadSucceeded = true;
    private volatile boolean warnedAboutUnsafeStorage;

    public HomesStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("homes.yml");
        this.temporaryFile = plugin.getDataFolder().toPath().resolve("homes.yml.tmp");
    }

    public void load(int defaultFreeSlots) {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                Files.writeString(file, "# " + HEADER.replace("\n", "\n# "), StandardCharsets.UTF_8);
            }
            String contents = Files.readString(file, StandardCharsets.UTF_8);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(contents);
            for (String root : yaml.getKeys(false)) {
                UUID owner;
                try {
                    owner = UUID.fromString(root);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                PlayerHomes profile = new PlayerHomes(owner);
                int highest = loadHomes(yaml.getConfigurationSection(root + ".homes"), profile);
                boolean hasPaidMetadata = yaml.contains(root + ".purchased-homes");
                int purchased = Math.max(0, yaml.getInt(root + ".purchased-homes", 0));
                int bonus = Math.max(0, yaml.getInt(root + ".bonus-homes", 0));
                if (!hasPaidMetadata) {
                    // Older files only recorded homes. Infer enough paid slots to keep every
                    // existing numeric home reachable without importing the sample records.
                    purchased = Math.max(0, highest - Math.max(0, defaultFreeSlots));
                } else {
                    int missingCapacity = Math.max(0, highest - defaultFreeSlots - purchased - bonus);
                    bonus = Math.addExact(bonus, missingCapacity);
                }
                profile.purchasedSlots(Math.min(purchased, HomesGUIStorageLimits.MAX_SLOT));
                profile.bonusSlots(Math.min(bonus, HomesGUIStorageLimits.MAX_SLOT));
                profiles.put(owner, profile);
            }
            persistedRevision = revision.get();
            plugin.getLogger().info("Loaded home data for " + profiles.size() + " player(s).");
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            profiles.clear();
            loadSucceeded = false;
            plugin.getLogger().log(Level.SEVERE,
                    "Could not load homes.yml. The source file has been left untouched; writes are disabled until it is repaired.", exception);
        }
    }

    private int loadHomes(ConfigurationSection section, PlayerHomes profile) {
        if (section == null) {
            return 0;
        }
        int highest = 0;
        for (String rawSlot : section.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(rawSlot);
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Ignoring non-numeric home slot '" + rawSlot + "' for " + profile.owner());
                continue;
            }
            if (slot < 1 || slot > MAX_STORED_SLOT) {
                plugin.getLogger().warning("Ignoring out-of-range home slot '" + rawSlot + "' for " + profile.owner());
                continue;
            }
            ConfigurationSection node = section.getConfigurationSection(rawSlot);
            if (node == null) {
                continue;
            }
            Object worldValue = node.get("world");
            Object xValue = node.get("x");
            Object yValue = node.get("y");
            Object zValue = node.get("z");
            if (!(worldValue instanceof String world) || !(xValue instanceof Number x)
                    || !(yValue instanceof Number y) || !(zValue instanceof Number z)) {
                plugin.getLogger().warning("Ignoring invalid home slot " + rawSlot + " for " + profile.owner());
                continue;
            }
            float yaw = numeric(node.get("yaw"), 0.0).floatValue();
            float pitch = numeric(node.get("pitch"), 0.0).floatValue();
            try {
                profile.putHome(slot, new HomeLocation(world, x.doubleValue(), y.doubleValue(), z.doubleValue(), yaw, pitch));
                highest = Math.max(highest, slot);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid home slot " + rawSlot + " for " + profile.owner()
                        + ": " + exception.getMessage());
            }
        }
        return highest;
    }

    private static Number numeric(Object value, double fallback) {
        return value instanceof Number number ? number : fallback;
    }

    public PlayerHomes profile(UUID owner) {
        return profiles.computeIfAbsent(owner, PlayerHomes::new);
    }

    public List<PlayerHomes> profiles() {
        return List.copyOf(profiles.values());
    }

    public List<UUID> owners() {
        List<UUID> result = new ArrayList<>(profiles.keySet());
        result.sort(Comparator.comparing(UUID::toString));
        return List.copyOf(result);
    }

    public boolean contains(UUID owner) {
        return profiles.containsKey(owner);
    }

    /** Call after mutating a profile. Saves are coalesced and run off tick threads. */
    public void markDirty() {
        if (!loadSucceeded) {
            if (!warnedAboutUnsafeStorage) {
                warnedAboutUnsafeStorage = true;
                plugin.getLogger().severe("Home data changed in memory but homes.yml could not be loaded; refusing to overwrite it. Repair the file and restart.");
            }
            return;
        }
        revision.incrementAndGet();
        saveAsync();
    }

    public void saveIfDirty() {
        if (loadSucceeded && revision.get() != persistedRevision) {
            saveAsync();
        }
    }

    private void saveAsync() {
        if (!loadSucceeded || revision.get() == persistedRevision || !writing.compareAndSet(false, true)) {
            return;
        }
        try {
            PlatformScheduler.runAsync(plugin, () -> {
                boolean saved = writeSnapshot();
                writing.set(false);
                if (saved && revision.get() != persistedRevision) {
                    saveAsync();
                }
            });
        } catch (RuntimeException exception) {
            writing.set(false);
            plugin.getLogger().log(Level.WARNING, "Could not schedule an asynchronous homes.yml save.", exception);
        }
    }

    /** Final synchronous flush used during shutdown. */
    public void flush() {
        if (!loadSucceeded || (revision.get() == persistedRevision && Files.exists(file))) {
            return;
        }
        writeSnapshot();
    }

    private boolean writeSnapshot() {
        synchronized (ioLock) {
            long writtenRevision = revision.get();
            try {
                Files.createDirectories(file.getParent());
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.options().header(HEADER);
                List<PlayerHomes.Snapshot> snapshots = profiles.values().stream()
                        .map(PlayerHomes::snapshot)
                        .sorted(Comparator.comparing(snapshot -> snapshot.owner().toString()))
                        .toList();
                for (PlayerHomes.Snapshot snapshot : snapshots) {
                    ConfigurationSection player = yaml.createSection(snapshot.owner().toString());
                    ConfigurationSection homes = player.createSection("homes");
                    for (Map.Entry<Integer, HomeLocation> entry : snapshot.homes().entrySet()) {
                        HomeLocation home = entry.getValue();
                        ConfigurationSection location = homes.createSection(Integer.toString(entry.getKey()));
                        location.set("world", home.world());
                        location.set("x", home.x());
                        location.set("y", home.y());
                        location.set("z", home.z());
                        location.set("yaw", home.yaw());
                        location.set("pitch", home.pitch());
                    }
                    player.set("purchased-homes", snapshot.purchasedSlots());
                    player.set("bonus-homes", snapshot.bonusSlots());
                }
                Files.writeString(temporaryFile, yaml.saveToString(), StandardCharsets.UTF_8);
                try {
                    Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
                }
                persistedRevision = writtenRevision;
                return true;
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not atomically save homes.yml.", exception);
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // Leave the original data file intact even if a temp file remains.
                }
                return false;
            }
        }
    }
}
