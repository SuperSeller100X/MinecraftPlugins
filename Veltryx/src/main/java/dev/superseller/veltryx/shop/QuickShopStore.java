package dev.superseller.veltryx.shop;

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

/** Durable per-player quick-shop slots; does not persist item prices. */
public final class QuickShopStore {
    public static final int SLOTS = 45;
    public record QuickSlot(Purchase purchase, int amount) {
        public QuickSlot {
            if (purchase == null || amount < 1 || amount > 64) throw new IllegalArgumentException("invalid quick slot");
        }
    }
    private record Key(UUID owner, int slot) { }
    private final Map<Key, QuickSlot> slots = new ConcurrentHashMap<>();
    private final Path file;
    private final Logger logger;
    public QuickShopStore(Path file, Logger logger) { this.file = file; this.logger = logger; }
    public synchronized void load() {
        slots.clear();
        if (!Files.exists(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            for (String key : p.stringPropertyNames()) {
                try {
                    String[] parts = key.split("\\.", -1);
                    if (parts.length != 2) continue;
                    UUID id = UUID.fromString(parts[0]);
                    int slot = Integer.parseInt(parts[1]);
                    String[] value = p.getProperty(key).split("\\|", -1);
                    if (slot < 0 || slot >= SLOTS || value.length != 4
                            || !value[0].matches("[A-Z0-9_]+") || !value[2].matches("[A-Z0-9_]*")) continue;
                    QuickSlot saved = new QuickSlot(new Purchase(value[0], value[2], Integer.parseInt(value[3])),
                            Integer.parseInt(value[1]));
                    slots.put(new Key(id, slot), saved);
                } catch (IllegalArgumentException ignored) {
                    logger.warning("Skipping malformed QuickShop slot: " + key);
                }
            }
        } catch (IOException e) { logger.log(Level.SEVERE, "Could not read QuickShop slots", e); }
    }
    public QuickSlot get(UUID id, int slot) { return slots.get(new Key(id, slot)); }
    public synchronized void set(UUID id, int slot, QuickSlot value) {
        if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("invalid slot");
        Key key = new Key(id, slot);
        if (value == null) slots.remove(key); else slots.put(key, value);
        save();
    }
    public synchronized void save() {
        Properties data = new Properties();
        slots.forEach((key, value) -> data.setProperty(key.owner() + "." + key.slot(),
                value.purchase().material() + "|" + value.amount() + "|" + value.purchase().choice() + "|" + value.purchase().level()));
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) { data.store(out, "Veltryx QuickShop slots"); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { logger.log(Level.SEVERE, "Could not save QuickShop slots", e); }
    }
}
