package dev.superseller.veltryx.shop;

import dev.superseller.veltryx.util.Txt;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The {@code /shop} GUI: a category menu and paged item lists. Left-click
 * buys 1, shift-click buys a stack. Prices and the player's balance are shown
 * on every item.
 */
public final class ShopGui implements Listener {

    /** Marks shop inventories and remembers where we are. */
    public static final class Holder implements InventoryHolder {
        private final String categoryId;
        private final int page;
        private Inventory inventory;

        Holder(String categoryId, int page) {
            this.categoryId = categoryId;
            this.page = page;
        }

        public String categoryId() {
            return categoryId;
        }

        public int page() {
            return page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int PAGE_SIZE = 45;

    private final ShopService shop;
    private final Messages messages;

    public ShopGui(ShopService shop, Messages messages) {
        this.shop = shop;
        this.messages = messages;
    }

    /** Opens the category menu (Folia-safe). */
    public void openMain(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            List<PriceTable.Category> categories = shop.prices().categories();
            int rows = Math.max(1, Math.min(6, (categories.size() + 8) / 9));
            Holder holder = new Holder(null, 0);
            Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                    Txt.legacy("§8§lShop — categories"));
            holder.inventory = inventory;
            for (int i = 0; i < categories.size() && i < rows * 9; i++) {
                PriceTable.Category category = categories.get(i);
                Material icon = Material.matchMaterial(category.icon());
                inventory.setItem(i, item(icon == null ? Material.CHEST : icon,
                        Txt.legacy("§f" + category.display()),
                        List.of(Txt.legacy("§7" + category.items().size() + " items"),
                                Component.text(""),
                                Txt.legacy("§eClick to browse"))));
            }
            player.openInventory(inventory);
        });
    }

    /** Opens one category's item page (Folia-safe). */
    public void openCategory(Player player, String categoryId, int page) {
        PlatformScheduler.runForPlayer(player, () -> {
            PriceTable.Category category = null;
            for (PriceTable.Category candidate : shop.prices().categories()) {
                if (candidate.id().equals(categoryId)) {
                    category = candidate;
                    break;
                }
            }
            if (category == null) {
                openMain(player);
                return;
            }
            int pages = Math.max(1, (category.items().size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int current = Math.max(0, Math.min(page, pages - 1));
            Holder holder = new Holder(categoryId, current);
            Inventory inventory = Bukkit.createInventory(holder, 54,
                    Txt.legacy("§8§lShop — " + category.display() + " §7(" + (current + 1) + "/" + pages + ")"));
            holder.inventory = inventory;

            List<PriceTable.Entry> items = category.items();
            for (int slot = 0; slot < PAGE_SIZE; slot++) {
                int index = current * PAGE_SIZE + slot;
                if (index >= items.size()) {
                    break;
                }
                PriceTable.Entry entry = items.get(index);
                Material material = Material.matchMaterial(entry.material());
                inventory.setItem(slot, item(material == null ? Material.PAPER : material,
                        Txt.legacy("§f" + PriceTable.displayName(entry.material())),
                        List.of(Txt.legacy("§7Buy: §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(entry.buyCents())
                                        + " §8×1 §7| §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(entry.buyCents() * 64)
                                        + " §8×64"),
                                Txt.legacy("§7Sell: §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(entry.sellCents())),
                                Component.text(""),
                                Txt.legacy("§eLeft-click§7: buy 1"),
                                Txt.legacy("§eShift-click§7: buy 64"))));
            }
            if (current > 0) {
                inventory.setItem(45, item(Material.ARROW, Txt.legacy("§ePrevious page"), List.of()));
            }
            inventory.setItem(49, item(Material.BARRIER, Txt.legacy("§cBack to categories"), List.of()));
            inventory.setItem(51, item(Material.GOLD_INGOT,
                    Txt.legacy("§6Your balance: §a$§f"
                            + dev.superseller.veltryx.util.Money.formatCompact(shop.balance(player))), List.of()));
            if (current < pages - 1) {
                inventory.setItem(53, item(Material.ARROW, Txt.legacy("§eNext page"), List.of()));
            }
            player.openInventory(inventory);
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) {
            return;
        }
        int slot = event.getSlot();
        if (holder.categoryId() == null) {
            List<PriceTable.Category> categories = shop.prices().categories();
            if (slot >= 0 && slot < categories.size()) {
                openCategory(player, categories.get(slot).id(), 0);
            }
            return;
        }
        if (slot < PAGE_SIZE) {
            List<PriceTable.Entry> items = categoryItems(holder.categoryId());
            int index = holder.page() * PAGE_SIZE + slot;
            if (items != null && index < items.size()) {
                int amount = event.isShiftClick() ? 64 : 1;
                buy(player, items.get(index), amount);
            }
            return;
        }
        if (slot == 45) {
            openCategory(player, holder.categoryId(), holder.page() - 1);
        } else if (slot == 49) {
            openMain(player);
        } else if (slot == 53) {
            openCategory(player, holder.categoryId(), holder.page() + 1);
        }
    }

    private void buy(Player player, PriceTable.Entry entry, int amount) {
        ShopService.BuyResult result = shop.buy(player, entry.material(), amount);
        if (result.ok()) {
player.sendMessage(messages.render("shop.buy-done", Map.of(
                    "amount", String.valueOf(result.given()),
                    "item", entry.material(),
                    "price", dev.superseller.veltryx.util.Money.formatCompact(result.spentCents()),
                    "balance", dev.superseller.veltryx.util.Money.formatCompact(shop.balance(player)))));
        } else {
            sendFail(player, result.failKey(), result.failValues());
        }
    }

    /** Renders a buy failure — explicit so every message key stays visible. */
    private void sendFail(Player player, String key, Map<String, String> values) {
        switch (key) {
            case "shop.cannot-afford" -> player.sendMessage(messages.render("shop.cannot-afford", values));
            case "shop.no-price" -> player.sendMessage(messages.render("shop.no-price", values));
            case "shop.no-space" -> player.sendMessage(messages.render("shop.no-space", values));
            default -> player.sendMessage(messages.render("shop.no-price", values));
        }
    }

    private List<PriceTable.Entry> categoryItems(String categoryId) {
        for (PriceTable.Category category : shop.prices().categories()) {
            if (category.id().equals(categoryId)) {
                return category.items();
            }
        }
        return null;
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(new ArrayList<>(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
