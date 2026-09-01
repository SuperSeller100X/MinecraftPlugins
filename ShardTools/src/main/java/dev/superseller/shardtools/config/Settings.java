package dev.superseller.shardtools.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.shardtools.item.SoundSpec;

/**
 * Typed snapshot of config.yml. Everything in the plugin is configurable;
 * call {@link #load()} again after /st reload.
 */
public final class Settings {

    private final JavaPlugin plugin;

    private String symbol = "\u2726";
    private long startBalance;

    private boolean awardEnabled = true;
    private long awardIntervalMinutes = 5;
    private long awardAmount = 5;
    private boolean awardAnnounce = true;

    private boolean requireSneak;
    private boolean allowCreative;
    private int areaRadius = 1;
    private boolean oreXp = true;
    private Set<String> protectedMaterials = new HashSet<>();
    private boolean protectContainers = true;
    private final Set<String> areaMaterials = new HashSet<>();
    private final Map<String, int[]> oreXpValues = new HashMap<>();
    private int treeMaxBlocks = 256;
    private boolean treeSameMaterialOnly = true;
    private boolean treeReplant;
    private boolean treeBreakLeaves = true;

    private boolean mineSoundEnabled = true;
    private List<SoundSpec> mineSounds = new ArrayList<>();
    private boolean mineParticlesEnabled = true;
    private String mineParticleId = "PORTAL";
    private int mineParticleCount = 3;

    private boolean equipSoundEnabled = true;
    private SoundSpec equipSound;
    private boolean equipParticlesEnabled = true;
    private String equipParticleId = "PORTAL";
    private int equipParticleCount = 20;

    private long sweepSeconds = 30;
    private boolean loreRefresh = true;
    private long[] warnMinutes = {60, 10, 1};
    private boolean scanOpenedInventories = true;

    private long hasteDurationHours = 1;
    private int hasteAmplifier = 1;
    private int hasteColorRgb = 0xF7FF8A;

    private boolean shopConfirm;
    private int shopRows = 6;
    private String shopFiller = "GRAY_STAINED_GLASS_PANE";

    private boolean moneyShopEnabled = true;
    private double moneyCostPerShard = 10.0D;
    private long moneyMin = 1L;
    private long moneyMax = 100_000L;

    private boolean anvilProtect = true;
    private boolean grindstoneProtect = true;

    private String soundAward = "entity.experience_orb.pickup";
    private String soundPurchase = "block.amethyst_block.resonate";
    private String soundDeny = "entity.villager.no";
    private String soundExpire = "entity.item.break";
    private String soundWarn = "block.note_block.pling";

    private final Set<String> disabledWorlds = new HashSet<>();

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        symbol = config.getString("currency.symbol", "\u2726");
        startBalance = Math.max(0L, config.getLong("currency.start-balance", 0L));

        awardEnabled = config.getBoolean("award.enabled", true);
        awardIntervalMinutes = Math.max(1L, config.getLong("award.interval-minutes", 5L));
        awardAmount = Math.max(0L, config.getLong("award.amount", 5L));
        awardAnnounce = config.getBoolean("award.announce", true);

        requireSneak = config.getBoolean("behavior.require-sneak", false);
        allowCreative = config.getBoolean("behavior.allow-in-creative", false);
        areaRadius = Math.max(1, Math.min(4, config.getInt("behavior.area-radius", 1)));
        oreXp = config.getBoolean("behavior.ore-xp", true);
        protectedMaterials = upperCaseAll(config.getStringList("behavior.protected-materials"));
        protectContainers = config.getBoolean("behavior.protect-containers", true);
        // One shared 3x3 block list for pickaxe, axe and shovel.
        areaMaterials.clear();
        areaMaterials.addAll(upperCaseAll(config.getStringList("behavior.area-materials")));
        if (areaMaterials.isEmpty()) {
            // Older configs listed blocks per tool: fall back to the union so
            // every shard tool keeps working (and now shares the same blocks).
            areaMaterials.addAll(upperCaseAll(config.getStringList("behavior.pickaxe-materials")));
            areaMaterials.addAll(upperCaseAll(config.getStringList("behavior.shovel-materials")));
        }
        oreXpValues.clear();
        ConfigurationSection xpSection = config.getConfigurationSection("behavior.ore-xp-values");
        if (xpSection != null) {
            for (String key : xpSection.getKeys(false)) {
                int[] range = parseRange(xpSection.getString(key, "0-0"));
                if (range != null) {
                    oreXpValues.put(key.toUpperCase(Locale.ROOT), range);
                }
            }
        }
        treeMaxBlocks = Math.max(1, config.getInt("behavior.tree.max-blocks", 256));
        treeSameMaterialOnly = config.getBoolean("behavior.tree.same-material-only", true);
        treeReplant = config.getBoolean("behavior.tree.replant", false);
        treeBreakLeaves = config.getBoolean("behavior.tree.break-leaves", true);

        // DonutSMP amethyst sounds: ONE amethyst step sound per USE of a
        // tool (not per broken block), amethyst resonate when equipped.
        mineSoundEnabled = config.getBoolean("effects.mine-sound.enabled", true);
        List<SoundSpec> useSounds = SoundSpec.parseAll(
                config.getStringList("effects.mine-sound.sounds"));
        if (useSounds.isEmpty()) {
            useSounds.add(SoundSpec.parse("block.amethyst_block.step 1.0 1.0"));
        }
        mineSounds = useSounds;

        mineParticlesEnabled = config.getBoolean("effects.mine-particles.enabled", true);
        mineParticleId = config.getString("effects.mine-particles.id", "PORTAL");
        mineParticleCount = Math.max(1, config.getInt("effects.mine-particles.count", 1));

        equipSoundEnabled = config.getBoolean("effects.equip-sound.enabled", true);
        String equip = config.getString("effects.equip-sound.sound", "block.amethyst_block.resonate 1.0 1.0");
        equipSound = SoundSpec.parse(equip);
        equipParticlesEnabled = config.getBoolean("effects.equip-particles.enabled", true);
        equipParticleId = config.getString("effects.equip-particles.id", "PORTAL");
        equipParticleCount = Math.max(1, config.getInt("effects.equip-particles.count", 10));

        sweepSeconds = Math.max(5L, config.getLong("expiry.sweep-seconds", 30L));
        loreRefresh = config.getBoolean("expiry.lore-refresh", true);
        List<Long> warnList = new ArrayList<>();
        for (long minutes : config.getLongList("expiry.warn-minutes")) {
            if (minutes > 0) {
                warnList.add(minutes);
            }
        }
        warnList.sort((a, b) -> Long.compare(b, a));
        warnMinutes = new long[warnList.size()];
        for (int i = 0; i < warnList.size(); i++) {
            warnMinutes[i] = warnList.get(i);
        }
        scanOpenedInventories = config.getBoolean("expiry.scan-opened-inventories", true);

        hasteDurationHours = Math.max(1L, config.getLong("haste-potion.duration-hours", 1L));
        hasteAmplifier = Math.max(0, config.getInt("haste-potion.amplifier", 1));
        hasteColorRgb = parseColor(config.getString("haste-potion.color", "#f7ff8a"), 0xF7FF8A);

        shopConfirm = config.getBoolean("shop.confirm", false);
        shopRows = Math.max(3, Math.min(6, config.getInt("shop.rows", 6)));
        shopFiller = config.getString("shop.filler", "GRAY_STAINED_GLASS_PANE");

        // Buying shards with in-game money through Vault (EssentialsX etc.).
        moneyShopEnabled = config.getBoolean("money-shop.enabled", true);
        moneyCostPerShard = Math.max(0.01D, config.getDouble("money-shop.cost-per-shard", 10.0D));
        moneyMin = Math.max(1L, config.getLong("money-shop.min-purchase", 1L));
        moneyMax = Math.max(moneyMin, config.getLong("money-shop.max-purchase", 100_000L));

        anvilProtect = config.getBoolean("protection.anvil", true);
        grindstoneProtect = config.getBoolean("protection.grindstone", true);

        soundAward = config.getString("sounds.award", "entity.experience_orb.pickup");
        soundPurchase = config.getString("sounds.purchase", "block.amethyst_block.resonate");
        soundDeny = config.getString("sounds.deny", "entity.villager.no");
        soundExpire = config.getString("sounds.expire", "entity.item.break");
        soundWarn = config.getString("sounds.warn", "block.note_block.pling");

        disabledWorlds.clear();
        disabledWorlds.addAll(upperCaseAll(config.getStringList("worlds.disabled")));
    }

    private static Set<String> upperCaseAll(List<String> input) {
        Set<String> out = new HashSet<>();
        for (String value : input) {
            if (value != null && !value.isEmpty()) {
                out.add(value.toUpperCase(Locale.ROOT).replace('.', '_'));
            }
        }
        return out;
    }

    private static int[] parseRange(String text) {
        if (text == null) {
            return null;
        }
        int dash = text.indexOf('-');
        try {
            int min;
            int max;
            if (dash > 0) {
                min = Integer.parseInt(text.substring(0, dash).trim());
                max = Integer.parseInt(text.substring(dash + 1).trim());
            } else {
                min = max = Integer.parseInt(text.trim());
            }
            return new int[]{Math.min(min, max), Math.max(min, max)};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int parseColor(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String hex = text.trim().replace("#", "");
        if (hex.length() == 6) {
            try {
                return Integer.parseInt(hex, 16) & 0xFFFFFF;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * One shared 3x3 list: pickaxe, axe and shovel all break the same blocks
     * (plus every *_ORE and every log - the axe fells whole trees when the
     * broken block itself is a log).
     */
    public boolean isAreaAllowed(Material material) {
        String name = material.name();
        return areaMaterials.contains(name) || name.endsWith("_ORE")
                || Tag.LOGS.isTagged(material);
    }

    public boolean isProtected(Material material) {
        return protectedMaterials.contains(material.name());
    }

    public boolean isContainer(Material material) {
        switch (material) {
            case CHEST:
            case TRAPPED_CHEST:
            case BARREL:
            case HOPPER:
            case DISPENSER:
            case DROPPER:
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case BREWING_STAND:
            case SHULKER_BOX:
                return true;
            default:
                return material.name().endsWith("_SHULKER_BOX");
        }
    }

    public int[] xpRange(Material material) {
        return oreXpValues.get(material.name());
    }

    public boolean isWorldDisabled(String worldName) {
        return worldName != null && disabledWorlds.contains(worldName.toUpperCase(Locale.ROOT));
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public String symbol() {
        return symbol;
    }

    public long startBalance() {
        return startBalance;
    }

    public boolean awardEnabled() {
        return awardEnabled;
    }

    public long awardIntervalMinutes() {
        return awardIntervalMinutes;
    }

    public long awardAmount() {
        return awardAmount;
    }

    public boolean awardAnnounce() {
        return awardAnnounce;
    }

    public boolean requireSneak() {
        return requireSneak;
    }

    public boolean allowCreative() {
        return allowCreative;
    }

    public int areaRadius() {
        return areaRadius;
    }

    public boolean oreXp() {
        return oreXp;
    }

    public boolean protectContainers() {
        return protectContainers;
    }

    public int treeMaxBlocks() {
        return treeMaxBlocks;
    }

    public boolean treeSameMaterialOnly() {
        return treeSameMaterialOnly;
    }

    public boolean treeReplant() {
        return treeReplant;
    }

    public boolean treeBreakLeaves() {
        return treeBreakLeaves;
    }

    public boolean mineSoundEnabled() {
        return mineSoundEnabled;
    }

    public List<SoundSpec> mineSounds() {
        return mineSounds;
    }

    public boolean mineParticlesEnabled() {
        return mineParticlesEnabled;
    }

    public String mineParticleId() {
        return mineParticleId;
    }

    public int mineParticleCount() {
        return mineParticleCount;
    }

    public boolean equipSoundEnabled() {
        return equipSoundEnabled;
    }

    public SoundSpec equipSound() {
        return equipSound;
    }

    public boolean equipParticlesEnabled() {
        return equipParticlesEnabled;
    }

    public String equipParticleId() {
        return equipParticleId;
    }

    public int equipParticleCount() {
        return equipParticleCount;
    }

    public long sweepSeconds() {
        return sweepSeconds;
    }

    public boolean loreRefresh() {
        return loreRefresh;
    }

    public long[] warnMinutes() {
        return warnMinutes;
    }

    public boolean scanOpenedInventories() {
        return scanOpenedInventories;
    }

    public long hasteDurationHours() {
        return hasteDurationHours;
    }

    public int hasteAmplifier() {
        return hasteAmplifier;
    }

    public int hasteColorRgb() {
        return hasteColorRgb;
    }

    public boolean shopConfirm() {
        return shopConfirm;
    }

    public int shopRows() {
        return shopRows;
    }

    public boolean moneyShopEnabled() {
        return moneyShopEnabled;
    }

    public double moneyCostPerShard() {
        return moneyCostPerShard;
    }

    public long moneyMinPurchase() {
        return moneyMin;
    }

    public long moneyMaxPurchase() {
        return moneyMax;
    }

    public String shopFiller() {
        return shopFiller;
    }

    public boolean anvilProtect() {
        return anvilProtect;
    }

    public boolean grindstoneProtect() {
        return grindstoneProtect;
    }

    public String soundAward() {
        return soundAward;
    }

    public String soundPurchase() {
        return soundPurchase;
    }

    public String soundDeny() {
        return soundDeny;
    }

    public String soundExpire() {
        return soundExpire;
    }

    public String soundWarn() {
        return soundWarn;
    }
}
