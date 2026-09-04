package dev.superseller.voidtotem.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed snapshot of VoidTotem's config.yml. Reload with {@link #load(FileConfiguration)}
 * after /vt reload.
 */
public final class VoidConfig {

    public enum RescueMode { LAST_SAFE, SPAWN, COORDS }
    public enum ConsumeFrom { ANY, HAND }

    private NamespacedKey itemModel;
    private boolean autoRegister;
    private long shopPrice;
    private RescueMode rescueMode;
    private String rescueWorld;
    private double rescueX, rescueY, rescueZ;
    private double heal;
    private boolean clearHarmful;
    private long resistanceSeconds;
    private long slowFallingSeconds;
    private ConsumeFrom consumeFrom;
    private boolean playAnimation;
    private boolean playSound;
    private boolean particles;
    private String particleId;
    private int particleCount;
    private long cooldownSeconds;
    private final Map<String, String> messages = new HashMap<>();

    public void load(FileConfiguration config) {
        String modelStr = config.getString("item-model", "voidtotem:void_totem");
        NamespacedKey parsed = NamespacedKey.fromString(modelStr);
        itemModel = parsed != null ? parsed : NamespacedKey.fromString("voidtotem:void_totem");

        autoRegister = config.getBoolean("auto-register-in-shardtools", true);
        shopPrice = Math.max(0L, config.getLong("shop-price", 1000L));

        rescueMode = parseEnum(config.getString("rescue-mode", "LAST_SAFE"), RescueMode.LAST_SAFE);
        rescueWorld = config.getString("rescue-world", "");
        rescueX = config.getDouble("rescue-x", 0.5);
        rescueY = config.getDouble("rescue-y", 64.0);
        rescueZ = config.getDouble("rescue-z", 0.5);

        heal = config.getDouble("heal-amount", 20.0);
        clearHarmful = config.getBoolean("clear-harmful-effects", true);
        resistanceSeconds = Math.max(0, config.getLong("resistance-seconds", 5));
        slowFallingSeconds = Math.max(0, config.getLong("slow-falling-seconds", 5));

        consumeFrom = parseEnum(config.getString("consume-from", "ANY"), ConsumeFrom.ANY);

        playAnimation = config.getBoolean("play-totem-animation", true);
        playSound = config.getBoolean("play-totem-sound", true);
        particles = config.getBoolean("spawn-particles", true);
        particleId = config.getString("particle-id", "PORTAL");
        particleCount = Math.max(1, config.getInt("particle-count", 30));
        cooldownSeconds = Math.max(0, config.getLong("rescue-cooldown-seconds", 2));

        messages.clear();
        ConfigurationSection ms = config.getConfigurationSection("messages");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                messages.put(key, ms.getString(key, ""));
            }
        }
    }

    private static <T extends Enum<T>> T parseEnum(String value, T fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /** Renders a message from config with %token% replacements. */
    public String message(String key, String... replacements) {
        String text = messages.getOrDefault(key, "");
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    public NamespacedKey itemModel() { return itemModel; }
    public boolean autoRegister() { return autoRegister; }
    public long shopPrice() { return shopPrice; }
    public RescueMode rescueMode() { return rescueMode; }
    public String rescueWorld() { return rescueWorld; }
    public double rescueX() { return rescueX; }
    public double rescueY() { return rescueY; }
    public double rescueZ() { return rescueZ; }
    public double heal() { return heal; }
    public boolean clearHarmful() { return clearHarmful; }
    public long resistanceSeconds() { return resistanceSeconds; }
    public long slowFallingSeconds() { return slowFallingSeconds; }
    public ConsumeFrom consumeFrom() { return consumeFrom; }
    public boolean playAnimation() { return playAnimation; }
    public boolean playSound() { return playSound; }
    public boolean particles() { return particles; }
    public String particleId() { return particleId; }
    public int particleCount() { return particleCount; }
    public long cooldownSeconds() { return cooldownSeconds; }
}
