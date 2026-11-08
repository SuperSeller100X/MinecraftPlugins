package dev.superseller.veltryx.shards;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import dev.superseller.veltryx.util.Txt;

/**
 * Builds shard items and reads their persistent data. Lifetimes are stored
 * per item as absolute real-world timestamps, so the self-destruct keeps
 * ticking while the holder is offline or the server is stopped.
 */
public final class ShardItems {

    private final NamespacedKey keyId;
    private final NamespacedKey keyCreated;
    private final NamespacedKey keyLifetime;
    private final NamespacedKey keyWarned;
    private final NamespacedKey keyAbilityOff;
    private final NamespacedKey keyEffectMinutes;

    public ShardItems(Plugin plugin) {
        this.keyId = new NamespacedKey(plugin, "sid");
        this.keyCreated = new NamespacedKey(plugin, "screated");
        this.keyLifetime = new NamespacedKey(plugin, "slife");
        this.keyWarned = new NamespacedKey(plugin, "swarned");
        this.keyAbilityOff = new NamespacedKey(plugin, "soff");
        this.keyEffectMinutes = new NamespacedKey(plugin, "seffmin");
    }

    /** Item id stored on the stack, or {@code null} for non-shard items. */
    public String itemId(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
    }

    public Long created(ItemStack stack) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(keyCreated, PersistentDataType.LONG);
    }

    public Long lifetime(ItemStack stack) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(keyLifetime, PersistentDataType.LONG);
    }

    public boolean expired(ItemStack stack, long nowMs) {
        Long created = created(stack);
        Long lifetime = lifetime(stack);
        if (created == null || lifetime == null || lifetime <= 0L) {
            return false;
        }
        return nowMs >= created + lifetime;
    }

    /** True unless the holder switched the tool's ability off with /shard toggle. */
    public boolean abilityEnabled(ItemStack stack) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta == null) {
            return true;
        }
        Integer off = meta.getPersistentDataContainer().get(keyAbilityOff, PersistentDataType.INTEGER);
        return off == null || off == 0;
    }

    /** Flips the ability flag on the stack and returns the NEW enabled state. */
    public boolean toggleAbility(ItemStack stack) {
        boolean enable = !abilityEnabled(stack);
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta == null) {
            return true;
        }
        meta.getPersistentDataContainer().set(keyAbilityOff, PersistentDataType.INTEGER, enable ? 0 : 1);
        stack.setItemMeta(meta);
        return enable;
    }

    /** Bitset of already-sent expiry warnings. */
    public int warnedBits(ItemStack stack) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer bits = meta.getPersistentDataContainer().get(keyWarned, PersistentDataType.INTEGER);
        return bits == null ? 0 : bits;
    }

    public void markWarned(ItemStack stack, int bitIndex) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(keyWarned, PersistentDataType.INTEGER,
                warnedBits(stack) | (1 << bitIndex));
        stack.setItemMeta(meta);
    }

    /** Haste effect duration in minutes stored on the potion, or null for the default. */
    public Long effectMinutes(ItemStack stack) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        Long minutes = meta.getPersistentDataContainer().get(keyEffectMinutes, PersistentDataType.LONG);
        return minutes == null || minutes <= 0L ? null : minutes;
    }

    /**
     * Creates the purchasable item with a fresh lifetime.
     *
     * @param choiceIndex selects one of the entry's pick-one enchant
     *                    alternatives (0 = Fortune III, 1 = Silk Touch).
     */
    public ItemStack create(ShardCatalog.Entry entry, int choiceIndex) {
        Material material = Material.matchMaterial(entry.material());
        if (material == null) {
            material = Material.AIR;
        }
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Txt.legacy("§b" + entry.displayName()));
        List<String> lines = new ArrayList<>(entry.lore());
        if (entry.expires()) {
            lines.add("§cThis item self-destructs 24h after purchase!");
        }
        meta.lore(Txt.legacy(lines));
        for (ShardCatalog.EnchantSpec spec : entry.enchants()) {
            meta.addEnchant(spec.enchantment(), spec.level(), true);
        }
        if (entry.hasChoice()) {
            ShardCatalog.EnchantSpec chosen = entry.choice(choiceIndex);
            meta.addEnchant(chosen.enchantment(), chosen.level(), true);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        long now = System.currentTimeMillis();
        pdc.set(keyId, PersistentDataType.STRING, entry.id());
        pdc.set(keyCreated, PersistentDataType.LONG, now);
        pdc.set(keyLifetime, PersistentDataType.LONG, entry.lifetimeMs());
        if (entry.behavior() == Behavior.HASTE_POTION) {
            pdc.set(keyEffectMinutes, PersistentDataType.LONG, 60L);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** Shop display icon: no persistent data, just the item + price lore. */
    public ItemStack displayIcon(ShardCatalog.Entry entry, long priceShards) {
        Material material = Material.matchMaterial(entry.material());
        if (material == null) {
            material = Material.AIR;
        }
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Txt.legacy("§b" + entry.displayName()));
        List<String> lines = new ArrayList<>(entry.lore());
        lines.add("§6Price: " + priceShards + " shards");
        lines.add("§eClick to buy");
        if (entry.hasChoice()) {
            lines.add("§7Right-click to pick Fortune III or Silk Touch");
        }
        meta.lore(Txt.legacy(lines));
        stack.setItemMeta(meta);
        return stack;
    }

    /** True when the entry is a shard tool carrying a toggleable ability. */
    public boolean hasAbility(ShardCatalog.Entry entry) {
        return entry != null && (entry.behavior() == Behavior.AREA_MINE
                || entry.behavior() == Behavior.TREE_AXE
                || entry.behavior() == Behavior.LUNGE_SPEAR
                || entry.behavior() == Behavior.RIPTIDE_TRIDENT);
    }
}
