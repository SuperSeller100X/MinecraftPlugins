package dev.superseller.shardtools.item;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import dev.superseller.shardtools.ShardToolsPlugin;
import dev.superseller.shardtools.config.Settings;
import dev.superseller.shardtools.util.TimeWords;

/**
 * Builds shard items and reads their persistent data. Lifetimes are stored
 * per item as absolute real-world timestamps, which is what makes the
 * self-destruct keep ticking while the holder is offline or the server is
 * stopped.
 */
public final class ShardItems {

    private final ShardToolsPlugin plugin;
    private final NamespacedKey keyId;
    private final NamespacedKey keyCreated;
    private final NamespacedKey keyLifetime;
    private final NamespacedKey keyWarned;
    private final NamespacedKey keyMinutes;

    public ShardItems(ShardToolsPlugin plugin) {
        this.plugin = plugin;
        this.keyId = new NamespacedKey((Plugin) plugin, "item-id");
        this.keyCreated = new NamespacedKey((Plugin) plugin, "created");
        this.keyLifetime = new NamespacedKey((Plugin) plugin, "lifetime");
        this.keyWarned = new NamespacedKey((Plugin) plugin, "warned");
        this.keyMinutes = new NamespacedKey((Plugin) plugin, "minutes");
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
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(keyId, PersistentDataType.STRING);
    }

    public Long created(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        Long created = meta.getPersistentDataContainer().get(keyCreated, PersistentDataType.LONG);
        return created == null ? null : created;
    }

    public Long lifetime(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        Long lifetime = meta.getPersistentDataContainer().get(keyLifetime, PersistentDataType.LONG);
        return lifetime == null ? null : lifetime;
    }

    public boolean expired(ItemStack stack, long nowMs) {
        if (stack == null) {
            return false;
        }
        Long created = created(stack);
        Long lifetime = lifetime(stack);
        if (created == null || lifetime == null || lifetime <= 0L) {
            return false;
        }
        return nowMs >= created + lifetime;
    }

    /** Creates the purchasable/giveable item with a fresh lifetime. */
    public ItemStack create(ShardCatalog.Entry entry, int amount) {
        Material material = Material.matchMaterial(entry.material());
        if (material == null) {
            plugin.getLogger().warning("Unknown material '" + entry.material() + "' for item " + entry.id());
            material = Material.AIR;
        }
        int stackAmount = Math.max(1, Math.min(amount, Math.max(1, material.getMaxStackSize())));
        ItemStack stack = new ItemStack(material, stackAmount);
        long now = System.currentTimeMillis();
        stack.editMeta(meta -> apply(meta, entry, now, null));
        return stack;
    }

    /** Shop display icon: no persistent data, extra purchase lore. */
    public ItemStack displayIcon(ShardCatalog.Entry entry, long price) {
        Material material = Material.matchMaterial(entry.material());
        if (material == null) {
            material = Material.AIR;
        }
        ItemStack stack = new ItemStack(material, 1);
        stack.editMeta(meta -> apply(meta, entry, System.currentTimeMillis(), purchaseLore(price)));
        return stack;
    }

    private List<Component> purchaseLore(long price) {
        Settings settings = plugin.settings();
        List<Component> extra = new ArrayList<>();
        extra.add(plugin.messages().bare("shop.price-line",
                "%price%", dev.superseller.shardtools.util.Numbers.format(price),
                "%symbol%", settings.symbol()));
        extra.add(plugin.messages().bare("shop.click-to-buy"));
        return extra;
    }

    private void apply(ItemMeta meta, ShardCatalog.Entry entry, long now, List<Component> extraLore) {
        meta.displayName(plugin.messages().miniMessage().deserialize(entry.displayName()));
        List<Component> lore = renderLore(entry, entry.lifetimeMs(), extraLore);
        meta.lore(lore);
        for (EnchantSpec spec : entry.enchants()) {
            org.bukkit.enchantments.Enchantment enchantment = plugin.enchantResolver().resolve(spec.enchant());
            if (enchantment != null) {
                meta.addEnchant(enchantment, spec.level(), true);
            }
        }
        if (meta instanceof PotionMeta && entry.behavior() == Behavior.HASTE_POTION) {
            ((PotionMeta) meta).setColor(Color.fromRGB(plugin.settings().hasteColorRgb()));
        }
        if (extraLore == null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyId, PersistentDataType.STRING, entry.id());
            pdc.set(keyCreated, PersistentDataType.LONG, now);
            pdc.set(keyLifetime, PersistentDataType.LONG, entry.lifetimeMs());
            pdc.set(keyWarned, PersistentDataType.INTEGER, 0);
            pdc.set(keyMinutes, PersistentDataType.LONG, -1L);
        }
    }

    /** Renders lore templates with %time% and %price% filled in. */
    public List<Component> renderLore(ShardCatalog.Entry entry, long remainingMs, List<Component> extra) {
        Settings settings = plugin.settings();
        String time = entry.expires()
                ? TimeWords.format(remainingMs)
                : plugin.messages().raw("time.permanent");
        String price = dev.superseller.shardtools.util.Numbers.format(plugin.priceBook().price(entry.id()));
        List<Component> lore = new ArrayList<>();
        for (String template : entry.lore()) {
            String line = template.replace("%time%", time).replace("%price%", price)
                    .replace("%symbol%", settings.symbol());
            lore.add(plugin.messages().miniMessage().deserialize(line));
        }
        if (extra != null) {
            lore.addAll(extra);
        }
        return lore;
    }

    /** Outcome of {@link #tick}: nothing changed, lore/meta updated, or expired. */
    public enum TickResult {
        NONE,
        UPDATED,
        EXPIRED
    }

    /**
     * Updates the countdown lore and warning state of a held item.
     * Returns EXPIRED when the item should be removed, UPDATED when the
     * lore/meta changed (caller should refresh the inventory view).
     */
    public TickResult tick(ItemStack stack, ShardCatalog.Entry entry, long nowMs, Player holder) {
        Long created = created(stack);
        Long lifetime = lifetime(stack);
        if (created == null || lifetime == null || lifetime <= 0L) {
            return TickResult.NONE;
        }
        if (nowMs >= created + lifetime) {
            return TickResult.EXPIRED;
        }
        long remaining = created + lifetime - nowMs;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return TickResult.NONE;
        }
        boolean metaChanged = false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int warned = pdc.getOrDefault(keyWarned, PersistentDataType.INTEGER, 0);
        int due = dev.superseller.shardtools.expiry.ExpiryMath.dueWarning(
                remaining, plugin.settings().warnMinutes(), warned);
        if (due >= 0) {
            pdc.set(keyWarned, PersistentDataType.INTEGER, warned | (1 << due));
            plugin.messages().send(holder, "expire.warning", "%item%", entry.displayName(),
                    "%time%", TimeWords.format(remaining));
            play(holder, plugin.settings().soundWarn());
            metaChanged = true;
        }
        if (plugin.settings().loreRefresh()) {
            long minutesLeft = TimeWords.minutesOf(remaining);
            Long shown = pdc.get(keyMinutes, PersistentDataType.LONG);
            if (shown == null || shown != minutesLeft) {
                pdc.set(keyMinutes, PersistentDataType.LONG, minutesLeft);
                meta.lore(renderLore(entry, remaining, null));
                metaChanged = true;
            }
        }
        if (metaChanged) {
            stack.setItemMeta(meta);
            return TickResult.UPDATED;
        }
        return TickResult.NONE;
    }

    private void play(Player holder, String soundId) {
        Sound sound = plugin.soundResolver().resolve(soundId);
        if (sound != null) {
            holder.playSound(holder.getLocation(), sound, 1.0f, 1.0f);
        }
    }
}
