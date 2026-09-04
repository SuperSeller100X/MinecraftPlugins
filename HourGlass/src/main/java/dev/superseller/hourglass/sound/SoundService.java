package dev.superseller.hourglass.sound;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.superseller.hourglass.config.HourGlassConfig;

import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plays the sound effects configured under {@code sounds.events}.
 *
 * <p>Sound names in config are looked up through {@link Registry#SOUNDS}, so
 * both spellings work: the constant style {@code ENTITY_PLAYER_LEVELUP} and the
 * namespaced key {@code minecraft:entity.player.levelup}. Datapack and mod
 * sounds therefore work too — nothing is hard-coded to the vanilla list.
 *
 * <p>Nothing here can throw at a caller: an unknown name logs once and then
 * plays silence, because a typo in a config file must never break a command.
 */
public final class SoundService {

    /** One configured sound slot. */
    public record Spec(String name, float volume, float pitch, SoundCategory category, boolean enabled) {

        public static final Spec SILENT = new Spec("", 0f, 1f, SoundCategory.MASTER, false);

        public boolean playable() {
            return enabled && name != null && !name.isBlank();
        }
    }

    /** Event key -> default sound, used when config.yml omits the entry. */
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("gui-open", "BLOCK_CHEST_OPEN"),
            Map.entry("gui-close", "BLOCK_CHEST_CLOSE"),
            Map.entry("gui-click", "UI_BUTTON_CLICK"),
            Map.entry("gui-back", "BLOCK_WOODEN_DOOR_CLOSE"),
            Map.entry("gui-page", "BLOCK_NOTE_BLOCK_HAT"),
            Map.entry("gui-error", "ENTITY_VILLAGER_NO"),
            Map.entry("command-ok", "ENTITY_EXPERIENCE_ORB_PICKUP"),
            Map.entry("command-error", "BLOCK_NOTE_BLOCK_BASS"),
            Map.entry("admin-edit", "BLOCK_ANVIL_USE"),
            Map.entry("admin-reset", "ENTITY_ITEM_BREAK"),
            Map.entry("admin-purge", "ENTITY_GENERIC_EXPLODE"),
            Map.entry("admin-export", "BLOCK_BEACON_ACTIVATE"),
            Map.entry("milestone", "UI_TOAST_CHALLENGE_COMPLETE"),
            Map.entry("join", "BLOCK_AMETHYST_BLOCK_CHIME"),
            Map.entry("display-on", "BLOCK_NOTE_BLOCK_PLING"),
            Map.entry("display-off", "BLOCK_NOTE_BLOCK_PLING"));

    /** Every sound event key the plugin knows, i.e. {@code sounds.events.<key>}. */
    public static Set<String> eventKeys() {
        return DEFAULTS.keySet();
    }

    /** The default sound for an event key, or {@code null} when the key is unknown. */
    public static String defaultSound(String key) {
        return key == null ? null : DEFAULTS.get(key);
    }

    private final JavaPlugin plugin;
    private final Map<String, Spec> specs = new ConcurrentHashMap<>();
    private final Map<String, Sound> resolved = new ConcurrentHashMap<>();
    private final Collection<String> warned = ConcurrentHashMap.newKeySet();
    private volatile Map<String, Sound> soundIndex;

    public SoundService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Re-reads {@code sounds.*}. */
    public void load(org.bukkit.configuration.file.YamlConfiguration yaml, HourGlassConfig config) {
        specs.clear();
        boolean master = yaml.getBoolean("sounds.enabled", true) && config.soundsEnabled();
        float baseVolume = (float) yaml.getDouble("sounds.volume", config.soundsVolume());
        float basePitch = (float) yaml.getDouble("sounds.pitch", config.soundsPitch());
        for (String key : DEFAULTS.keySet()) {
            String path = "sounds.events." + key;
            String name = yaml.getString(path + ".sound", DEFAULTS.get(key));
            float volume = (float) yaml.getDouble(path + ".volume", baseVolume);
            float pitch = (float) yaml.getDouble(path + ".pitch", basePitch);
            SoundCategory category = category(yaml.getString(path + ".category", "master"));
            boolean enabled = yaml.getBoolean(path + ".enabled", true) && master;
            specs.put(key, new Spec(name, volume, pitch, category, enabled));
        }
    }

    private static SoundCategory category(String name) {
        if (name == null || name.isBlank()) {
            return SoundCategory.MASTER;
        }
        try {
            return SoundCategory.valueOf(name.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            return SoundCategory.MASTER;
        }
    }

    public Spec spec(String key) {
        return specs.getOrDefault(key, Spec.SILENT);
    }

    /** Plays an event to one player, honouring the master switch. */
    public void play(Player player, String key) {
        if (player == null) {
            return;
        }
        play(player, player.getLocation(), key);
    }

    /** Plays an event at a location for one player. */
    public void play(Player player, Location location, String key) {
        Spec spec = spec(key);
        if (player == null || !spec.playable()) {
            return;
        }
        Sound sound = resolve(spec.name());
        if (sound == null) {
            return;
        }
        try {
            player.playSound(location, sound, spec.category(), spec.volume(), spec.pitch());
        } catch (RuntimeException e) {
            if (warned.add(key)) {
                plugin.getLogger().warning("Could not play sound '" + key + "': " + e.getMessage());
            }
        }
    }

    /**
     * Plays an event to any sender; console command senders simply get nothing,
     * which keeps commands free of {@code instanceof} noise.
     */
    public void play(org.bukkit.command.CommandSender sender, String key) {
        if (sender instanceof Player player) {
            play(player, key);
        }
    }

    /** Plays an event to every listed player (milestone broadcasts). */
    public void playAll(Collection<? extends Player> players, String key) {
        for (Player player : players) {
            if (player != null && player.isOnline()) {
                play(player, key);
            }
        }
    }

    /**
     * Resolves a configured sound name. Accepts {@code ENTITY_PLAYER_LEVELUP},
     * {@code entity.player.levelup}, {@code minecraft:entity.player.levelup} and
     * anything a datapack adds. Cached; unknown names log once and return
     * {@code null}.
     */
    public Sound resolve(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String name = configured.trim();
        Sound cached = resolved.get(name);
        if (cached != null) {
            return cached;
        }
        Sound found = lookup(name);
        if (found != null) {
            resolved.put(name, found);
            return found;
        }
        if (warned.add(name)) {
            plugin.getLogger().warning("Unknown sound '" + name + "' - that sound will stay silent."
                    + " Use a name like ENTITY_PLAYER_LEVELUP or minecraft:entity.player.levelup.");
        }
        return null;
    }

    private Sound lookup(String name) {
        // 1. namespaced key form
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.US));
        if (key != null) {
            Sound byKey = safeRegistryGet(key);
            if (byKey != null) {
                return byKey;
            }
        }
        // 2. CONSTANT_NAME form against a lazily built index of the registry
        Map<String, Sound> index = index();
        if (index == null) {
            return null;
        }
        Sound exact = index.get(name.toUpperCase(Locale.US).replace('-', '_'));
        if (exact != null) {
            return exact;
        }
        return index.get(name.toLowerCase(Locale.US));
    }

    private Sound safeRegistryGet(NamespacedKey key) {
        try {
            return Registry.SOUNDS.get(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** {@code CONSTANT_NAME -> Sound} plus {@code namespace:path -> Sound}. */
    private Map<String, Sound> index() {
        Map<String, Sound> current = soundIndex;
        if (current != null) {
            return current;
        }
        Map<String, Sound> built = new ConcurrentHashMap<>();
        try {
            Registry.SOUNDS.stream().forEach(sound -> {
                NamespacedKey key = Registry.SOUNDS.getKey(sound);
                if (key == null) {
                    return;
                }
                built.put((key.getNamespace() + ":" + key.getKey()).toLowerCase(Locale.US), sound);
                if (NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
                    built.put(key.getKey().toUpperCase(Locale.US).replace('.', '_'), sound);
                }
            });
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Sound registry unavailable: " + e.getMessage());
            return null;
        }
        soundIndex = built;
        return built;
    }

    /** Number of configured, playable sound events — shown by {@code /playtimeadmin stats}. */
    public int enabledCount() {
        int count = 0;
        for (Spec spec : specs.values()) {
            if (spec.playable()) {
                count++;
            }
        }
        return count;
    }
}
