package dev.superseller.playervault.storage;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.configuration.file.YamlConfiguration;

import dev.superseller.playervault.model.VaultData;

/**
 * Flat-file storage: one {@code vaults/<uuid>.yml} per player.
 *
 * <p>Human readable, needs no driver and works identically on Linux, Windows and
 * macOS because every read and write goes through {@code java.nio} with an explicit
 * UTF-8 charset.
 *
 * <p>Writes are atomic: the document is written to a temporary sibling file and then
 * moved into place, so a crash mid-write can never truncate a player's vault.
 *
 * <p>The Base64 item blob is stored as a list of fixed-size chunks. SnakeYAML folds
 * very long single-line scalars, and chunking keeps the file byte-exact on reload.
 */
public final class YamlVaultStore implements VaultStore {

    private static final String FILE_SUFFIX = ".yml";
    private static final int CHUNK_SIZE = 4096;

    private final Path directory;
    private final Logger logger;
    private final int defaultRows;

    public YamlVaultStore(File dataFolder, Logger logger, int defaultRows) {
        this.directory = dataFolder.toPath().resolve("vaults");
        this.logger = logger;
        this.defaultRows = Math.max(1, defaultRows);
    }

    @Override
    public String name() {
        return "YAML (" + directory.getFileName() + "/)";
    }

    private Path file(UUID owner) {
        return directory.resolve(owner + FILE_SUFFIX);
    }

    @Override
    public VaultData load(UUID owner) throws IOException {
        Path path = file(owner);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(raw));
        int rows = Math.max(1, config.getInt("rows", defaultRows));
        VaultData data = new VaultData(owner, config.getString("name", ""), rows);
        data.purchasedRows(config.getInt("purchased", Math.max(0, rows - defaultRows)));
        data.totalSpent(config.getDouble("spent", 0.0d));
        data.contents(ItemCodec.decode(readBlob(config), rows * VaultData.SLOTS_PER_ROW, logger));
        return data;
    }

    @Override
    public void save(VaultData data) throws IOException {
        Files.createDirectories(directory);
        YamlConfiguration config = new YamlConfiguration();
        config.set("owner", data.owner().toString());
        config.set("name", data.name());
        config.set("rows", data.rows());
        config.set("purchased", data.purchasedRows());
        config.set("spent", data.totalSpent());
        config.set("updated", data.updated());
        writeBlob(config, ItemCodec.encode(data.contents()));

        Path target = file(data.owner());
        Path temp = directory.resolve(data.owner() + FILE_SUFFIX + ".tmp");
        Files.writeString(temp, config.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            // Some mounts (network shares, some macOS volumes) refuse atomic moves.
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void delete(UUID owner) throws IOException {
        Files.deleteIfExists(file(owner));
    }

    @Override
    public List<UUID> all() throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<UUID> owners = new ArrayList<>();
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(FILE_SUFFIX))
                    .forEach(path -> {
                        String base = path.getFileName().toString();
                        String id = base.substring(0, base.length() - FILE_SUFFIX.length());
                        try {
                            owners.add(UUID.fromString(id));
                        } catch (IllegalArgumentException ignored) {
                            // Not one of ours; leave the file alone.
                        }
                    });
        }
        owners.sort(Comparator.comparing(UUID::toString));
        return owners;
    }

    @Override
    public void close() {
        // Nothing to release for flat files.
    }

    private void writeBlob(YamlConfiguration config, String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            config.set("items", List.of());
            return;
        }
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < encoded.length(); start += CHUNK_SIZE) {
            chunks.add(encoded.substring(start, Math.min(encoded.length(), start + CHUNK_SIZE)));
        }
        config.set("items", chunks);
    }

    private String readBlob(YamlConfiguration config) {
        Object value = config.get("items");
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object entry : list) {
                if (entry != null) {
                    builder.append(entry);
                }
            }
            return builder.toString();
        }
        if (value != null) {
            logger.log(Level.WARNING, "Unexpected 'items' type in a vault file: " + value.getClass().getName());
        }
        return "";
    }
}
