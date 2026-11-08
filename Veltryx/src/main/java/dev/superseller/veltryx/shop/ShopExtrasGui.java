package dev.superseller.veltryx.shop;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;

/** Search, configurable QuickShop slots, and potion/book variant pickers. */
public final class ShopExtrasGui implements Listener {
    private abstract static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class QuickHolder extends Holder { }
    private static final class SearchHolder extends Holder {
        final String query; final int page, quickSlot; final List<PriceTable.Entry> results;
        SearchHolder(String query, int page, int quickSlot, List<PriceTable.Entry> results) {
            this.query = query; this.page = page; this.quickSlot = quickSlot; this.results = results;
        }
    }
    private static final class EffectHolder extends Holder {
        final String material, category; final int quickSlot, page;
        final List<ChoiceCatalog.PotionOption> options = ChoiceCatalog.potions();
        EffectHolder(String material, int quickSlot, String category, int page) {
            this.material = material; this.quickSlot = quickSlot; this.category = category; this.page = page;
        }
    }
    private static final class BookHolder extends Holder {
        final int quickSlot, page; final String category;
        final List<ChoiceCatalog.BookOption> options = ChoiceCatalog.books();
        BookHolder(int quickSlot, String category, int page) { this.quickSlot = quickSlot; this.category = category; this.page = page; }
    }
    private static final class LevelHolder extends Holder {
        final int quickSlot, page; final String category; final ChoiceCatalog.BookOption option;
        LevelHolder(int quickSlot, String category, int page, ChoiceCatalog.BookOption option) {
            this.quickSlot = quickSlot; this.category = category; this.page = page; this.option = option;
        }
    }
    private static final class AmountHolder extends Holder {
        final Purchase purchase; final int quickSlot, amount;
        AmountHolder(Purchase purchase, int quickSlot, int amount) { this.purchase = purchase; this.quickSlot = quickSlot; this.amount = amount; }
    }
    private record PendingSearch(int quickSlot) { }
    private final Map<UUID, PendingSearch> pending = new ConcurrentHashMap<>();
    private final ShopService shop;
    private final Messages messages;
    private final QuickShopStore slots;
    private ShopGui gui;
    private java.util.function.Consumer<Player> cancelOtherPrompt = player -> { };

    public ShopExtrasGui(ShopService shop, Messages messages, Path slotsFile, Logger logger) {
        this.shop = shop; this.messages = messages;
        this.slots = new QuickShopStore(slotsFile, logger); this.slots.load();
    }
    public void attachShopGui(ShopGui gui) { this.gui = gui; }
    public void attachPromptCancellation(java.util.function.Consumer<Player> cancel) { this.cancelOtherPrompt = cancel; }
    public void cancelPendingSearch(Player player) {
        if (pending.remove(player.getUniqueId()) != null) messages.send(player, "shop.search-cancelled", Map.of());
    }
    public void save() { slots.save(); }

    /** All 45 slots are individually configurable. */
    public void openQuickshop(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            QuickHolder h = new QuickHolder();
            Inventory inv = Bukkit.createInventory(h, 45, Txt.legacy("§8QuickShop")); h.inventory = inv;
            for (int slot = 0; slot < QuickShopStore.SLOTS; slot++) {
                QuickShopStore.QuickSlot saved = slots.get(player.getUniqueId(), slot);
                if (saved == null || saved.purchase().unitPrice(shop.prices()) <= 0) {
                    inv.setItem(slot, ShopItems.icon("GRAY_STAINED_GLASS_PANE", "§bSet QuickShop item",
                            List.of("§7Click to search and choose an amount.")));
                } else {
                    Purchase purchase = saved.purchase();
                    long price = purchase.unitPrice(shop.prices());
                    inv.setItem(slot, ShopItems.icon(purchase.material(), saved.amount(), "§b" + purchase.label(),
                            List.of("§7Amount: §f" + saved.amount(), "§7Price: §a$§f" + Money.formatCompact(Money.multiplySaturated(price, saved.amount())),
                                    "§eClick to buy", "§7Right-click to change", "§7Shift-right-click to clear")));
                }
            }
            player.openInventory(inv);
        });
    }
    public void openSearchPrompt(Player player) { prompt(player, -1); }
    private void prompt(Player player, int quickSlot) {
        PlatformScheduler.runForPlayer(player, () -> {
            cancelOtherPrompt.accept(player);
            pending.put(player.getUniqueId(), new PendingSearch(quickSlot));
            player.closeInventory();
            messages.send(player, quickSlot < 0 ? "shop.search-prompt" : "shop.quick-search-prompt", Map.of());
        });
    }
    public void openSearch(Player player, String query, int page) { openSearch(player, query, page, -1); }
    private void openSearch(Player player, String query, int page, int quickSlot) {
        PlatformScheduler.runForPlayer(player, () -> {
            List<PriceTable.Entry> found = search(query);
            int pages = Math.max(1, (found.size() + 44) / 45);
            int current = Math.max(0, Math.min(page, pages - 1));
            SearchHolder h = new SearchHolder(query, current, quickSlot, found);
            Inventory inv = Bukkit.createInventory(h, 54, Txt.legacy("§8Search: §b" + query + " §7(" + (current + 1) + "/" + pages + ")"));
            h.inventory = inv; ShopItems.fill(inv);
            for (int i = 0; i < 45; i++) {
                int index = current * 45 + i; if (index >= found.size()) break;
                PriceTable.Entry entry = found.get(index);
                inv.setItem(i, ShopItems.icon(entry.material(), "§b" + PriceTable.displayName(entry.material()),
                        List.of("§7Shop price: §a$§f" + Money.formatCompact(entry.buyCents()),
                                quickSlot < 0 ? "§eClick to choose" : "§eClick to set this slot")));
            }
            if (current > 0) inv.setItem(45, ShopItems.icon("BLUE_STAINED_GLASS_PANE", "§bPrevious Page", List.of()));
            inv.setItem(49, ShopItems.icon("NETHER_STAR", "§bBack", List.of()));
            if (current < pages - 1) inv.setItem(53, ShopItems.icon("BLUE_STAINED_GLASS_PANE", "§bNext Page", List.of()));
            player.openInventory(inv);
        });
    }
    List<PriceTable.Entry> search(String query) {
        List<PriceTable.Entry> out = new ArrayList<>();
        for (PriceTable.Category category : shop.prices().categories()) {
            for (PriceTable.Entry entry : category.items()) {
                if ("*".equals(query.trim()) || ChoiceCatalog.matches(query, PriceTable.displayName(entry.material()), entry.material())) out.add(entry);
            }
        }
        return out;
    }
    /** Consume the search text privately; it must never be broadcast. */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        PendingSearch search = pending.remove(player.getUniqueId());
        if (search == null) return;
        event.setCancelled(true);
        String query = event.getMessage().trim();
        if (query.equalsIgnoreCase("cancel")) {
            PlatformScheduler.runForPlayer(player, () -> {
                messages.send(player, "shop.search-cancelled", Map.of());
                if (search.quickSlot() >= 0) openQuickshop(player); else if (gui != null) gui.openMain(player);
            });
        } else openSearch(player, query, 0, search.quickSlot());
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { pending.remove(e.getPlayer().getUniqueId()); }

    /** Called by category/search when an item requires a potion or enchantment choice. */
    public void openChoice(Player player, String material, int quickSlot, String category, int page) {
        if (material.equals("ENCHANTED_BOOK")) openBooks(player, quickSlot, category, page);
        else openEffects(player, material, quickSlot, category, page);
    }
    private void openEffects(Player player, String material, int quickSlot, String category, int page) {
        PlatformScheduler.runForPlayer(player, () -> {
            EffectHolder h = new EffectHolder(material, quickSlot, category, page);
            Inventory inv = Bukkit.createInventory(h, 54, Txt.legacy("§8Choose " + PriceTable.displayName(material))); h.inventory = inv;
            ShopItems.fill(inv);
            for (int i = 0; i < h.options.size() && i < 45; i++) {
                ChoiceCatalog.PotionOption option = h.options.get(i);
                Purchase purchase = new Purchase(material, option.key(), 0);
                inv.setItem(i, ShopItems.icon(material, "§b" + option.displayName(),
                        List.of("§7Each: §a$§f" + Money.formatCompact(purchase.unitPrice(shop.prices())), "§eClick to select")));
            }
            inv.setItem(49, ShopItems.icon("NETHER_STAR", "§bBack", List.of()));
            player.openInventory(inv);
        });
    }
    private void openBooks(Player player, int quickSlot, String category, int page) {
        PlatformScheduler.runForPlayer(player, () -> {
            BookHolder h = new BookHolder(quickSlot, category, page);
            Inventory inv = Bukkit.createInventory(h, 54, Txt.legacy("§8Choose Enchantment")); h.inventory = inv; ShopItems.fill(inv);
            for (int i = 0; i < h.options.size() && i < 45; i++) {
                ChoiceCatalog.BookOption option = h.options.get(i);
                inv.setItem(i, ShopItems.icon("ENCHANTED_BOOK", "§b" + option.displayName(),
                        List.of("§7Maximum level: " + option.maxLevel(), "§eClick to choose a level")));
            }
            inv.setItem(49, ShopItems.icon("NETHER_STAR", "§bBack", List.of())); player.openInventory(inv);
        });
    }
    private void openLevels(Player player, BookHolder parent, ChoiceCatalog.BookOption option) {
        PlatformScheduler.runForPlayer(player, () -> {
            LevelHolder h = new LevelHolder(parent.quickSlot, parent.category, parent.page, option);
            Inventory inv = Bukkit.createInventory(h, 27, Txt.legacy("§8" + option.displayName() + " — Level")); h.inventory = inv; ShopItems.fill(inv);
            for (int level = 1; level <= option.maxLevel(); level++) {
                Purchase purchase = new Purchase("ENCHANTED_BOOK", option.key(), level);
                inv.setItem(9 + level, ShopItems.icon("ENCHANTED_BOOK", level, "§b" + option.displayName() + " " + level,
                        List.of("§7Each: §a$§f" + Money.formatCompact(purchase.unitPrice(shop.prices())), "§eClick to select")));
            }
            inv.setItem(22, ShopItems.icon("NETHER_STAR", "§bBack", List.of())); player.openInventory(inv);
        });
    }
    private void selected(Player player, Purchase purchase, int quickSlot, String category, int page) {
        if (quickSlot >= 0) openAmount(player, purchase, quickSlot, 1);
        else if (gui != null) gui.openBuying(player, purchase, category, page, 1);
    }
    private void openAmount(Player player, Purchase purchase, int quickSlot, int requested) {
        PlatformScheduler.runForPlayer(player, () -> {
            int amount = Math.max(1, Math.min(64, requested));
            AmountHolder h = new AmountHolder(purchase, quickSlot, amount);
            Inventory inv = Bukkit.createInventory(h, 27, Txt.legacy("§8QuickShop Amount")); h.inventory = inv; ShopItems.fill(inv);
            inv.setItem(9, ShopItems.icon("RED_STAINED_GLASS_PANE", 32, "§c-32", List.of()));
            inv.setItem(10, ShopItems.icon("RED_STAINED_GLASS_PANE", 16, "§c-16", List.of()));
            inv.setItem(11, ShopItems.icon("RED_STAINED_GLASS_PANE", 1, "§c-1", List.of()));
            inv.setItem(13, ShopItems.icon(purchase.material(), amount, "§b" + purchase.label(),
                    List.of("§7Amount: §f" + amount, "§7Price: §a$§f" + Money.formatCompact(Money.multiplySaturated(purchase.unitPrice(shop.prices()), amount)))));
            inv.setItem(15, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 1, "§b+1", List.of()));
            inv.setItem(16, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 16, "§b+16", List.of()));
            inv.setItem(17, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 32, "§b+32", List.of()));
            inv.setItem(18, ShopItems.icon("ARROW", "§bBack", List.of()));
            inv.setItem(22, ShopItems.icon("LIME_STAINED_GLASS_PANE", "§aSave QuickShop slot", List.of("§7Click the saved item later to buy.")));
            player.openInventory(inv);
        });
    }
    private void back(Player player, int quickSlot, String category, int page) {
        if (quickSlot >= 0) openQuickshop(player);
        else if (gui != null && category != null) gui.openCategory(player, category, page);
        else if (gui != null) gui.openMain(player);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory().getHolder() != h) return;
        int slot = event.getSlot();
        if (h instanceof QuickHolder) {
            if (slot < 0 || slot >= QuickShopStore.SLOTS) return;
            QuickShopStore.QuickSlot saved = slots.get(player.getUniqueId(), slot);
            if (saved != null && event.isRightClick() && event.isShiftClick()) { slots.set(player.getUniqueId(), slot, null); openQuickshop(player); }
            else if (saved == null || saved.purchase().unitPrice(shop.prices()) <= 0) prompt(player, slot);
            else if (event.isRightClick() || event.isShiftClick()) prompt(player, slot);
            else if (gui != null) gui.buy(player, saved.purchase(), saved.amount());
        } else if (h instanceof SearchHolder search) {
            if (slot < 45) {
                int index = search.page * 45 + slot;
                if (index >= search.results.size()) return;
                PriceTable.Entry entry = search.results.get(index);
                if (SurvivalItems.needsChoice(entry.material())) openChoice(player, entry.material(), search.quickSlot, null, 0);
                else selected(player, Purchase.of(entry.material()), search.quickSlot, null, 0);
            } else if (slot == 45 && search.page > 0) openSearch(player, search.query, search.page - 1, search.quickSlot);
            else if (slot == 53) openSearch(player, search.query, search.page + 1, search.quickSlot);
            else if (slot == 49) back(player, search.quickSlot, null, 0);
        } else if (h instanceof EffectHolder effects) {
            if (slot == 49) back(player, effects.quickSlot, effects.category, effects.page);
            else if (slot >= 0 && slot < effects.options.size() && slot < 45)
                selected(player, new Purchase(effects.material, effects.options.get(slot).key(), 0), effects.quickSlot, effects.category, effects.page);
        } else if (h instanceof BookHolder books) {
            if (slot == 49) back(player, books.quickSlot, books.category, books.page);
            else if (slot >= 0 && slot < books.options.size() && slot < 45) openLevels(player, books, books.options.get(slot));
        } else if (h instanceof LevelHolder levels) {
            if (slot == 22) openBooks(player, levels.quickSlot, levels.category, levels.page);
            else if (slot >= 10 && slot <= 9 + levels.option.maxLevel())
                selected(player, new Purchase("ENCHANTED_BOOK", levels.option.key(), slot - 9), levels.quickSlot, levels.category, levels.page);
        } else if (h instanceof AmountHolder amount) {
            int change = switch (slot) { case 9 -> -32; case 10 -> -16; case 11 -> -1; case 15 -> 1; case 16 -> 16; case 17 -> 32; default -> 0; };
            if (change != 0) openAmount(player, amount.purchase, amount.quickSlot, amount.amount + change);
            else if (slot == 18) openQuickshop(player);
            else if (slot == 22) {
                if (amount.purchase.unitPrice(shop.prices()) <= 0) { messages.send(player, "shop.no-price", Map.of()); return; }
                slots.set(player.getUniqueId(), amount.quickSlot, new QuickShopStore.QuickSlot(amount.purchase, amount.amount));
                openQuickshop(player);
            }
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
    static org.bukkit.potion.PotionType resolvePotion(String key) {
        if ("SPEED".equals(key)) key = "SWIFTNESS";
        if ("JUMP_BOOST".equals(key)) key = "LEAPING";
        try { return org.bukkit.potion.PotionType.valueOf(key); }
        catch (IllegalArgumentException e) { return null; }
    }
}
