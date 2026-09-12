package dev.superseller.minecraftwiki.message;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.kyori.adventure.text.Component;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.minecraftwiki.config.ConfigFile;
import dev.superseller.minecraftwiki.config.ConfigIssue;
import dev.superseller.minecraftwiki.config.ConfigIssues;
import dev.superseller.minecraftwiki.util.Text;

/**
 * Localised, MiniMessage formatted messages.
 *
 * <p>English ships as {@code messages.yml}. Any {@code lang/<code>.yml} file present in the
 * plugin folder is registered as another language; nothing is generated or guessed. Keys a
 * translation does not define fall back to the default language.</p>
 *
 * <p>The bundle map is rebuilt on reload and published through a volatile reference, so
 * readers never need locking and never observe a half-loaded translation.</p>
 */
public final class Messages {

    /** Key holding the chat prefix inside a bundle. */
    private static final String PREFIX_KEY = "prefix";

    private final JavaPlugin plugin;
    private final boolean fallbackToDefault;

    private volatile Map<String, YamlConfiguration> bundles = Map.of();
    private volatile String defaultLanguage = "en";
    private volatile String prefix = "";

    public Messages(JavaPlugin plugin, boolean fallbackToDefault) {
        this.plugin = plugin;
        this.fallbackToDefault = fallbackToDefault;
    }

    /**
     * Loads the default bundle plus every {@code lang/*.yml} translation.
     *
     * @param messagesFile the already parsed default message file
     * @param defaultCode  language code of that file
     */
    public void load(ConfigFile messagesFile, String defaultCode, ConfigIssues issues) {
        Map<String, YamlConfiguration> loaded = new LinkedHashMap<>();
        String code = normalise(defaultCode);
        defaultLanguage = code;
        loaded.put(code, messagesFile.config());

        File langFolder = new File(plugin.getDataFolder(), "lang");
        File[] files = langFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String language = normalise(file.getName().substring(0, file.getName().length() - 4));
                if (language.isEmpty()) {
                    continue;
                }
                YamlConfiguration parsed = new YamlConfiguration();
                try {
                    parsed.load(file);
                    if (loaded.put(language, parsed) != null) {
                        issues.add(ConfigIssue.warning("lang/" + file.getName(), "",
                                "overrides the default language bundle"));
                    }
                } catch (java.io.IOException error) {
                    issues.add(ConfigIssue.error("lang/" + file.getName(), "",
                            "could not be read: " + error.getMessage()));
                } catch (InvalidConfigurationException error) {
                    issues.add(ConfigIssue.error("lang/" + file.getName(), "",
                            "is not valid YAML and was skipped: " + error.getMessage()));
                }
            }
        }
        prefix = raw(code, PREFIX_KEY, "");
        bundles = Collections.unmodifiableMap(loaded);
    }

    /** Every registered language code, sorted. */
    public Set<String> languages() {
        return new TreeSet<>(bundles.keySet());
    }

    public boolean has(String code) {
        String language = normalise(code);
        return !language.isEmpty() && bundles.containsKey(language);
    }

    public String defaultLanguage() {
        return defaultLanguage;
    }

    /** Resolves the language to use for a player, honouring per-player settings. */
    public String resolve(String requested) {
        if (requested != null && has(requested)) {
            return normalise(requested);
        }
        return defaultLanguage;
    }

    /** Renders one message key. A missing key renders as the key itself, in red. */
    public Component get(String language, String key, Map<String, String> placeholders) {
        String raw = raw(language, key, null);
        if (raw == null) {
            return Component.text("missing message: " + key);
        }
        return Text.mini(raw, placeholders);
    }

    /** Renders a list message key. A missing key yields a single line saying so. */
    public List<Component> getList(String language, String key, Map<String, String> placeholders) {
        YamlConfiguration bundle = bundleFor(language);
        List<String> lines = bundle == null ? List.of() : bundle.getStringList(key);
        if (lines.isEmpty() && fallbackToDefault && !defaultLanguage.equals(normalise(language))) {
            YamlConfiguration fallback = bundles.get(defaultLanguage);
            lines = fallback == null ? List.of() : fallback.getStringList(key);
        }
        if (lines.isEmpty()) {
            String single = raw(language, key, null);
            if (single != null) {
                return List.of(Text.mini(single, placeholders));
            }
            return List.of(Component.text("missing message: " + key));
        }
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(Text.mini(line, placeholders));
        }
        return List.copyOf(out);
    }

    /** Sends one prefixed message. */
    public void send(CommandSender receiver, String language, String key, Map<String, String> placeholders) {
        if (receiver == null) {
            return;
        }
        receiver.sendMessage(prefixed(get(language, key, placeholders)));
    }

    /** Sends every line of a list message; the prefix is applied to the first line only. */
    public void sendList(CommandSender receiver, String language, String key, Map<String, String> placeholders) {
        if (receiver == null) {
            return;
        }
        List<Component> lines = getList(language, key, placeholders);
        for (int i = 0; i < lines.size(); i++) {
            receiver.sendMessage(i == 0 ? prefixed(lines.get(i)) : lines.get(i));
        }
    }

    /** Applies the configured chat prefix. */
    public Component prefixed(Component message) {
        if (prefix.isEmpty()) {
            return message;
        }
        return Text.mini(prefix).append(message);
    }

    /** Raw, unrendered string for a key, with default-language fallback. */
    private String raw(String language, String key, String fallback) {
        YamlConfiguration bundle = bundleFor(language);
        String value = bundle == null ? null : bundle.getString(key);
        if (value == null && fallbackToDefault && !defaultLanguage.equals(normalise(language))) {
            YamlConfiguration defaultBundle = bundles.get(defaultLanguage);
            value = defaultBundle == null ? null : defaultBundle.getString(key);
        }
        return value == null ? fallback : value;
    }

    private YamlConfiguration bundleFor(String language) {
        Map<String, YamlConfiguration> current = bundles;
        YamlConfiguration bundle = current.get(normalise(language));
        if (bundle == null) {
            bundle = current.get(defaultLanguage);
        }
        return bundle;
    }

    private static String normalise(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }
}
