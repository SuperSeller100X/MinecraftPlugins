package dev.superseller.veltryx.shop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;

/** Server Shop: screenshot-style category grid, pages and purchase screen. */
public final class ShopGui implements Listener {
    public static final class Holder implements InventoryHolder {
        final String screen, category;
        final int page, amount;
        final Purchase purchase;
        final Map<Integer, PriceTable.Entry> entries = new LinkedHashMap<>();
        final Map<Integer, String> categories = new LinkedHashMap<>();
        Inventory inventory;
        Holder(String screen, String category, int page, Purchase purchase, int amount) {
            this.screen = screen; this.category = category; this.page = page;
            this.purchase = purchase; this.amount = amount;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    /** Seven item columns by three rows, with the same gray border as the reference. */
    private static final int[] ITEM_SLOTS = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
    private static final Map<String, Integer> MAIN_SLOTS = Map.ofEntries(
            Map.entry("building", 10), Map.entry("nature", 12), Map.entry("ores", 14),
            Map.entry("wood", 16), Map.entry("food", 20), Map.entry("tools", 22),
            Map.entry("armor", 24), Map.entry("mobs", 28), Map.entry("colored", 30),
            Map.entry("redstone", 32), Map.entry("misc", 34));
    private static final int[] EXTRA_CATEGORY_SLOTS = {11,13,15,17,19,21,23,25,27,29,31,33,35,37,38,39,41,42,43};
    private static final List<String> RESOURCE_ORDER = List.of(
            "COPPER_BLOCK", "REDSTONE_BLOCK", "IRON_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK", "EMERALD_BLOCK", "NETHERITE_BLOCK",
            "COPPER_INGOT", "REDSTONE", "IRON_INGOT", "GOLD_INGOT", "DIAMOND", "EMERALD", "NETHERITE_INGOT",
            "COPPER_ORE", "REDSTONE_ORE", "IRON_ORE", "GOLD_ORE", "DIAMOND_ORE", "EMERALD_ORE", "DEEPSLATE_DIAMOND_ORE");
    private final ShopService shop;
    private final Messages messages;
    private ShopExtrasGui extras;
    private Consumer<Player> sellOpener;

    public ShopGui(ShopService shop, Messages messages) { this.shop = shop; this.messages = messages; }
    public void attachExtras(ShopExtrasGui extras) { this.extras = extras; }
    public void attachSellOpener(Consumer<Player> opener) { this.sellOpener = opener; }

    public void openMain(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            Holder h = new Holder("main", null, 0, null, 0);
            Inventory inv = Bukkit.createInventory(h, 54, Txt.legacy("§8Server Shop")); h.inventory = inv;
            ShopItems.fill(inv);
            for (PriceTable.Category category : shop.prices().categories()) {
                Integer slot = MAIN_SLOTS.get(category.id());
                if (slot == null || h.categories.containsKey(slot)) {
                    slot = null;
                    for (int possible : EXTRA_CATEGORY_SLOTS) if (!h.categories.containsKey(possible)) { slot = possible; break; }
                }
                if (slot == null) continue;
                h.categories.put(slot, category.id());
                String icon = displayIcon(category), display = displayCategory(category);
                inv.setItem(slot, ShopItems.icon(icon, "§b" + display,
                        List.of("§7" + category.items().size() + " items", "§eClick to browse")));
            }
            inv.setItem(26, ShopItems.icon("COMPASS", "§bSearch", List.of("§7Search every shop item.")));
            inv.setItem(40, ShopItems.icon("ENDER_CHEST", "§bQuickShop", List.of("§7Your 45 quick-buy slots.")));
            inv.setItem(45, ShopItems.icon("YELLOW_SHULKER_BOX", "§bSellGUI", List.of("§7Open the sell screen.")));
            inv.setItem(53, ShopItems.icon("ARROW", "§bClose", List.of()));
            player.openInventory(inv);
        });
    }

    public void openCategory(Player player, String categoryId, int page) {
        PlatformScheduler.runForPlayer(player, () -> {
            PriceTable.Category category = category(categoryId);
            if (category == null) { openMain(player); return; }
            List<PriceTable.Entry> items = ordered(category);
            int pages = Math.max(1, (items.size() + ITEM_SLOTS.length - 1) / ITEM_SLOTS.length);
            int current = Math.max(0, Math.min(page, pages - 1));
            Holder h = new Holder("category", categoryId, current, null, 0);
            Inventory inv = Bukkit.createInventory(h, 45,
                    Txt.legacy("§8‹----- §b" + displayCategory(category) + " §8-----›")); h.inventory = inv;
            ShopItems.fill(inv);
            for (int i = 0; i < ITEM_SLOTS.length; i++) {
                int index = current * ITEM_SLOTS.length + i;
                if (index >= items.size()) break;
                PriceTable.Entry entry = items.get(index);
                int slot = ITEM_SLOTS[i]; h.entries.put(slot, entry);
                int badge = categoryId.equals("ores") && current == 0 && i >= 7 && i <= 13 ? 9 : 1;
                inv.setItem(slot, ShopItems.icon(entry.material(), badge, "§b" + PriceTable.displayName(entry.material()),
                        List.of("§7Buy: §a$§f" + Money.formatCompact(entry.buyCents()) + " §7each",
                                entry.sellCents() > 0 ? "§7Sell: §a$§f" + Money.formatCompact(entry.sellCents()) : "§7Not sold to the shop",
                                "§eClick to choose")));
            }
            if (current > 0) inv.setItem(38, ShopItems.icon("BLUE_STAINED_GLASS_PANE", "§bPrevious Page", List.of()));
            inv.setItem(40, ShopItems.icon("NETHER_STAR", "§bBack to Server Shop", List.of()));
            if (current < pages - 1) inv.setItem(41, ShopItems.icon("BLUE_STAINED_GLASS_PANE", "§bNext Page", List.of()));
            inv.setItem(44, ShopItems.icon("ARROW", "§bBack", List.of()));
            player.openInventory(inv);
        });
    }

    /** Open the same 5-row buying layout for ordinary items and chosen variants. */
    public void openBuying(Player player, Purchase purchase, String categoryId, int page, int requested) {
        PlatformScheduler.runForPlayer(player, () -> {
            long unit = purchase.unitPrice(shop.prices());
            if (unit <= 0) { player.sendMessage(messages.render("shop.no-price", Map.of())); openMain(player); return; }
            int amount = Math.max(1, Math.min(64, requested));
            Holder h = new Holder("buy", categoryId, page, purchase, amount);
            Inventory inv = Bukkit.createInventory(h, 45, Txt.legacy("§8Buying §a› §b" + purchase.label())); h.inventory = inv;
            ShopItems.fill(inv);
            inv.setItem(13, ShopItems.icon("PAPER", "§aClick to purchase",
                    List.of("§b" + shop.format(Money.multiplySaturated(unit, amount)), "§7Amount: " + amount)));
            inv.setItem(22, ShopItems.icon(purchase.material(), amount, "§b" + purchase.label(),
                    List.of("§7Selected: " + amount, "§7Each: §a$§f" + Money.formatCompact(unit))));
            inv.setItem(23, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 1, "§b+1", List.of("§7Right-click to subtract")));
            inv.setItem(24, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 16, "§b+16", List.of("§7Right-click to subtract")));
            inv.setItem(25, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 32, "§b+32", List.of("§7Right-click to subtract")));
            inv.setItem(31, ShopItems.icon("CHEST", "§cClick to buy stacks", List.of("§7Buy up to 64.")));
            inv.setItem(36, ShopItems.icon("YELLOW_SHULKER_BOX", "§bSellGUI", List.of()));
            inv.setItem(44, ShopItems.icon("ARROW", "§bBack", List.of()));
            player.openInventory(inv);
        });
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory().getHolder() != h) return;
        int slot = event.getSlot();
        if (h.screen.equals("main")) {
            if (slot == 26 && extras != null) extras.openSearchPrompt(player);
            else if (slot == 40 && extras != null) extras.openQuickshop(player);
            else if (slot == 45 && sellOpener != null) openSell(player);
            else if (slot == 53) PlatformScheduler.runForPlayer(player, player::closeInventory);
            else if (h.categories.containsKey(slot)) openCategory(player, h.categories.get(slot), 0);
        } else if (h.screen.equals("category")) {
            PriceTable.Entry entry = h.entries.get(slot);
            if (entry != null) {
                if (SurvivalItems.needsChoice(entry.material()) && extras != null) {
                    extras.openChoice(player, entry.material(), -1, h.category, h.page);
                } else {
                    int start = h.category.equals("ores") && h.page == 0 && slot >= 19 && slot <= 25 ? 9 : 1;
                    openBuying(player, Purchase.of(entry.material()), h.category, h.page, start);
                }
            } else if (slot == 38 && h.page > 0) openCategory(player, h.category, h.page - 1);
            else if (slot == 40 || slot == 44) openMain(player);
            else if (slot == 41) openCategory(player, h.category, h.page + 1);
        } else {
            if (slot == 13) buy(player, h.purchase, h.amount);
            else if (slot == 31) buy(player, h.purchase, 64);
            else if (slot >= 23 && slot <= 25) {
                int step = slot == 23 ? 1 : slot == 24 ? 16 : 32;
                openBuying(player, h.purchase, h.category, h.page, h.amount + (event.isRightClick() ? -step : step));
            } else if (slot == 36 && sellOpener != null) openSell(player);
            else if (slot == 44) {
                if (h.category == null) openMain(player); else openCategory(player, h.category, h.page);
            }
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
    void buy(Player player, Purchase purchase, int amount) {
        ShopService.BuyResult result = shop.buy(player, purchase, amount);
        if (result.ok()) player.sendMessage(messages.render("shop.buy-done", Map.of(
                "amount", String.valueOf(result.given()), "item", purchase.label(),
                "price", Money.formatCompact(result.spentCents()), "balance", Money.formatCompact(shop.balance(player)))));
        else player.sendMessage(messages.render(result.failKey(), result.failValues()));
    }
    private void openSell(Player player) {
        if (player.hasPermission("veltryx.sell")) sellOpener.accept(player);
        else messages.send(player, "general.no-permission", Map.of());
    }
    private PriceTable.Category category(String id) {
        for (PriceTable.Category category : shop.prices().categories()) if (category.id().equals(id)) return category;
        return null;
    }
    private static String displayCategory(PriceTable.Category c) {
        return c.id().equals("ores") && c.display().equals("Ores & Minerals") ? "Resources" : c.display();
    }
    private static String displayIcon(PriceTable.Category c) {
        // Upgrades preserve user values; recognize only unchanged old defaults.
        return switch (c.id() + ":" + c.icon()) {
            case "ores:DIAMOND" -> "IRON_INGOT";
            case "building:BRICKS" -> "CRAFTING_TABLE";
            case "nature:OAK_SAPLING" -> "GRASS_BLOCK";
            case "tools:DIAMOND_PICKAXE" -> "DIAMOND_SWORD";
            case "food:GOLDEN_APPLE" -> "WHEAT";
            case "mobs:ENDER_PEARL" -> "GUNPOWDER";
            case "colored:RED_WOOL" -> "LAPIS_BLOCK";
            case "redstone:PISTON" -> "NOTE_BLOCK";
            case "misc:ENDER_CHEST" -> "CHEST";
            default -> c.icon();
        };
    }
    private static List<PriceTable.Entry> ordered(PriceTable.Category category) {
        List<PriceTable.Entry> items = new ArrayList<>(category.items());
        if (category.id().equals("ores")) {
            items.sort(Comparator.comparingInt(e -> {
                int i = RESOURCE_ORDER.indexOf(e.material());
                return i < 0 ? Integer.MAX_VALUE : i;
            }));
        }
        return items;
    }
}
