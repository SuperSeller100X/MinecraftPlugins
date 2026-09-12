package dev.superseller.minecraftwiki.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.superseller.minecraftwiki.util.Materials;
import dev.superseller.minecraftwiki.util.Sounds;

/**
 * Typed, validating access to one YAML file.
 *
 * <p>Every getter falls back to an explicit default and records a {@link ConfigIssue} so a
 * broken value is visible in the log instead of being silently replaced. Out-of-range
 * numbers are clamped and reported, unknown materials and sounds are reported, and wrong
 * types are reported.</p>
 */
public final class YamlReader {

    private final YamlConfiguration root;
    private final String file;
    private final ConfigIssues issues;
    private final Set<String> soundNames = new LinkedHashSet<>();

    public YamlReader(YamlConfiguration root, String file, ConfigIssues issues) {
        this.root = root == null ? new YamlConfiguration() : root;
        this.file = file;
        this.issues = issues;
    }

    public String file() {
        return file;
    }

    public YamlConfiguration root() {
        return root;
    }

    /** Every sound name read from this file, for a single validation pass. */
    public Set<String> soundNames() {
        return soundNames;
    }

    /** Reports every invalid sound name found in this file. */
    public void validateSounds() {
        for (String sound : soundNames) {
            String problem = Sounds.validate(sound);
            if (problem != null) {
                issues.add(ConfigIssue.warning(file, "sounds", problem + " (it will be silent)"));
            }
        }
    }

    public boolean contains(String path) {
        return root.contains(path);
    }

    public ConfigurationSection section(String path) {
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null && root.contains(path)) {
            issues.add(ConfigIssue.error(file, path, "should be a section but is a single value"));
        }
        return section;
    }

    public String string(String path, String fallback) {
        Object raw = root.get(path);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof String text) {
            return text;
        }
        issues.add(ConfigIssue.warning(file, path, "should be text, found "
                + raw.getClass().getSimpleName() + " - using the default"));
        return fallback;
    }

    public List<String> stringList(String path) {
        Object raw = root.get(path);
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object entry : list) {
                if (entry != null) {
                    out.add(String.valueOf(entry));
                }
            }
            return List.copyOf(out);
        }
        issues.add(ConfigIssue.warning(file, path, "should be a list - ignored"));
        return List.of();
    }

    public boolean bool(String path, boolean fallback) {
        Object raw = root.get(path);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String text) {
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
                return Boolean.parseBoolean(text);
            }
        }
        issues.add(ConfigIssue.warning(file, path, "should be true or false - using " + fallback));
        return fallback;
    }

    public int integer(String path, int fallback, int min, int max) {
        Object raw = root.get(path);
        if (raw == null) {
            return fallback;
        }
        Integer value = null;
        if (raw instanceof Number number) {
            value = number.intValue();
        } else if (raw instanceof String text) {
            try {
                value = Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                // handled below
            }
        }
        if (value == null) {
            issues.add(ConfigIssue.warning(file, path, "should be a whole number - using " + fallback));
            return fallback;
        }
        if (value < min || value > max) {
            int clamped = Math.max(min, Math.min(max, value));
            issues.add(ConfigIssue.warning(file, path, value + " is outside " + min + ".." + max
                    + " - clamped to " + clamped));
            return clamped;
        }
        return value;
    }

    public long longValue(String path, long fallback, long min, long max) {
        Object raw = root.get(path);
        if (raw == null) {
            return fallback;
        }
        Long value = null;
        if (raw instanceof Number number) {
            value = number.longValue();
        } else if (raw instanceof String text) {
            try {
                value = Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                // handled below
            }
        }
        if (value == null) {
            issues.add(ConfigIssue.warning(file, path, "should be a whole number - using " + fallback));
            return fallback;
        }
        if (value < min || value > max) {
            long clamped = Math.max(min, Math.min(max, value));
            issues.add(ConfigIssue.warning(file, path, value + " is outside " + min + ".." + max
                    + " - clamped to " + clamped));
            return clamped;
        }
        return value;
    }

    public double decimal(String path, double fallback, double min, double max) {
        Object raw = root.get(path);
        if (raw == null) {
            return fallback;
        }
        Double value = null;
        if (raw instanceof Number number) {
            value = number.doubleValue();
        } else if (raw instanceof String text) {
            try {
                value = Double.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                // handled below
            }
        }
        if (value == null) {
            issues.add(ConfigIssue.warning(file, path, "should be a number - using " + fallback));
            return fallback;
        }
        if (value < min || value > max) {
            double clamped = Math.max(min, Math.min(max, value));
            issues.add(ConfigIssue.warning(file, path, value + " is outside " + min + ".." + max
                    + " - clamped to " + clamped));
            return clamped;
        }
        return value;
    }

    /** Reads a material, reporting unknown names and returning the fallback for them. */
    public Material material(String path, Material fallback) {
        String raw = string(path, null);
        if (raw == null) {
            return fallback;
        }
        Materials.Lookup lookup = Materials.resolve(raw, fallback, file + ":" + path);
        if (lookup.problem() != null) {
            issues.add(ConfigIssue.warning(file, path, lookup.problem()));
        }
        return lookup.material();
    }

    /**
     * Reads a sound spec ({@code key}, {@code volume}, {@code pitch}) from a section.
     * A section without a key yields a silent spec rather than a default sound.
     */
    public SoundSpec sound(String path, String defaultKey, float defaultVolume, float defaultPitch) {
        ConfigurationSection section = section(path);
        if (section == null) {
            return new SoundSpec(defaultKey, defaultVolume, defaultPitch);
        }
        YamlReader nested = new YamlReader(sectionAsConfig(section), file, issues);
        String key = nested.string("key", defaultKey);
        if (key != null && !key.isBlank()) {
            soundNames.add(key);
        }
        float volume = (float) nested.decimal("volume", defaultVolume, 0f, 10f);
        float pitch = (float) nested.decimal("pitch", defaultPitch, 0f, 10f);
        return new SoundSpec(key, volume, pitch);
    }

    /** Reads a named enum constant case-insensitively. */
    public <E extends Enum<E>> E enumValue(String path, Class<E> type, E fallback) {
        String raw = string(path, null);
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            issues.add(ConfigIssue.warning(file, path, "'" + raw + "' is not one of "
                    + java.util.Arrays.toString(type.getEnumConstants()) + " - using " + fallback));
            return fallback;
        }
    }

    /** Wraps a subsection so nested reads keep the same file name and issue sink. */
    private static YamlConfiguration sectionAsConfig(ConfigurationSection section) {
        YamlConfiguration copy = new YamlConfiguration();
        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) {
                copy.set(key, section.get(key));
            }
        }
        return copy;
    }
}
