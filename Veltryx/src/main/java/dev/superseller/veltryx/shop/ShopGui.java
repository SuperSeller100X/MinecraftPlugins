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
        ShopService.SaleQuote sellQuote;
        long buyUnit;
        boolean transitioning;
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
            inv.setItem(26, ShopItems.icon("COMPASS", "§bAdvanced Search", List.of(
                    "§7Search names or IDs; words can be in any order.",
                    "§7Try §fdiamond sword§7, §fcat:tools§7, or §f*§7.",
                    "§7Price filters: §fbuy<=50 §7and §fsell>=1§7.")));
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
                boolean choice = SurvivalItems.needsChoice(entry.material());
                inv.setItem(slot, ShopItems.icon(entry.material(), badge, "§b" + PriceTable.displayName(entry.material()),
                        List.of(choice ? "§7Prices depend on the variant" : "§7Buy: §a$§f" + Money.formatNumber(entry.buyCents()) + " §7each",
                                entry.sellCents() > 0 ? "§7Sell base: §a$§f" + Money.formatNumber(entry.sellCents()) : "§7Not sold to the shop",
                                "§eLeft-click to buy", "§6Right-click to sell")));
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
            if (!player.hasPermission("veltryx.shop")) { messages.send(player, "shop.no-permission"); return; }
            long unit = purchase.unitPrice(shop.prices());
            if (unit <= 0) { player.sendMessage(messages.render("shop.no-price", Map.of())); returnTo(player, categoryId, page); return; }
            int amount = Math.max(1, Math.min(64, requested));
            Holder h = new Holder("buy", categoryId, page, purchase, amount);
            h.buyUnit = unit;
            Inventory inv = Bukkit.createInventory(h, 45, Txt.legacy("§8Buying §a› §b" + purchase.label())); h.inventory = inv;
            ShopItems.fill(inv);
            inv.setItem(13, ShopItems.icon("PAPER", "§aClick to purchase",
                    List.of("§b" + shop.format(java.math.BigInteger.valueOf(unit).multiply(java.math.BigInteger.valueOf(amount))),
                            "§7Amount: " + amount)));
            inv.setItem(22, ShopItems.icon(purchase.material(), amount, "§b" + purchase.label(),
                    List.of("§7Selected: " + amount, "§7Each: §a$§f" + Money.formatCompact(unit))));
            inv.setItem(23, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 1, "§b+1", List.of("§7Right-click to subtract")));
            inv.setItem(24, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 16, "§b+16", List.of("§7Right-click to subtract")));
            inv.setItem(25, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 32, "§b+32", List.of("§7Right-click to subtract")));
            inv.setItem(31, ShopItems.icon("CHEST", "§bSelect 64", List.of("§7Review the total, then confirm.")));
            inv.setItem(36, ShopItems.icon("YELLOW_SHULKER_BOX", "§bSellGUI", List.of()));
            inv.setItem(44, ShopItems.icon("ARROW", "§bBack", List.of()));
            player.openInventory(inv);
        });
    }

    /** Preview a sale of the selected material/variant before removing anything. */
    public void openSelling(Player player, Purchase purchase, String categoryId, int page, int requested) {
        PlatformScheduler.runForPlayer(player, () -> {
            if (!player.hasPermission("veltryx.shop")) { messages.send(player, "shop.no-permission"); return; }
            if (!player.hasPermission("veltryx.sell")) { messages.send(player, "general.no-permission"); return; }
            if (purchase.unitPrice(shop.prices()) <= 0 || shop.prices().sellCents(purchase.material()) <= 0) {
                messages.send(player, "shop.no-sell"); returnTo(player, categoryId, page); return;
            }
            int amount = Math.max(1, Math.min(64, requested));
            Holder h = new Holder("sell", categoryId, page, purchase, amount);
            h.sellQuote = shop.quoteSell(player, purchase, amount);
            Inventory inv = Bukkit.createInventory(h, 45, Txt.legacy("§8Selling §a› §b" + purchase.label())); h.inventory = inv;
            ShopItems.fill(inv);
            List<org.bukkit.inventory.ItemStack> preview = h.sellQuote.previewItems();
            int[] previewSlots = {9, 10, 11, 12, 14, 15, 16, 17};
            for (int i = 0; i < Math.min(preview.size(), previewSlots.length); i++) inv.setItem(previewSlots[i], preview.get(i));
            List<String> quoteLore = new ArrayList<>();
            quoteLore.add("§7Matching items: §f" + h.sellQuote.result().sold());
            quoteLore.add("§7Payout: §a$§f" + Money.formatNumber(h.sellQuote.result().earnedCents()));
            quoteLore.add("§7Hover the shown stacks to inspect them.");
            if (preview.size() > previewSlots.length) quoteLore.add("§7And " + (preview.size() - previewSlots.length) + " more stacks.");
            quoteLore.add("§7Enchantments, variants and wear affect value.");
            inv.setItem(13, ShopItems.icon("PAPER", "§aConfirm sale", quoteLore));
            inv.setItem(22, ShopItems.icon(purchase.material(), amount, "§b" + purchase.label(),
                    List.of("§7Sell up to: §f" + amount, "§7Only matching inventory items are used.")));
            inv.setItem(23, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 1, "§b+1", List.of("§7Right-click to subtract")));
            inv.setItem(24, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 16, "§b+16", List.of("§7Right-click to subtract")));
            inv.setItem(25, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 32, "§b+32", List.of("§7Right-click to subtract")));
            inv.setItem(31, ShopItems.icon("CHEST", "§bSelect up to 64", List.of("§7Review the payout, then confirm.")));
            inv.setItem(36, ShopItems.icon("YELLOW_SHULKER_BOX", "§bSellGUI", List.of()));
            inv.setItem(44, ShopItems.icon("ARROW", "§bBack", List.of()));
            player.openInventory(inv);
        });
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory().getHolder() != h || h.transitioning) return;
        if (!player.hasPermission("veltryx.shop")) { messages.send(player, "shop.no-permission"); return; }
        org.bukkit.event.inventory.ClickType click = event.getClick();
        if (click != org.bukkit.event.inventory.ClickType.LEFT && click != org.bukkit.event.inventory.ClickType.RIGHT
                && click != org.bukkit.event.inventory.ClickType.SHIFT_LEFT && click != org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) return;
        int slot = event.getSlot();
        if (h.screen.equals("main")) {
            if (slot == 26 && extras != null) { h.transitioning = true; extras.openSearchPrompt(player); }
            else if (slot == 40 && extras != null) { h.transitioning = true; extras.openQuickshop(player); }
            else if (slot == 45 && sellOpener != null) {
                if (!player.hasPermission("veltryx.sell")) { messages.send(player, "general.no-permission"); return; }
                h.transitioning = true; openSell(player);
            } else if (slot == 53) { h.transitioning = true; PlatformScheduler.runForPlayer(player, player::closeInventory); }
            else if (h.categories.containsKey(slot)) { h.transitioning = true; openCategory(player, h.categories.get(slot), 0); }
        } else if (h.screen.equals("category")) {
            PriceTable.Entry entry = h.entries.get(slot);
            if (entry != null) {
                boolean selling = event.isRightClick();
                if (selling && !player.hasPermission("veltryx.sell")) { messages.send(player, "general.no-permission"); return; }
                if (SurvivalItems.needsChoice(entry.material())) {
                    if (extras == null) return;
                    h.transitioning = true; extras.openChoice(player, entry.material(), -1, h.category, h.page, selling);
                } else {
                    h.transitioning = true;
                    int start = h.category.equals("ores") && h.page == 0 && slot >= 19 && slot <= 25 ? 9 : 1;
                    if (selling) openSelling(player, Purchase.of(entry.material()), h.category, h.page, start);
                    else openBuying(player, Purchase.of(entry.material()), h.category, h.page, start);
                }
            } else if (slot == 38 && h.page > 0) { h.transitioning = true; openCategory(player, h.category, h.page - 1); }
            else if (slot == 40 || slot == 44) { h.transitioning = true; openMain(player); }
            else if (slot == 41) { h.transitioning = true; openCategory(player, h.category, h.page + 1); }
        } else if (h.screen.equals("buy") || h.screen.equals("sell")) {
            boolean selling = h.screen.equals("sell");
            if (selling && !player.hasPermission("veltryx.sell")) { messages.send(player, "general.no-permission"); return; }
            if (slot == 13) {
                h.transitioning = true;
                if (selling) sell(player, h);
                else {
                    buy(player, h.purchase, h.amount, h.buyUnit);
                    openBuying(player, h.purchase, h.category, h.page, h.amount);
                }
            } else if (slot == 31) {
                h.transitioning = true;
                if (selling) openSelling(player, h.purchase, h.category, h.page, 64);
                else openBuying(player, h.purchase, h.category, h.page, 64);
            } else if (slot >= 23 && slot <= 25) {
                h.transitioning = true;
                int step = slot == 23 ? 1 : slot == 24 ? 16 : 32;
                int amount = h.amount + (event.isRightClick() ? -step : step);
                if (selling) openSelling(player, h.purchase, h.category, h.page, amount);
                else openBuying(player, h.purchase, h.category, h.page, amount);
            } else if (slot == 36 && sellOpener != null) {
                if (!player.hasPermission("veltryx.sell")) { messages.send(player, "general.no-permission"); return; }
                h.transitioning = true; openSell(player);
            } else if (slot == 44) { h.transitioning = true; returnTo(player, h.category, h.page); }
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
    void buy(Player player, Purchase purchase, int amount, long expectedUnit) {
        if (!player.hasPermission("veltryx.shop")) { messages.send(player, "shop.no-permission"); return; }
        ShopService.BuyResult result = shop.buy(player, purchase, amount, expectedUnit);
        if (result.ok()) player.sendMessage(messages.render("shop.buy-done", Map.of(
                "amount", String.valueOf(result.given()), "item", purchase.label(),
                "price", Money.formatCompact(result.spentCents()), "balance", Money.formatCompact(shop.balance(player)))));
        else if ("shop.price-changed".equals(result.failKey())) messages.send(player, "shop.price-changed");
        else player.sendMessage(messages.render(result.failKey(), result.failValues()));
    }
    private void sell(Player player, Holder holder) {
        ShopService.SellResult result = shop.sell(player, holder.purchase, holder.amount, holder.sellQuote);
        if (result.ok()) {
            dev.superseller.veltryx.util.SellNotices.show(player, messages, "shop.sold", Map.of(
                    "count", String.valueOf(result.sold()), "item", holder.purchase.label(),
                    "balance", Money.formatCompact(shop.balance(player))), result.earnedCents());
        } else messages.send(player, result.failKey());
        openSelling(player, holder.purchase, holder.category, holder.page, holder.amount);
    }

    private static final String SEARCH_ORIGIN = "@search:";
    static String searchOrigin(String query) { return SEARCH_ORIGIN + query; }
    void returnTo(Player player, String category, int page) {
        if (category != null && category.startsWith(SEARCH_ORIGIN) && extras != null)
            extras.openSearch(player, category.substring(SEARCH_ORIGIN.length()), page);
        else if (category != null) openCategory(player, category, page);
        else openMain(player);
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
