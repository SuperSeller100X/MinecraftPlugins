package dev.superseller.minecraftwiki.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * One YAML file in the plugin data folder, backed by the copy shipped inside the jar.
 *
 * <p>Loading is explicit about failure: a syntactically broken file is reported as a
 * configuration error and replaced by the bundled defaults instead of being read as an
 * empty file, which would silently reset the server owner's settings.</p>
 */
public final class ConfigFile {

    private final JavaPlugin plugin;
    private final String name;
    private final File file;
    private YamlConfiguration config = new YamlConfiguration();
    private boolean valid;

    public ConfigFile(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        this.file = new File(plugin.getDataFolder(), name);
    }

    public String name() {
        return name;
    }

    public File file() {
        return file;
    }

    public YamlConfiguration config() {
        return config;
    }

    /** True when the file parsed cleanly. */
    public boolean valid() {
        return valid;
    }

    /**
     * Copies the bundled default out of the jar when the file is missing, then parses it.
     *
     * @param issues collects any parse failure
     */
    public void load(ConfigIssues issues) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            issues.add(ConfigIssue.error(name, "", "could not create the plugin data folder " + parent));
            config = new YamlConfiguration();
            valid = false;
            return;
        }
        if (!file.exists()) {
            if (!saveBundledDefault()) {
                issues.add(ConfigIssue.error(name, "",
                        "the file is missing and no bundled default exists for it"));
                config = new YamlConfiguration();
                valid = false;
                return;
            }
        }
        YamlConfiguration parsed = new YamlConfiguration();
        try {
            parsed.load(file);
            config = parsed;
            valid = true;
        } catch (IOException error) {
            issues.add(ConfigIssue.error(name, "", "could not be read: " + error.getMessage()));
            config = new YamlConfiguration();
            valid = false;
        } catch (InvalidConfigurationException error) {
            issues.add(ConfigIssue.error(name, "", "is not valid YAML: " + error.getMessage()
                    + " - the bundled defaults are used instead so nothing is silently reset"));
            config = bundledOrDefault();
            valid = false;
        }
    }

    /** Writes the jar copy to disk. Returns false when the jar does not contain one. */
    private boolean saveBundledDefault() {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                return false;
            }
            java.nio.file.Files.copy(in, file.toPath());
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    /** Parses the bundled default from the jar; empty when there is none. */
    private YamlConfiguration bundledOrDefault() {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                return new YamlConfiguration();
            }
            YamlConfiguration parsed = new YamlConfiguration();
            parsed.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return parsed;
        } catch (IOException | InvalidConfigurationException error) {
            return new YamlConfiguration();
        }
    }
}
