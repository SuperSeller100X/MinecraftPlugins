package dev.superseller.homesgui.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Immutable messages.yml reader with prefixing, placeholders, and legacy colour codes. */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final YamlConfiguration source;
    private final Component prefix;

    private Messages(YamlConfiguration source) {
        this.source = source;
        this.prefix = LEGACY.deserialize(source.getString("prefix", ""));
    }

    public static Messages load(File file, Logger logger) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.isFile()) {
                yaml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            }
        } catch (InvalidConfigurationException | java.io.IOException exception) {
            logger.log(Level.SEVERE, "Could not load messages.yml; using built-in fallback messages.", exception);
        }
        return new Messages(yaml);
    }

    public void send(CommandSender sender, String path, String... replacements) {
        if (sender != null) {
            sender.sendMessage(prefix.append(component(path, replacements)));
        }
    }

    public void sendLines(CommandSender sender, String path, String... replacements) {
        if (sender == null) {
            return;
        }
        for (String line : source.getStringList(path)) {
            sender.sendMessage(prefix.append(deserialize(replace(line, replacements))));
        }
    }

    public Component component(String path, String... replacements) {
        return deserialize(replace(source.getString(path, "&cMissing message: " + path), replacements));
    }

    public List<Component> components(String path, String... replacements) {
        List<Component> result = new ArrayList<>();
        for (String line : source.getStringList(path)) {
            result.add(deserialize(replace(line, replacements)));
        }
        return List.copyOf(result);
    }

    public String raw(String path, String... replacements) {
        return replace(source.getString(path, path), replacements);
    }

    public static Component deserialize(String input) {
        return LEGACY.deserialize(input == null ? "" : input);
    }

    private static String replace(String input, String... replacements) {
        if (input == null || replacements == null) {
            return input == null ? "" : input;
        }
        String output = input;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            String key = replacements[index];
            String value = replacements[index + 1];
            if (key != null) {
                output = output.replace(key, value == null ? "" : value);
            }
        }
        return output;
    }
}
