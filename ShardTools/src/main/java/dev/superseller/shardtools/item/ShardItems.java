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
    private final NamespacedKey keyAbilityOff;
    private final NamespacedKey keyEffectMinutes;

    public ShardItems(ShardToolsPlugin plugin) {
        this.plugin = plugin;
        this.keyId = new NamespacedKey((Plugin) plugin, "item-id");
        this.keyCreated = new NamespacedKey((Plugin) plugin, "created");
        this.keyLifetime = new NamespacedKey((Plugin) plugin, "lifetime");
        this.keyWarned = new NamespacedKey((Plugin) plugin, "warned");
        this.keyAbilityOff = new NamespacedKey((Plugin) plugin, "ability-off");
        this.keyEffectMinutes = new NamespacedKey((Plugin) plugin, "effect-minutes");
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

    /** True unless the holder switched the tool's ability off with /st toggle. */
    public boolean abilityEnabled(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return true;
        }
        Integer off = meta.getPersistentDataContainer().get(keyAbilityOff, PersistentDataType.INTEGER);
        return off == null || off == 0;
    }

    /** Flips the ability flag on the stack and returns the NEW enabled state. */
    public boolean toggleAbility(ItemStack stack) {
        boolean enable = !abilityEnabled(stack);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return true;
        }
        meta.getPersistentDataContainer().set(keyAbilityOff, PersistentDataType.INTEGER, enable ? 0 : 1);
        stack.setItemMeta(meta);
        return enable;
    }

    /**
     * Haste effect duration stored on the potion (minutes) when it was bought
     * with extra time, or {@code null} to use the configured default.
     */
    public Long effectMinutes(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        Long minutes = meta.getPersistentDataContainer().get(keyEffectMinutes, PersistentDataType.LONG);
        return minutes == null || minutes <= 0L ? null : minutes;
    }

    /**
     * True when the shop should offer buying extra time for this entry:
     * expiring shard items get extra lifetime, the haste potion gets a
     * longer effect duration.
     */
    public boolean extendable(ShardCatalog.Entry entry) {
        if (!plugin.settings().extendEnabled() || entry.isCommandItem()) {
            return false;
        }
        return entry.behavior() == Behavior.HASTE_POTION || entry.expires();
    }

    /** Creates the purchasable/giveable item with a fresh lifetime. */
    public ItemStack create(ShardCatalog.Entry entry, int amount) {
        return create(entry, amount, 0L, 0);
    }

    /** Creates the item with purchased extra time (first enchant choice). */
    public ItemStack create(ShardCatalog.Entry entry, int amount, long extraMinutes) {
        return create(entry, amount, extraMinutes, 0);
    }

    /**
     * Creates the item with purchased extra time on top and the picked
     * enchant alternative: expiring shard items live {@code extraMinutes}
     * longer before self-destructing; the haste potion instead grants its
     * effect for that much longer. {@code choiceIndex} selects one of the
     * entry's enchant-choice alternatives (e.g. Fortune vs Silk Touch).
     */
    public ItemStack create(ShardCatalog.Entry entry, int amount, long extraMinutes, int choiceIndex) {
        Material material = Material.matchMaterial(entry.material());
        if (material == null) {
            plugin.getLogger().warning("Unknown material '" + entry.material() + "' for item " + entry.id());
            material = Material.AIR;
        }
        int stackAmount = Math.max(1, Math.min(amount, Math.max(1, material.getMaxStackSize())));
        ItemStack stack = new ItemStack(material, stackAmount);
        long now = System.currentTimeMillis();
        long extra = Math.max(0L, extraMinutes);
        long lifetimeMs = entry.lifetimeMs();
        long effectMinutes = -1L;
        if (extra > 0L) {
            if (entry.behavior() == Behavior.HASTE_POTION) {
                effectMinutes = plugin.settings().hasteDurationHours() * 60L + extra;
            } else if (entry.expires()) {
                lifetimeMs += extra * 60_000L;
            }
        }
        final long finalLifetime = lifetimeMs;
        final long finalEffect = effectMinutes;
        final EnchantSpec choice = entry.enchantChoice(choiceIndex);
        stack.editMeta(meta -> apply(meta, entry, now, null, finalLifetime, finalEffect, choice));
        return stack;
    }

    /** Shop display icon: no persistent data, extra purchase lore. */
    public ItemStack displayIcon(ShardCatalog.Entry entry, long price) {
        Material material = Material.matchMaterial(entry.material());
        if (material == null) {
            material = Material.AIR;
        }
        ItemStack stack = new ItemStack(material, 1);
        stack.editMeta(meta -> apply(meta, entry, System.currentTimeMillis(),
                purchaseLore(entry, price), entry.lifetimeMs(), -1L, null));
        return stack;
    }

    /**
     * Purchase-dialog preview icon: display only (no persistent data), with
     * the PROJECTED lifetime/effect duration and the currently selected
     * enchant baked in, so the item's own tooltip lines (the red
     * self-destruct text, the "when drunk" line, the enchant list) update
     * live while the buyer clicks around. The real item is only created
     * once, on confirm.
     */
    public ItemStack previewIcon(ShardCatalog.Entry entry, long price,
                                 long lifetimeMs, long effectMinutes, EnchantSpec choice) {
        Material material = Material.matchMaterial(entry.material());
        if (material == null) {
            material = Material.AIR;
        }
        List<Component> extra = new ArrayList<>();
        extra.add(plugin.messages().itemLine("shop.price-line",
                "%price%", dev.superseller.shardtools.util.Numbers.format(price),
                "%symbol%", plugin.settings().symbol()));
        ItemStack stack = new ItemStack(material, 1);
        stack.editMeta(meta -> apply(meta, entry, System.currentTimeMillis(),
                extra, lifetimeMs, effectMinutes, choice));
        return stack;
    }

    private List<Component> purchaseLore(ShardCatalog.Entry entry, long price) {
        Settings settings = plugin.settings();
        List<Component> extra = new ArrayList<>();
        extra.add(plugin.messages().itemLine("shop.price-line",
                "%price%", dev.superseller.shardtools.util.Numbers.format(price),
                "%symbol%", settings.symbol()));
        extra.add(plugin.messages().itemLine("shop.click-to-buy"));
        if (entry.hasEnchantChoice()) {
            extra.add(plugin.messages().itemLine("choice.hint"));
        }
        if (extendable(entry)) {
            extra.add(plugin.messages().itemLine("extend.hint"));
        }
        return extra;
    }

    private void apply(ItemMeta meta, ShardCatalog.Entry entry, long now, List<Component> extraLore,
                       long lifetimeMs, long effectMinutes, EnchantSpec choice) {
        boolean shopIcon = extraLore != null;
        // Items without an ability stay plain vanilla when handed out: no
        // custom name, no rarity color, no lore - just the enchantments.
        boolean vanillaLook = !shopIcon && entry.behavior() == Behavior.NONE;
        if (!vanillaLook) {
            meta.displayName(plugin.messages().itemText(entry.displayName()));
        }
        if (!vanillaLook || lifetimeMs > 0L) {
            long effectMs = effectMinutes > 0L
                    ? effectMinutes * 60_000L : defaultEffectMs();
            List<Component> lore = renderLore(entry, lifetimeMs, effectMs, extraLore);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
        }
        for (EnchantSpec spec : entry.enchants()) {
            org.bukkit.enchantments.Enchantment enchantment = plugin.enchantResolver().resolve(spec.enchant());
            if (enchantment != null) {
                meta.addEnchant(enchantment, spec.level(), true);
            }
        }
        if (choice != null) {
            org.bukkit.enchantments.Enchantment enchantment = plugin.enchantResolver().resolve(choice.enchant());
            if (enchantment != null) {
                meta.addEnchant(enchantment, choice.level(), true);
            }
        }
        if (meta instanceof PotionMeta && entry.behavior() == Behavior.HASTE_POTION) {
            ((PotionMeta) meta).setColor(Color.fromRGB(plugin.settings().hasteColorRgb()));
        }
        // Custom resource-pack model for this entry only (e.g. the Void
        // Totem); every other item of the same material stays vanilla.
        if (entry.itemModel() != null) {
            NamespacedKey modelKey = NamespacedKey.fromString(entry.itemModel());
            if (modelKey != null) {
                meta.setItemModel(modelKey);
            } else {
                plugin.getLogger().warning(
                        "Invalid item-model '" + entry.itemModel() + "' for item " + entry.id());
            }
        }
        // Persistent data only where the plugin still has work to do: items
        // with an ability or a self-destruct timer. Plain vanilla items carry
        // no ShardTools data at all.
        if (!shopIcon && (entry.behavior() != Behavior.NONE || lifetimeMs > 0L)) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyId, PersistentDataType.STRING, entry.id());
            pdc.set(keyCreated, PersistentDataType.LONG, now);
            pdc.set(keyLifetime, PersistentDataType.LONG, lifetimeMs);
            pdc.set(keyWarned, PersistentDataType.INTEGER, 0);
            if (effectMinutes > 0L) {
                pdc.set(keyEffectMinutes, PersistentDataType.LONG, effectMinutes);
            }
        }
    }

    /** Configured default haste effect duration in milliseconds. */
    public long defaultEffectMs() {
        return plugin.settings().hasteDurationHours() * 3_600_000L;
    }

    /** Renders lore templates with %time%, %price% and %effect% filled in. */
    public List<Component> renderLore(ShardCatalog.Entry entry, long remainingMs, List<Component> extra) {
        return renderLore(entry, remainingMs, defaultEffectMs(), extra);
    }

    /** Renders lore templates; %effect% shows the (possibly extended) haste duration. */
    public List<Component> renderLore(ShardCatalog.Entry entry, long remainingMs, long effectMs,
                                      List<Component> extra) {
        Settings settings = plugin.settings();
        String time = entry.expires()
                ? TimeWords.format(remainingMs)
                : plugin.messages().raw("time.permanent");
        String price = dev.superseller.shardtools.util.Numbers.format(plugin.priceBook().price(entry.id()));
        String effect = TimeWords.format(effectMs);
        List<Component> lore = new ArrayList<>();
        for (String template : entry.lore()) {
            String line = template.replace("%time%", time).replace("%price%", price)
                    .replace("%effect%", effect).replace("%symbol%", settings.symbol());
            lore.add(plugin.messages().itemText(line));
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
        if (metaChanged) {
            stack.setItemMeta(meta);
        }
        boolean loreChanged = refreshLore(stack, entry, nowMs);
        return metaChanged || loreChanged ? TickResult.UPDATED : TickResult.NONE;
    }

    /**
     * Display-only countdown resync: re-renders the dynamic lore lines (the
     * red remaining-lifetime text and the potion "when drunk" duration) from
     * the item's immutable creation data. Never writes persistent data - the
     * item itself stays untouched; only the visible tooltip text is brought
     * up to date, and only when it actually differs from what is shown.
     * Returns true when the tooltip changed (caller resyncs the client).
     */
    public boolean refreshLore(ItemStack stack, ShardCatalog.Entry entry, long nowMs) {
        if (!plugin.settings().loreRefresh()) {
            return false;
        }
        Long created = created(stack);
        Long lifetime = lifetime(stack);
        if (created == null || lifetime == null || lifetime <= 0L) {
            return false;
        }
        long remaining = created + lifetime - nowMs;
        if (remaining <= 0L) {
            return false;   // expiry sweep destroys it, nothing to display
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Long effectOverride = meta.getPersistentDataContainer().get(keyEffectMinutes, PersistentDataType.LONG);
        long effectMs = effectOverride != null && effectOverride > 0L
                ? effectOverride * 60_000L : defaultEffectMs();
        List<Component> fresh = renderLore(entry, remaining, effectMs, null);
        if (fresh.isEmpty() || fresh.equals(meta.lore())) {
            return false;
        }
        meta.lore(fresh);
        stack.setItemMeta(meta);
        return true;
    }

    private void play(Player holder, String soundId) {
        Sound sound = plugin.soundResolver().resolve(soundId);
        if (sound != null) {
            holder.playSound(holder.getLocation(), sound, 1.0f, 1.0f);
        }
    }
}
