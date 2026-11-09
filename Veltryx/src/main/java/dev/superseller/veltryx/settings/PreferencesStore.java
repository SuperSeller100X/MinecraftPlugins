package dev.superseller.veltryx.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Persistent, per-player switches. Defaults leave vanilla behaviour unchanged. */
public final class PreferencesStore {
    public record Preferences(boolean mobsDisabled, boolean phantomsDisabled, boolean nightVision) {
        public static final Preferences DEFAULT = new Preferences(false, false, false);
        public Preferences toggleMobs() { return new Preferences(!mobsDisabled, phantomsDisabled, nightVision); }
        public Preferences togglePhantoms() { return new Preferences(mobsDisabled, !phantomsDisabled, nightVision); }
        public Preferences toggleNightVision() { return new Preferences(mobsDisabled, phantomsDisabled, !nightVision); }
    }
    private final Path file;
    private final Logger logger;
    private final Map<UUID, Preferences> values = new ConcurrentHashMap<>();

    public PreferencesStore(Path file, Logger logger) { this.file = file; this.logger = logger; }

    public synchronized void load() {
        values.clear();
        if (!Files.exists(file)) return;
        Properties data = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            data.load(in);
            for (String key : data.stringPropertyNames()) {
                try {
                    UUID id = UUID.fromString(key);
                    String[] bits = data.getProperty(key).split(",", -1);
                    if (bits.length != 3) continue;
                    values.put(id, new Preferences(Boolean.parseBoolean(bits[0]),
                            Boolean.parseBoolean(bits[1]), Boolean.parseBoolean(bits[2])));
                } catch (IllegalArgumentException ignored) {
                    logger.warning("Skipping malformed player preference: " + key);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not read player preferences", e);
        }
    }

    public Preferences get(UUID id) { return values.getOrDefault(id, Preferences.DEFAULT); }

    /** Write through immediately so changes survive a restart. */
    public synchronized void set(UUID id, Preferences preferences) {
        if (preferences.equals(Preferences.DEFAULT)) values.remove(id); else values.put(id, preferences);
        save();
    }

    public synchronized void save() {
        Properties data = new Properties();
        values.forEach((id, p) -> data.setProperty(id.toString(),
                p.mobsDisabled() + "," + p.phantomsDisabled() + "," + p.nightVision()));
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                data.store(out, "Veltryx per-player settings");
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save player preferences", e);
        }
    }
}
