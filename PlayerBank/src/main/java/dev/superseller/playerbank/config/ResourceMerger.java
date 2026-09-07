package dev.superseller.playerbank.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Merges keys that a plugin update added to the bundled {@code config.yml} /
 * {@code messages.yml} into the copies on disk. Existing (possibly customized)
 * values are never touched — only missing keys are copied — so an updated jar
 * never leaves the GUI showing raw message keys like
 * {@code gui.chest.deposit-name}.
 */
public final class ResourceMerger {

    private ResourceMerger() {
    }

    /**
     * Copies every key that exists in the bundled {@code resource} but is
     * missing from {@code file} into that file. Returns how many keys were
     * added (0 when the file is already complete or does not exist).
     */
    public static int merge(JavaPlugin plugin, String resource, File file) {
        if (!file.isFile()) {
            return 0; // saveResource wrote the fresh bundled copy already
        }
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                return 0;
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration disk = YamlConfiguration.loadConfiguration(file);
            List<String> added = new ArrayList<>();
            for (String key : bundled.getKeys(true)) {
                if (bundled.isConfigurationSection(key) || disk.contains(key)) {
                    continue;
                }
                disk.set(key, bundled.get(key));
                added.add(key);
            }
            if (!added.isEmpty()) {
                disk.save(file);
            }
            return added.size();
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().warning("Could not merge " + resource + " with new defaults: "
                    + e.getMessage());
            return 0;
        }
    }
}
