package dev.superseller.hourglass.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Every byte of file I/O in the plugin goes through here.
 *
 * <p>Why: {@link YamlConfiguration#loadConfiguration(java.io.File)} uses the
 * platform default charset, so an accented player name or a non-ASCII message
 * would be mangled on a Windows box running CP1252. Reading and writing with an
 * explicit {@link StandardCharsets#UTF_8}, plus a temp-file + atomic-move write,
 * makes the plugin behave identically on Linux, Windows and macOS — and keeps a
 * crash mid-save from truncating a player's stored playtime.
 */
public final class YamlIO {

    private YamlIO() {
    }

    /**
     * Loads {@code <dataFolder>/<name>} as UTF-8, extracting the packaged
     * default of the same name first when the file does not exist yet. Missing
     * keys fall back to the packaged file, so an admin upgrade that adds options
     * never breaks an old config.
     */
    public static YamlConfiguration loadDefaults(JavaPlugin plugin, String name) {
        Path file = plugin.getDataFolder().toPath().resolve(name);
        if (!Files.exists(file)) {
            try {
                Files.createDirectories(file.getParent());
                extract(plugin, name, file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create " + name + ": " + e.getMessage());
            }
        }
        YamlConfiguration loaded = new YamlConfiguration();
        if (Files.exists(file)) {
            try {
                loaded = YamlConfiguration.loadConfiguration(
                        Files.newBufferedReader(file, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().severe("Could not read " + name + " (" + e.getMessage()
                        + "); using built-in defaults for this file.");
                loaded = new YamlConfiguration();
            }
        }
        YamlConfiguration packaged = loadResource(plugin, name);
        if (packaged != null) {
            loaded.setDefaults(packaged);
        }
        return loaded;
    }

    /** Reads a YAML file from inside the jar (UTF-8), or {@code null}. */
    public static YamlConfiguration loadResource(JavaPlugin plugin, String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Copies a packaged resource to disk byte for byte, keeping comments. */
    public static void extract(JavaPlugin plugin, String name, Path target) throws IOException {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                throw new IOException("resource " + name + " is missing from the jar");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Serialises and writes a configuration as UTF-8, atomically. */
    public static void save(YamlConfiguration config, Path file) throws IOException {
        writeAtomically(file, config.saveToString());
    }

    /** Writes text as UTF-8 through a temp file, then moves it into place. */
    public static void writeAtomically(Path file, String content) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some SMB shares and odd POSIX mounts refuse atomic moves; a plain
            // replace is still correct, just not atomic.
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Reads a whole UTF-8 text file, or {@code null} when it does not exist. */
    public static String read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
