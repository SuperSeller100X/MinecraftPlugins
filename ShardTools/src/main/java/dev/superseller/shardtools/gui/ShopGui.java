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

    /** Holder for the buy-with-extra-time dialog. */
    public static final class ExtendHolder implements InventoryHolder {

        /** Raw slots of the dialog's buttons. */
        public static final int SLOT_MINUS = 11;
        public static final int SLOT_ICON = 13;
        public static final int SLOT_PLUS = 15;
        public static final int SLOT_CONFIRM = 21;
        public static final int SLOT_CANCEL = 23;

        private Inventory inventory;
        private final String itemId;
        private final int backPage;
        private int steps;

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
    // Buy-with-extra-time dialog
    // ---------------------------------------------------------------

    /**
     * Opens the extra-time dialog for an extendable item: expiring shard
     * items buy extra lifetime, the haste potion buys a longer effect.
     */
    public void openExtend(Player player, ShardCatalog.Entry entry, int backPage, int steps) {
        ExtendHolder holder = new ExtendHolder(entry.id(), backPage);
        holder.steps(clampSteps(entry, steps));
        Component title = plugin.messages().bare("extend.title", "%item%", entry.displayName());
        Inventory inventory = Bukkit.createInventory(holder, 27, title);
        holder.inventory(inventory);
        renderExtend(holder, entry);
        player.openInventory(inventory);
    }

    /** (Re-)draws the extra-time dialog after the step count changed. */
    public void renderExtend(ExtendHolder holder, ShardCatalog.Entry entry) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        boolean potion = entry.behavior() == Behavior.HASTE_POTION;
        long stepMinutes = potion
                ? plugin.settings().extendPotionStepMinutes() : plugin.settings().extendToolStepMinutes();
        long stepPrice = potion
                ? plugin.settings().extendPotionStepPrice() : plugin.settings().extendToolStepPrice();
        int steps = clampSteps(entry, holder.steps());
        holder.steps(steps);

        ItemStack filler = borderItem();
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler);
        }
        String stepTime = TimeWords.format(stepMinutes * 60_000L);
        String stepCost = Numbers.format(stepPrice);
        inventory.setItem(ExtendHolder.SLOT_MINUS, button("RED_STAINED_GLASS_PANE",
                plugin.messages().itemLine("extend.minus",
                        "%time%", stepTime, "%price%", stepCost,
                        "%symbol%", plugin.settings().symbol())));
        inventory.setItem(ExtendHolder.SLOT_PLUS, button("LIME_STAINED_GLASS_PANE",
                plugin.messages().itemLine("extend.plus",
                        "%time%", stepTime, "%price%", stepCost,
                        "%symbol%", plugin.settings().symbol())));

        Long base = plugin.priceBook().price(entry.id());
        long basePrice = base == null ? 0L : base;
        long extraMs = steps * stepMinutes * 60_000L;
        long totalPrice = basePrice + steps * stepPrice;
        long baseMs = potion
                ? plugin.items().defaultEffectMs() : entry.lifetimeMs();

        ItemStack icon = plugin.items().displayIcon(entry, basePrice);
        ItemMeta iconMeta = icon.getItemMeta();
        if (iconMeta != null) {
            List<Component> lore = iconMeta.lore() != null
                    ? new ArrayList<>(iconMeta.lore()) : new ArrayList<>();
            lore.add(plugin.messages().itemLine(potion ? "extend.effect-line" : "extend.lifetime-line",
                    "%base%", TimeWords.format(baseMs),
                    "%extra%", TimeWords.format(extraMs),
                    "%total%", TimeWords.format(baseMs + extraMs)));
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
        int max = entry.behavior() == Behavior.HASTE_POTION
                ? plugin.settings().extendPotionMaxSteps() : plugin.settings().extendToolMaxSteps();
        return Math.max(0, Math.min(steps, max));
    }

    private ItemStack button(String materialName, Component name) {
        Material material = Material.matchMaterial(materialName);
        ItemStack stack = new ItemStack(material == null ? Material.AIR : material, 1);
        stack.editMeta(meta -> meta.displayName(name));
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
