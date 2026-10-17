package dev.superseller.shardtools.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import dev.superseller.shardtools.ShardToolsPlugin;
import dev.superseller.shardtools.item.Behavior;
import dev.superseller.shardtools.item.EnchantSpec;
import dev.superseller.shardtools.item.IncompatibilityMap;
import dev.superseller.shardtools.item.ShardCatalog;
import dev.superseller.shardtools.util.Numbers;
import dev.superseller.shardtools.util.TimeWords;

/**
 * DonutSMP-style shard shop GUI with automatic pagination and an optional
 * confirmation step.
 */
public final class ShopGui {

    /** Holder for a shop page. */
    public static final class ShopHolder implements InventoryHolder {

        private Inventory inventory;
        private final int page;
        private final int pages;
        private final Map<Integer, String> slotItems = new HashMap<>();

        public ShopHolder(int page, int pages) {
            this.page = page;
            this.pages = pages;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public int page() {
            return page;
        }

        public int pages() {
            return pages;
        }

        public Map<Integer, String> slotItems() {
            return slotItems;
        }
    }

    /** Holder for the buy-confirmation dialog. */
    public static final class ConfirmHolder implements InventoryHolder {

        private Inventory inventory;
        private final String itemId;
        private final int backPage;

        public ConfirmHolder(String itemId, int backPage) {
            this.itemId = itemId;
            this.backPage = backPage;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public String itemId() {
            return itemId;
        }

        public int backPage() {
            return backPage;
        }
    }

    /** Holder for the buy-with-extra-time / enchant-choice dialog. */
    public static final class ExtendHolder implements InventoryHolder {

        /** Raw slots of the dialog's buttons. */
        public static final int SLOT_CHOICE = 4;
        public static final int SLOT_MINUS = 11;
        public static final int SLOT_ICON = 13;
        public static final int SLOT_PLUS = 15;
        public static final int SLOT_CONFIRM = 21;
        public static final int SLOT_CANCEL = 23;

        private Inventory inventory;
        private final String itemId;
        private final int backPage;
        private int steps;
        private int choice;

        public ExtendHolder(String itemId, int backPage) {
            this.itemId = itemId;
            this.backPage = backPage;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public String itemId() {
            return itemId;
        }

        public int backPage() {
            return backPage;
        }

        /** Currently selected number of extra-time steps. */
        public int steps() {
            return steps;
        }

        public void steps(int steps) {
            this.steps = steps;
        }

        /** Currently selected enchant-choice index (e.g. Fortune vs Silk Touch). */
        public int choice() {
            return choice;
        }

        public void choice(int choice) {
            this.choice = choice;
        }
    }

    /**
     * Holder for the full enchantment picker dialog (mcpvp.club style).
     * Every selectable enchantment is a toggleable slot: enchanted book
     * when on, plain book when off. Incompatible enchantments auto-deselect
     * when their conflict is enabled (e.g. Mending ↔ Infinity on bows).
     */
    public static final class EnchantPickerHolder implements InventoryHolder {

        /** Maximum enchantment slots in the picker (two rows of 7). */
        public static final int MAX_ENCHANT_SLOTS = 14;

        /** Slot positions for enchantment toggles (rows 1-2 of a 5-row chest). */
        public static final int[] ENCHANT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
        };

        public static final int SLOT_MINUS = 37;
        public static final int SLOT_PREVIEW = 40;
        public static final int SLOT_PLUS = 43;
        public static final int SLOT_CANCEL = 36;
        public static final int SLOT_CONFIRM = 44;

        private Inventory inventory;
        private final String itemId;
        private final int backPage;
        private int steps;
        /** Indices into the entry's selectableEnchants() list that are currently on. */
        private final java.util.Set<Integer> selected = new java.util.HashSet<>();

        public EnchantPickerHolder(String itemId, int backPage) {
            this.itemId = itemId;
            this.backPage = backPage;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public String itemId() {
            return itemId;
        }

        public int backPage() {
            return backPage;
        }

        public int steps() {
            return steps;
        }

        public void steps(int steps) {
            this.steps = steps;
        }

        public java.util.Set<Integer> selected() {
            return selected;
        }

        /** Toggle the enchant at {@code index} in the selectable list. */
        public void toggle(int index) {
            if (selected.contains(index)) {
                selected.remove(index);
            } else {
                selected.add(index);
            }
        }

        public boolean isSelected(int index) {
            return selected.contains(index);
        }
    }

    private final ShardToolsPlugin plugin;

    public ShopGui(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    public int pageCount() {
        int rows = plugin.settings().shopRows();
        int pageSize = Math.max(1, (rows - 2) * 7);
        return Math.max(1, (plugin.catalog().ordered().size() + pageSize - 1) / pageSize);
    }

    public void open(Player player, int page) {
        int pages = pageCount();
        int safePage = Math.max(0, Math.min(page, pages - 1));
        int rows = plugin.settings().shopRows();
        ShopHolder holder = new ShopHolder(safePage, pages);
        long balance = plugin.accounts().balance(player.getUniqueId(), player.getName());
        Component title = plugin.messages().bare("shop.title",
                "%balance%", Numbers.format(balance),
                "%symbol%", plugin.settings().symbol());
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory(inventory);

        ItemStack filler = borderItem();
        for (int slot = 0; slot < rows * 9; slot++) {
            inventory.setItem(slot, filler);
        }

        int pageSize = Math.max(1, (rows - 2) * 7);
        List<ShardCatalog.Entry> catalog = plugin.catalog().ordered();
        int from = safePage * pageSize;
        int index = 0;
        for (int row = 1; row <= rows - 2 && from + index < catalog.size(); row++) {
            for (int col = 1; col <= 7 && from + index < catalog.size(); col++) {
                ShardCatalog.Entry entry = catalog.get(from + index);
                Long price = plugin.priceBook().price(entry.id());
                if (price == null) {
                    price = 0L;
                }
                int slot = row * 9 + col;
                inventory.setItem(slot, plugin.items().displayIcon(entry, price));
                holder.slotItems().put(slot, entry.id());
                index++;
            }
        }

        int bottomStart = (rows - 1) * 9;
        if (safePage > 0) {
            inventory.setItem(bottomStart + 2, navItem("ARROW",
                    plugin.messages().itemLine("shop.previous-page"), "__prev__", holder, bottomStart + 2));
        }
        if (safePage < pages - 1) {
            inventory.setItem(bottomStart + 6, navItem("ARROW",
                    plugin.messages().itemLine("shop.next-page"), "__next__", holder, bottomStart + 6));
        }
        inventory.setItem(bottomStart + 4, infoItem(player, safePage, pages));

        player.openInventory(inventory);
    }

    public void openConfirm(Player player, ShardCatalog.Entry entry, int backPage) {
        ConfirmHolder holder = new ConfirmHolder(entry.id(), backPage);
        Component title = plugin.messages().bare("shop.confirm-title", "%item%", entry.displayName());
        Inventory inventory = Bukkit.createInventory(holder, 27, title);
        holder.inventory(inventory);
        ItemStack filler = borderItem();
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler);
        }
        Long price = plugin.priceBook().price(entry.id());
        ItemStack icon = plugin.items().displayIcon(entry, price == null ? 0L : price);
        List<Component> lore = icon.getItemMeta() != null && icon.getItemMeta().lore() != null
                ? new ArrayList<>(icon.getItemMeta().lore())
                : new ArrayList<>();
        lore.add(plugin.messages().itemLine("shop.click-to-confirm"));
        ItemMeta iconMeta = icon.getItemMeta();
        if (iconMeta != null) {
            iconMeta.lore(lore);
            icon.setItemMeta(iconMeta);
        }
        inventory.setItem(11, icon);
        inventory.setItem(15, navItem("RED_STAINED_GLASS_PANE",
                plugin.messages().itemLine("shop.click-to-cancel"), null, null, -1));
        player.openInventory(inventory);
    }

    // ---------------------------------------------------------------
    // Buy dialog: extra time and/or enchant choice
    // ---------------------------------------------------------------

    /**
     * Opens the purchase dialog for an item that is extendable (buy extra
     * time) and/or offers an enchant choice (e.g. Fortune vs Silk Touch).
     */
    public void openExtend(Player player, ShardCatalog.Entry entry, int backPage, int steps) {
        ExtendHolder holder = new ExtendHolder(entry.id(), backPage);
        holder.steps(clampSteps(entry, steps));
        String titleKey = plugin.items().extendable(entry) ? "extend.title" : "choice.title";
        Component title = plugin.messages().bare(titleKey, "%item%", entry.displayName());
        Inventory inventory = Bukkit.createInventory(holder, 27, title);
        holder.inventory(inventory);
        renderExtend(holder, entry);
        player.openInventory(inventory);
    }

    /** (Re-)draws the purchase dialog after the selection changed. */
    public void renderExtend(ExtendHolder holder, ShardCatalog.Entry entry) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        boolean potion = entry.behavior() == Behavior.HASTE_POTION;
        boolean extendable = plugin.items().extendable(entry);
        long stepMinutes = potion
                ? plugin.settings().extendPotionStepMinutes() : plugin.settings().extendToolStepMinutes();
        long stepPrice = potion
                ? plugin.settings().extendPotionStepPrice() : plugin.settings().extendToolStepPrice();
        int maxSteps = potion
                ? plugin.settings().extendPotionMaxSteps() : plugin.settings().extendToolMaxSteps();
        int steps = clampSteps(entry, holder.steps());
        holder.steps(steps);

        ItemStack filler = borderItem();
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler);
        }
        if (extendable) {
            String stepTime = TimeWords.format(stepMinutes * 60_000L);
            String stepCost = Numbers.format(stepPrice);
            // Only show the -/+ buttons while they can actually change
            // something: no minus pane at 0 extra time, no plus at the cap.
            if (steps > 0) {
                inventory.setItem(ExtendHolder.SLOT_MINUS, button("RED_STAINED_GLASS_PANE",
                        plugin.messages().itemLine("extend.minus",
                                "%time%", stepTime, "%price%", stepCost,
                                "%symbol%", plugin.settings().symbol())));
            }
            if (steps < maxSteps) {
                inventory.setItem(ExtendHolder.SLOT_PLUS, button("LIME_STAINED_GLASS_PANE",
                        plugin.messages().itemLine("extend.plus",
                                "%time%", stepTime, "%price%", stepCost,
                                "%symbol%", plugin.settings().symbol())));
            }
        }

        EnchantSpec chosen = null;
        if (entry.hasEnchantChoice()) {
            int choiceIndex = Math.floorMod(holder.choice(), entry.enchantChoices().size());
            holder.choice(choiceIndex);
            chosen = entry.enchantChoice(choiceIndex);
            inventory.setItem(ExtendHolder.SLOT_CHOICE, button("ENCHANTED_BOOK",
                    plugin.messages().itemLine("choice.button",
                            "%enchant%", prettyEnchant(chosen)),
                    plugin.messages().itemLine("choice.switch")));
        }

        Long base = plugin.priceBook().price(entry.id());
        long basePrice = base == null ? 0L : base;
        long extraMs = steps * stepMinutes * 60_000L;
        long totalPrice = basePrice + steps * stepPrice;
        long baseMs = potion
                ? plugin.items().defaultEffectMs() : entry.lifetimeMs();

        // Display-only preview: the icon's own tooltip lines (red
        // self-destruct countdown, "when drunk" duration, enchant list)
        // reflect the current selection live. The real item is only built
        // once, when the purchase is confirmed.
        ItemStack icon = potion
                ? plugin.items().previewIcon(entry, basePrice,
                        entry.lifetimeMs(), (baseMs + extraMs) / 60_000L, chosen)
                : plugin.items().previewIcon(entry, basePrice,
                        baseMs + extraMs, -1L, chosen);
        ItemMeta iconMeta = icon.getItemMeta();
        if (iconMeta != null) {
            List<Component> lore = iconMeta.lore() != null
                    ? new ArrayList<>(iconMeta.lore()) : new ArrayList<>();
            if (extendable) {
                lore.add(plugin.messages().itemLine(potion ? "extend.effect-line" : "extend.lifetime-line",
                        "%base%", TimeWords.format(baseMs),
                        "%extra%", TimeWords.format(extraMs),
                        "%total%", TimeWords.format(baseMs + extraMs)));
            }
            lore.add(plugin.messages().itemLine("extend.total-line",
                    "%price%", Numbers.format(totalPrice),
                    "%symbol%", plugin.settings().symbol()));
            iconMeta.lore(lore);
            icon.setItemMeta(iconMeta);
        }
        inventory.setItem(ExtendHolder.SLOT_ICON, icon);

        inventory.setItem(ExtendHolder.SLOT_CONFIRM, button("GREEN_STAINED_GLASS_PANE",
                plugin.messages().itemLine("extend.confirm",
                        "%price%", Numbers.format(totalPrice),
                        "%symbol%", plugin.settings().symbol())));
        inventory.setItem(ExtendHolder.SLOT_CANCEL, button("BARRIER",
                plugin.messages().itemLine("shop.click-to-cancel")));
    }

    /** Clamps the selected steps to 0..max for the entry's extension type. */
    public int clampSteps(ShardCatalog.Entry entry, int steps) {
        if (!plugin.items().extendable(entry)) {
            return 0;
        }
        int max = entry.behavior() == Behavior.HASTE_POTION
                ? plugin.settings().extendPotionMaxSteps() : plugin.settings().extendToolMaxSteps();
        return Math.max(0, Math.min(steps, max));
    }

    // ---------------------------------------------------------------
    // Enchantment Picker (mcpvp.club style)
    // ---------------------------------------------------------------

    /**
     * Opens the full enchantment picker for an item with selectable
     * enchants. All selectable enchants start selected by default; the user
     * can toggle each on/off, and incompatible ones auto-deselect.
     */
    public void openEnchantPicker(Player player, ShardCatalog.Entry entry, int backPage) {
        EnchantPickerHolder holder = new EnchantPickerHolder(entry.id(), backPage);
        // All selectable enchants start selected by default
        for (int i = 0; i < entry.selectableEnchants().size(); i++) {
            holder.selected().add(i);
        }
        Component title = plugin.messages().bare("picker.title", "%item%", entry.displayName());
        Inventory inventory = Bukkit.createInventory(holder, 45, title);
        holder.inventory(inventory);
        renderEnchantPicker(holder, entry);
        player.openInventory(inventory);
    }

    /** (Re-)draws the enchantment picker after the selection changed. */
    public void renderEnchantPicker(EnchantPickerHolder holder, ShardCatalog.Entry entry) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        boolean extendable = plugin.items().extendable(entry);

        // Fill everything with border first
        ItemStack filler = borderItem();
        for (int slot = 0; slot < 45; slot++) {
            inventory.setItem(slot, filler);
        }

        // Render enchantment toggle slots
        List<EnchantSpec> selectable = entry.selectableEnchants();
        for (int i = 0; i < EnchantPickerHolder.ENCHANT_SLOTS.length && i < selectable.size(); i++) {
            int slot = EnchantPickerHolder.ENCHANT_SLOTS[i];
            EnchantSpec spec = selectable.get(i);
            boolean on = holder.isSelected(i);
            if (on) {
                inventory.setItem(slot, button("ENCHANTED_BOOK",
                        plugin.messages().itemLine("picker.enchant-on",
                                "%enchant%", prettyEnchant(spec)),
                        plugin.messages().itemLine("picker.click-toggle-off")));
            } else {
                inventory.setItem(slot, button("BOOK",
                        plugin.messages().itemLine("picker.enchant-off",
                                "%enchant%", prettyEnchant(spec)),
                        plugin.messages().itemLine("picker.click-toggle-on")));
            }
        }

        // Time extension controls (only if extendable)
        if (extendable) {
            boolean potion = entry.behavior() == Behavior.HASTE_POTION;
            long stepMinutes = potion
                    ? plugin.settings().extendPotionStepMinutes() : plugin.settings().extendToolStepMinutes();
            long stepPrice = potion
                    ? plugin.settings().extendPotionStepPrice() : plugin.settings().extendToolStepPrice();
            int maxSteps = potion
                    ? plugin.settings().extendPotionMaxSteps() : plugin.settings().extendToolMaxSteps();
            int steps = clampSteps(entry, holder.steps());
            holder.steps(steps);

            String stepTime = TimeWords.format(stepMinutes * 60_000L);
            String stepCost = Numbers.format(stepPrice);
            if (steps > 0) {
                inventory.setItem(EnchantPickerHolder.SLOT_MINUS, button("RED_STAINED_GLASS_PANE",
                        plugin.messages().itemLine("extend.minus",
                                "%time%", stepTime, "%price%", stepCost,
                                "%symbol%", plugin.settings().symbol())));
            }
            if (steps < maxSteps) {
                inventory.setItem(EnchantPickerHolder.SLOT_PLUS, button("LIME_STAINED_GLASS_PANE",
                        plugin.messages().itemLine("extend.plus",
                                "%time%", stepTime, "%price%", stepCost,
                                "%symbol%", plugin.settings().symbol())));
            }
        }

        // Preview icon in the center
        EnchantSpec chosen = null; // no old-style choice when using picker
        Long base = plugin.priceBook().price(entry.id());
        long basePrice = base == null ? 0L : base;
        long extraMs = 0L;
        long totalPrice = basePrice;
        if (extendable) {
            boolean potion = entry.behavior() == Behavior.HASTE_POTION;
            long stepMinutes = potion
                    ? plugin.settings().extendPotionStepMinutes() : plugin.settings().extendToolStepMinutes();
            long stepPrice = potion
                    ? plugin.settings().extendPotionStepPrice() : plugin.settings().extendToolStepPrice();
            extraMs = holder.steps() * stepMinutes * 60_000L;
            totalPrice = basePrice + holder.steps() * stepPrice;
        }
        long baseMs = entry.behavior() == Behavior.HASTE_POTION
                ? plugin.items().defaultEffectMs() : entry.lifetimeMs();

        ItemStack icon = extendable
                ? (entry.behavior() == Behavior.HASTE_POTION
                    ? plugin.items().previewIcon(entry, basePrice,
                            entry.lifetimeMs(), (baseMs + extraMs) / 60_000L, chosen, holder.selected())
                    : plugin.items().previewIcon(entry, basePrice,
                            baseMs + extraMs, -1L, chosen, holder.selected()))
                : plugin.items().previewIcon(entry, basePrice,
                        entry.lifetimeMs(), -1L, chosen, holder.selected());
        ItemMeta iconMeta = icon.getItemMeta();
        if (iconMeta != null) {
            List<Component> lore = iconMeta.lore() != null
                    ? new ArrayList<>(iconMeta.lore()) : new ArrayList<>();
            if (extendable) {
                boolean potion = entry.behavior() == Behavior.HASTE_POTION;
                lore.add(plugin.messages().itemLine(potion ? "extend.effect-line" : "extend.lifetime-line",
                        "%base%", TimeWords.format(baseMs),
                        "%extra%", TimeWords.format(extraMs),
                        "%total%", TimeWords.format(baseMs + extraMs)));
            }
            // Show selected enchant count
            lore.add(plugin.messages().itemLine("picker.selected-count",
                    "%count%", Integer.toString(holder.selected().size()),
                    "%total%", Integer.toString(selectable.size())));
            lore.add(plugin.messages().itemLine("extend.total-line",
                    "%price%", Numbers.format(totalPrice),
                    "%symbol%", plugin.settings().symbol()));
            iconMeta.lore(lore);
            icon.setItemMeta(iconMeta);
        }
        inventory.setItem(EnchantPickerHolder.SLOT_PREVIEW, icon);

        // Confirm / Cancel
        inventory.setItem(EnchantPickerHolder.SLOT_CONFIRM, button("LIME_STAINED_GLASS_PANE",
                plugin.messages().itemLine("extend.confirm",
                        "%price%", Numbers.format(totalPrice),
                        "%symbol%", plugin.settings().symbol())));
        inventory.setItem(EnchantPickerHolder.SLOT_CANCEL, button("BARRIER",
                plugin.messages().itemLine("shop.click-to-cancel")));
    }

    /**
     * Called when the user toggles an enchantment in the picker. Enforces
     * incompatibility rules: enabling an enchant deselects every conflicting
     * one; disabling just removes it.
     */
    public void handleEnchantToggle(EnchantPickerHolder holder, ShardCatalog.Entry entry, int enchantIndex) {
        List<EnchantSpec> selectable = entry.selectableEnchants();
        if (enchantIndex < 0 || enchantIndex >= selectable.size()) {
            return;
        }
        boolean wasOn = holder.isSelected(enchantIndex);
        holder.toggle(enchantIndex);
        if (!wasOn) {
            // Just enabled this enchant - deselect incompatible ones
            String justEnabled = selectable.get(enchantIndex).enchant();
            java.util.Iterator<Integer> it = holder.selected().iterator();
            while (it.hasNext()) {
                int idx = it.next();
                if (idx != enchantIndex && idx < selectable.size()) {
                    String other = selectable.get(idx).enchant();
                    if (IncompatibilityMap.incompatible(justEnabled, other)) {
                        it.remove();
                    }
                }
            }
        }
    }

    /** "fortune:3" -> "Fortune III", "silk_touch:1" -> "Silk Touch". */
    public static String prettyEnchant(EnchantSpec spec) {
        String[] parts = spec.enchant().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        if (spec.level() >= 2) {
            builder.append(' ').append(roman(spec.level()));
        }
        return builder.toString();
    }

    private static String roman(int level) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level <= numerals.length
                ? numerals[level - 1] : Integer.toString(level);
    }

    private ItemStack button(String materialName, Component name) {
        Material material = Material.matchMaterial(materialName);
        ItemStack stack = new ItemStack(material == null ? Material.AIR : material, 1);
        stack.editMeta(meta -> meta.displayName(name));
        return stack;
    }

    private ItemStack button(String materialName, Component name, Component loreLine) {
        ItemStack stack = button(materialName, name);
        stack.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            lore.add(loreLine);
            meta.lore(lore);
        });
        return stack;
    }

    private ItemStack infoItem(Player player, int page, int pages) {
        // DonutSMP-style purple amethyst shard icon showing the balance.
        ItemStack stack = new ItemStack(Material.matchMaterial("AMETHYST_SHARD"), 1);
        long balance = plugin.accounts().balance(player.getUniqueId(), player.getName());
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.messages().itemLine("shop.balance-lore",
                "%balance%", Numbers.format(balance),
                "%symbol%", plugin.settings().symbol()));
        lore.add(plugin.messages().itemLine("shop.page",
                "%page%", Integer.toString(page + 1),
                "%pages%", Integer.toString(pages)));
        stack.editMeta(meta -> {
            meta.displayName(plugin.messages().itemLine("shop.balance-item",
                    "%balance%", Numbers.format(balance),
                    "%symbol%", plugin.settings().symbol()));
            meta.lore(lore);
        });
        return stack;
    }

    private ItemStack navItem(String materialName, Component name, String navKey,
                              ShopHolder holder, int slot) {
        Material material = Material.matchMaterial(materialName);
        ItemStack stack = new ItemStack(material == null ? Material.AIR : material, 1);
        stack.editMeta(meta -> meta.displayName(name));
        if (holder != null && navKey != null && slot >= 0) {
            holder.slotItems().put(slot, navKey);
        }
        return stack;
    }

    private ItemStack borderItem() {
        Material material = Material.matchMaterial(plugin.settings().shopFiller());
        ItemStack stack = new ItemStack(material == null ? Material.AIR : material, 1);
        stack.editMeta(meta -> {
            meta.displayName(Component.empty());
            NamespacedKey hidden = new NamespacedKey(plugin, "gui-filler");
            meta.getPersistentDataContainer().set(hidden, PersistentDataType.BYTE, (byte) 1);
        });
        return stack;
    }
}
