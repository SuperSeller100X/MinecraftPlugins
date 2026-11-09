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

    /** Matches the item-model definition in the bundled ShardTools resource pack. */
    public static final NamespacedKey VOID_TOTEM_MODEL =
            java.util.Objects.requireNonNull(NamespacedKey.fromString("shardtools:void_totem"));
    private static final NamespacedKey VANILLA_TOTEM_MODEL = NamespacedKey.minecraft("totem_of_undying");

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

    /** Whether the server knows the material used by this entry. */
    public boolean available(ShardCatalog.Entry entry) {
        if (entry == null) return false;
        Material material = Material.matchMaterial(entry.material());
        return material != null && material != Material.AIR;
    }

    /**
     * Upgrade older Veltryx items without resetting their timestamp, lore,
     * enchantments, durability or other persistent metadata. Returns the
     * original object unchanged when no migration is needed.
     */
    public ItemStack upgradeLegacy(ItemStack original) {
        String id = itemId(original);
        if ("shard_spear".equals(id)) {
            Material spear = Material.matchMaterial("NETHERITE_SPEAR");
            if (spear == null || (original.getType() != Material.NETHERITE_SWORD && original.getType() != spear)) return original;
            boolean needsType = original.getType() != spear;
            boolean needsLunge = original.getEnchantmentLevel(Enchantment.LUNGE) <= 0;
            if (!needsType && !needsLunge) return original;
            ItemStack upgraded = original.clone();
            if (needsType) upgraded.setType(spear);
            if (!id.equals(itemId(upgraded)) || !retainsMetadata(original, upgraded)) return original;
            if (needsLunge) {
                ItemMeta meta = upgraded.getItemMeta();
                if (meta == null || !meta.addEnchant(Enchantment.LUNGE, 3, true) || !upgraded.setItemMeta(meta)
                        || upgraded.getEnchantmentLevel(Enchantment.LUNGE) != 3 || !id.equals(itemId(upgraded))) return original;
            }
            return upgraded;
        }
        if ("void_totem".equals(id) && original.getType() == Material.TOTEM_OF_UNDYING) {
            ItemMeta oldMeta = original.getItemMeta();
            if (oldMeta == null) return original;
            NamespacedKey current = oldMeta.getItemModel();
            // A different model or model-data selector may be an intentional
            // server customization, so leave it alone.
            if ((current != null && !current.equals(VANILLA_TOTEM_MODEL))
                    || oldMeta.hasCustomModelDataComponent()) return original;
            ItemStack upgraded = original.clone();
            ItemMeta meta = upgraded.getItemMeta();
            if (meta == null) return original;
            meta.setItemModel(VOID_TOTEM_MODEL);
            return upgraded.setItemMeta(meta) ? upgraded : original;
        }
        return original;
    }

    private boolean retainsMetadata(ItemStack before, ItemStack after) {
        ItemMeta a = before.getItemMeta(), b = after.getItemMeta();
        if (a == null || b == null || !a.equals(b)) return false;
        if (!a.getPersistentDataContainer().equals(b.getPersistentDataContainer())
                || !a.getEnchants().equals(b.getEnchants())
                || !java.util.Objects.equals(a.displayName(), b.displayName())
                || !java.util.Objects.equals(a.lore(), b.lore())
                || !java.util.Objects.equals(a.getItemModel(), b.getItemModel())) return false;
        if (a instanceof org.bukkit.inventory.meta.Damageable) {
            return b instanceof org.bukkit.inventory.meta.Damageable
                    && ((org.bukkit.inventory.meta.Damageable) a).getDamage()
                    == ((org.bukkit.inventory.meta.Damageable) b).getDamage();
        }
        return true;
    }

    /**
     * Creates the purchasable item with a fresh lifetime.
     *
     * @param choiceIndex selects one of the entry's pick-one enchant
     *                    alternatives (0 = Fortune III, 1 = Silk Touch).
     */
    public ItemStack create(ShardCatalog.Entry entry, int choiceIndex) {
        if (!available(entry)) throw new IllegalArgumentException("Unavailable shard item");
        Material material = Material.matchMaterial(entry.material());
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) throw new IllegalStateException("Cannot create shard item metadata");
        applyAppearance(meta, entry);
        List<String> lines = new ArrayList<>(entry.lore());
        if (entry.expires()) lines.add("§8Self-destructs §c24 hours §8after purchase.");
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
        if (!stack.setItemMeta(meta) || !entry.id().equals(itemId(stack))
                || !Long.valueOf(now).equals(created(stack)) || !Long.valueOf(entry.lifetimeMs()).equals(lifetime(stack))) {
            throw new IllegalStateException("Could not save shard item metadata");
        }
        for (ShardCatalog.EnchantSpec spec : entry.enchants()) {
            if (stack.getEnchantmentLevel(spec.enchantment()) != spec.level())
                throw new IllegalStateException("Could not save required shard enchantment");
        }
        if (entry.hasChoice()) {
            ShardCatalog.EnchantSpec chosen = entry.choice(choiceIndex);
            if (stack.getEnchantmentLevel(chosen.enchantment()) != chosen.level())
                throw new IllegalStateException("Could not save chosen shard enchantment");
        }
        if ("void_totem".equals(entry.id())
                && !VOID_TOTEM_MODEL.equals(stack.getItemMeta().getItemModel())) {
            throw new IllegalStateException("Could not save Void Totem item model");
        }
        return stack;
    }

    /** Shop display icon: no persistent data, just the item + price lore. */
    public ItemStack displayIcon(ShardCatalog.Entry entry, long priceShards) {
        if (!available(entry)) {
            ItemStack unavailable = new ItemStack(Material.BARRIER, 1);
            ItemMeta meta = unavailable.getItemMeta();
            if (meta != null) {
                meta.displayName(Txt.legacy("§c" + entry.displayName()));
                meta.lore(Txt.legacy(List.of("§7Unavailable on this server version.")));
                unavailable.setItemMeta(meta);
            }
            return unavailable;
        }
        Material material = Material.matchMaterial(entry.material());
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        applyAppearance(meta, entry);
        List<String> lines = new ArrayList<>(entry.lore());
        if (entry.expires()) lines.add("§8Self-destructs §c24 hours §8after purchase.");
        lines.add("§7Price: §6" + priceShards + " shards");
        lines.add(entry.hasChoice() ? "§eClick to choose an enchantment" : "§eClick to buy");
        meta.lore(Txt.legacy(lines));
        stack.setItemMeta(meta);
        return stack;
    }

    private void applyAppearance(ItemMeta meta, ShardCatalog.Entry entry) {
        boolean totem = "void_totem".equals(entry.id());
        meta.displayName(Txt.legacy((totem ? "§d" : "§b") + entry.displayName()));
        if (totem) meta.setItemModel(VOID_TOTEM_MODEL);
    }

    /** True when the entry is a shard tool carrying a toggleable ability. */
    public boolean hasAbility(ShardCatalog.Entry entry) {
        return entry != null && (entry.behavior() == Behavior.AREA_MINE
                || entry.behavior() == Behavior.TREE_AXE
                || entry.behavior() == Behavior.LUNGE_SPEAR
                || entry.behavior() == Behavior.RIPTIDE_TRIDENT);
    }
}
