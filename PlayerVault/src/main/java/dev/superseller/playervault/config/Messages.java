package dev.superseller.playervault.config;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.superseller.playervault.util.Texts;
import net.kyori.adventure.text.Component;

/**
 * Loads {@code messages.yml} and turns entries into components.
 *
 * <p>Every message is looked up lazily from the parsed document, keys are dot
 * separated, and {@code prefix} is prepended by the {@code send} helpers so command
 * output stays consistent. Missing keys fall back to the key itself rather than
 * throwing, which keeps a half-translated file usable.
 */
public final class Messages {

    private YamlConfiguration source;
    private Component prefix;

    private Messages(YamlConfiguration source) {
        this.source = source;
        this.prefix = Texts.parse(source.getString("prefix", ""));
    }

    /**
     * Reads the message file from disk.
     *
     * <p>The returned instance is reused by {@link #reload(File, Logger)}, so every
     * component that was handed a reference picks up new wording without being
     * rebuilt.
     *
     * @param file the {@code messages.yml} inside the plugin data folder
     * @param logger used to report an unreadable file
     */
    public static Messages load(File file, Logger logger) {
        Messages messages = new Messages(read(file, logger));
        if (!file.isFile()) {
            logger.warning("Missing " + file.getName() + "; messages will be empty.");
        }
        return messages;
    }

    /** Re-reads the message file in place. */
    public void reload(File file, Logger logger) {
        this.source = read(file, logger);
        this.prefix = Texts.parse(this.source.getString("prefix", ""));
    }

    private static YamlConfiguration read(File file, Logger logger) {
        Path path = file.toPath();
        if (!Files.isRegularFile(path)) {
            return YamlConfiguration.loadConfiguration(new StringReader(""));
        }
        try {
            return YamlConfiguration.loadConfiguration(
                    new StringReader(Files.readString(path, StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not read " + file.getName() + ", using defaults.", ex);
            return YamlConfiguration.loadConfiguration(new StringReader(""));
        }
    }

    /** Builds a placeholder map from {@code key, value, key, value, ...} pairs. */
    private static Map<String, String> pairs(String... replacements) {
        return Texts.map(replacements);
    }

    /** Raw (unparsed) single-line entry. */
    public String raw(String key, String... replacements) {
        String value = source.getString(key, key);
        return Texts.replace(value, pairs(replacements));
    }

    /** Raw (unparsed) multi-line entry. */
    public List<String> rawList(String key, String... replacements) {
        List<String> values = source.getStringList(key);
        Map<String, String> map = pairs(replacements);
        return values.stream().map(line -> Texts.replace(line, map)).toList();
    }

    /** Parses an entry into a component without the plugin prefix. */
    public Component component(String key, String... replacements) {
        return Texts.parse(Texts.replace(source.getString(key, key), pairs(replacements)));
    }

    /** Parses an entry into a component with the plugin prefix in front. */
    public Component prefixed(String key, String... replacements) {
        Map<String, String> map = pairs(replacements);
        Component body = Texts.parse(Texts.replace(source.getString(key, key), map));
        return prefix.append(body);
    }

    /** Sends a prefixed message. */
    public void send(CommandSender target, String key, String... replacements) {
        if (target != null) {
            target.sendMessage(prefixed(key, replacements));
        }
    }

    /** Sends a message without the plugin prefix. */
    public void sendPlain(CommandSender target, String key, String... replacements) {
        if (target != null) {
            target.sendMessage(component(key, replacements));
        }
    }

    /** Sends a literal component, already parsed by the caller. */
    public void send(CommandSender target, Component message) {
        if (target != null && message != null) {
            target.sendMessage(prefix.append(message));
        }
    }

    public Component prefix() {
        return prefix;
    }
}
