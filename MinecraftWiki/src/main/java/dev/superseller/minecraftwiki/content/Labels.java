package dev.superseller.minecraftwiki.content;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.superseller.minecraftwiki.util.Text;

/**
 * Display labels for the machine keys content resolvers emit.
 *
 * <p>Resolvers never hardcode wording: they produce stable keys such as {@code max_level}
 * and this class turns them into text from {@code gui.yml}. An unlabelled key falls back to
 * the prettified key, so a new property from a newer server version still renders sensibly
 * instead of breaking.</p>
 */
public final class Labels {

    private final Map<String, String> labels;
    private final String format;

    public Labels(Map<String, String> labels, String format) {
        this.labels = labels == null ? Map.of() : Map.copyOf(labels);
        this.format = format == null || format.isBlank()
                ? "<gray><label> <dark_gray>\u00BB <white><value>" : format;
    }

    /** Loads the {@code labels} section of gui.yml. */
    public static Labels load(YamlConfiguration config) {
        Map<String, String> labels = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("labels");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) {
                    labels.put(key.toLowerCase(Locale.ROOT), value);
                }
            }
        }
        String format = config.getString("labels-format");
        return new Labels(labels, format);
    }

    /** The label for a machine key, or the prettified key when none is configured. */
    public String label(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String configured = labels.get(key.toLowerCase(Locale.ROOT));
        return configured == null || configured.isEmpty() ? Text.prettify(key) : configured;
    }

    /** Renders one {@code label value} line as MiniMessage. */
    public String line(String key, String value) {
        return format.replace("<label>", label(key)).replace("<value>", value == null ? "" : value);
    }
}
