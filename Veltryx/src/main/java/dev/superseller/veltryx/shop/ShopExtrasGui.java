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
        boolean transitioning;
        @Override public Inventory getInventory() { return inventory; }
    }
    private record QuickQuote(QuickShopStore.QuickSlot slot, long unitPrice) { }
    private static final class QuickHolder extends Holder {
        final Map<Integer, QuickQuote> quotes = new java.util.HashMap<>();
    }
    private static final class SearchHolder extends Holder {
        final String query; final int page, quickSlot; final List<PriceTable.Entry> results;
        SearchHolder(String query, int page, int quickSlot, List<PriceTable.Entry> results) {
            this.query = query; this.page = page; this.quickSlot = quickSlot; this.results = results;
        }
    }
    private static final class EffectHolder extends Holder {
        final String material, category; final int quickSlot, page; final boolean selling;
        final List<ChoiceCatalog.PotionOption> options = ChoiceCatalog.potions();
        EffectHolder(String material, int quickSlot, String category, int page, boolean selling) {
            this.material = material; this.quickSlot = quickSlot; this.category = category; this.page = page; this.selling = selling;
        }
    }
    private static final class BookHolder extends Holder {
        final int quickSlot, page; final String category; final boolean selling;
        final List<ChoiceCatalog.BookOption> options = ChoiceCatalog.books();
        BookHolder(int quickSlot, String category, int page, boolean selling) {
            this.quickSlot = quickSlot; this.category = category; this.page = page; this.selling = selling;
        }
    }
    private static final class LevelHolder extends Holder {
        final int quickSlot, page; final String category; final ChoiceCatalog.BookOption option; final boolean selling;
        LevelHolder(int quickSlot, String category, int page, ChoiceCatalog.BookOption option, boolean selling) {
            this.quickSlot = quickSlot; this.category = category; this.page = page; this.option = option; this.selling = selling;
        }
    }
    private static final class AmountHolder extends Holder {
        final Purchase purchase; final int quickSlot, amount;
        AmountHolder(Purchase purchase, int quickSlot, int amount) { this.purchase = purchase; this.quickSlot = quickSlot; this.amount = amount; }
    }
    private record PendingSearch(int quickSlot, org.bukkit.Location location, org.bukkit.block.data.BlockData original) { }
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
        PendingSearch old = pending.remove(player.getUniqueId());
        if (old != null) { restoreSign(player, old); messages.send(player, "shop.search-cancelled", Map.of()); }
    }
    public void save() { slots.save(); }

    /** All 45 slots are individually configurable. */
    public void openQuickshop(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            QuickHolder h = new QuickHolder();
            Inventory inv = Bukkit.createInventory(h, 45, Txt.legacy("§8QuickShop")); h.inventory = inv;
            PriceTable table = shop.prices();
            for (int slot = 0; slot < QuickShopStore.SLOTS; slot++) {
                QuickShopStore.QuickSlot saved = slots.get(player.getUniqueId(), slot);
                long price = saved == null ? -1 : saved.purchase().unitPrice(table);
                if (price <= 0) {
                    inv.setItem(slot, ShopItems.icon("GRAY_STAINED_GLASS_PANE", "§bSet QuickShop item",
                            List.of("§7Click to search and choose an amount.")));
                } else {
                    Purchase purchase = saved.purchase();
                    h.quotes.put(slot, new QuickQuote(saved, price));
                    inv.setItem(slot, ShopItems.icon(purchase.material(), saved.amount(), "§b" + purchase.label(),
                            List.of("§7Amount: §f" + saved.amount(), "§7Price: §a$§f" + totalPrice(price, saved.amount()),
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
            cancelPendingSearch(player);
            player.closeInventory();
            org.bukkit.Location at = player.getLocation();
            org.bukkit.Location location = new org.bukkit.Location(player.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
            org.bukkit.block.data.BlockData original = null;
            try {
                original = location.getBlock().getBlockData();
                PendingSearch session = new PendingSearch(quickSlot, location, original);
                pending.put(player.getUniqueId(), session);
                player.sendBlockChange(location, org.bukkit.Material.OAK_SIGN.createBlockData());
                player.sendSignChange(location, new String[]{"", "", "", ""});
                player.openVirtualSign(io.papermc.paper.math.Position.block(at.getBlockX(), at.getBlockY(), at.getBlockZ()), org.bukkit.block.sign.Side.FRONT);
            } catch (RuntimeException ex) {
                pending.remove(player.getUniqueId());
                if (original != null) player.sendBlockChange(location, original);
                messages.send(player, "shop.search-unavailable", Map.of());
                if (quickSlot >= 0) openQuickshop(player); else if (gui != null) gui.openMain(player);
            }
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
                List<String> lore = new ArrayList<>();
                lore.add("§7Category: §f" + PriceTable.displayName(entry.category()));
                if (SurvivalItems.needsChoice(entry.material())) lore.add("§7Prices depend on the variant");
                else {
                    lore.add("§7Buy: §a$§f" + Money.formatNumber(entry.buyCents()));
                    lore.add(entry.sellCents() > 0 ? "§7Sell: §a$§f" + Money.formatNumber(entry.sellCents()) : "§7Not sold to the shop");
                }
                if (quickSlot < 0) { lore.add("§eLeft-click to buy"); lore.add("§6Right-click to sell"); }
                else lore.add("§eClick to set this slot");
                inv.setItem(i, ShopItems.icon(entry.material(), "§b" + PriceTable.displayName(entry.material()), lore));
            }
            if (found.isEmpty()) inv.setItem(22, ShopItems.icon("BARRIER", "§cNo matching items",
                    List.of("§7Try fewer words, another category, or * to browse all.")));
            if (current > 0) inv.setItem(45, ShopItems.icon("BLUE_STAINED_GLASS_PANE", "§bPrevious Page", List.of()));
            inv.setItem(48, ShopItems.icon("PAPER", "§bSearch help", List.of(
                    "§7Words may be in any order; underscores work too.",
                    "§7Use §fcat:tools§7, §fbuy<=50§7, §fsell>=1§7, or §f*§7.",
                    "§7Price filters use base prices; variants can differ.")));
            inv.setItem(49, ShopItems.icon("NETHER_STAR", "§bBack", List.of()));
            inv.setItem(50, ShopItems.icon("COMPASS", "§bNew search", List.of("§eClick to edit your search.")));
            if (current < pages - 1) inv.setItem(53, ShopItems.icon("BLUE_STAINED_GLASS_PANE", "§bNext Page", List.of()));
            player.openInventory(inv);
        });
    }
    List<PriceTable.Entry> search(String query) {
        return ShopSearch.find(shop.prices(), query);
    }
    private void restoreSign(Player player, PendingSearch session) {
        if (session.location() != null && session.original() != null && player.getWorld().equals(session.location().getWorld()))
            player.sendBlockChange(session.location(), session.original());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSign(io.papermc.paper.event.packet.UncheckedSignChangeEvent event) {
        Player player = event.getPlayer();
        PendingSearch session = pending.get(player.getUniqueId());
        var pos = event.getEditedBlockPosition();
        if (session == null || session.location() == null || !player.getWorld().equals(session.location().getWorld())
                || event.getSide() != org.bukkit.block.sign.Side.FRONT
                || pos.blockX() != session.location().getBlockX() || pos.blockY() != session.location().getBlockY()
                || pos.blockZ() != session.location().getBlockZ() || !pending.remove(player.getUniqueId(), session)) return;
        event.setCancelled(true);
        String query = event.lines().stream().map(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c).trim())
                .filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.joining(" "));
        PlatformScheduler.runForPlayer(player, () -> { restoreSign(player, session); finishSearch(player, session, query); });
    }
    private void finishSearch(Player player, PendingSearch session, String query) {
        if (query.isBlank() || query.equalsIgnoreCase("cancel")) {
            messages.send(player, "shop.search-cancelled", Map.of());
            if (session.quickSlot() >= 0) openQuickshop(player); else if (gui != null) gui.openMain(player);
        } else openSearch(player, query, 0, session.quickSlot());
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { pending.remove(e.getPlayer().getUniqueId()); }

    private static final List<String> POTION_FORMS = List.of("POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW");

    /** Called by category/search when an item requires a potion or enchantment choice. */
    public void openChoice(Player player, String material, int quickSlot, String category, int page) {
        openChoice(player, material, quickSlot, category, page, false);
    }
    public void openChoice(Player player, String material, int quickSlot, String category, int page, boolean selling) {
        if (selling && !player.hasPermission("veltryx.sell")) { messages.send(player, "general.no-permission"); return; }
        if (material.equals("ENCHANTED_BOOK")) openBooks(player, quickSlot, category, page, selling);
        else if (POTION_FORMS.contains(material)) openEffects(player, material, quickSlot, category, page, selling);
    }
    private void openEffects(Player player, String material, int quickSlot, String category, int page, boolean selling) {
        PlatformScheduler.runForPlayer(player, () -> {
            if (!player.hasPermission("veltryx.shop") || (selling && !player.hasPermission("veltryx.sell"))) {
                messages.send(player, "shop.no-permission"); return;
            }
            EffectHolder h = new EffectHolder(material, quickSlot, category, page, selling);
            Inventory inv = Bukkit.createInventory(h, 54, Txt.legacy("§8Choose effect: §b" + PriceTable.displayName(material))); h.inventory = inv;
            ShopItems.fill(inv);
            for (int i = 0; i < h.options.size() && i < 45; i++) {
                ChoiceCatalog.PotionOption option = h.options.get(i);
                Purchase purchase = new Purchase(material, option.key(), 0);
                List<String> lore = new ArrayList<>();
                lore.add("§7Form: §f" + PriceTable.displayName(material));
                lore.add("§7Variant: §f" + PotionDisplay.brewLabel(option.key()));
                lore.addAll(PotionDisplay.effects(material, option.key()));
                if (selling) lore.add("§7Exact sale payout is shown next.");
                else lore.add("§7Each: §a$§f" + Money.formatNumber(purchase.unitPrice(shop.prices())));
                lore.add("§eClick to select");
                org.bukkit.inventory.ItemStack icon = ShopItems.icon(material, "§b" + option.displayName(), lore);
                org.bukkit.potion.PotionType type = resolvePotion(option.key());
                if (type != null && icon.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta meta) {
                    meta.setBasePotionType(type); icon.setItemMeta(meta);
                }
                inv.setItem(i, icon);
            }
            for (int i = 0; i < POTION_FORMS.size(); i++) {
                String form = POTION_FORMS.get(i);
                boolean offered = shop.prices().buyCents(form) > 0 && (!selling || shop.prices().sellCents(form) > 0);
                inv.setItem(45 + i, ShopItems.icon(form, (form.equals(material) ? "§a" : "§b") + PriceTable.displayName(form),
                        List.of(!offered ? "§cNot offered" : form.equals(material) ? "§aSelected form" : "§eClick to change form")));
            }
            inv.setItem(49, ShopItems.icon("NETHER_STAR", "§bBack", List.of()));
            inv.setItem(53, ShopItems.icon("DRAGON_BREATH", "§bPotion forms", List.of(
                    "§7Select an effect above and a form below.",
                    "§7Lingering is made with dragon's breath.")));
            player.openInventory(inv);
        });
    }
    private void openBooks(Player player, int quickSlot, String category, int page, boolean selling) {
        PlatformScheduler.runForPlayer(player, () -> {
            if (!player.hasPermission("veltryx.shop") || (selling && !player.hasPermission("veltryx.sell"))) {
                messages.send(player, "shop.no-permission"); return;
            }
            BookHolder h = new BookHolder(quickSlot, category, page, selling);
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
            LevelHolder h = new LevelHolder(parent.quickSlot, parent.category, parent.page, option, parent.selling);
            Inventory inv = Bukkit.createInventory(h, 27, Txt.legacy("§8" + option.displayName() + " — Level")); h.inventory = inv; ShopItems.fill(inv);
            for (int level = 1; level <= option.maxLevel(); level++) {
                Purchase purchase = new Purchase("ENCHANTED_BOOK", option.key(), level);
                inv.setItem(9 + level, ShopItems.icon("ENCHANTED_BOOK", level, "§b" + option.displayName() + " " + level,
                        List.of(parent.selling ? "§7Exact sale payout is shown next." : "§7Each: §a$§f" + Money.formatNumber(purchase.unitPrice(shop.prices())),
                                "§eClick to select")));
            }
            inv.setItem(22, ShopItems.icon("NETHER_STAR", "§bBack", List.of())); player.openInventory(inv);
        });
    }
    private void selected(Player player, Purchase purchase, int quickSlot, String category, int page, boolean selling) {
        if (quickSlot >= 0) openAmount(player, purchase, quickSlot, 1);
        else if (gui != null && selling) gui.openSelling(player, purchase, category, page, 1);
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
                    List.of("§7Amount: §f" + amount, "§7Price: §a$§f" + totalPrice(purchase.unitPrice(shop.prices()), amount))));
            inv.setItem(15, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 1, "§b+1", List.of()));
            inv.setItem(16, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 16, "§b+16", List.of()));
            inv.setItem(17, ShopItems.icon("LIGHT_BLUE_STAINED_GLASS_PANE", 32, "§b+32", List.of()));
            inv.setItem(18, ShopItems.icon("ARROW", "§bBack", List.of()));
            inv.setItem(22, ShopItems.icon("LIME_STAINED_GLASS_PANE", "§aSave QuickShop slot", List.of("§7Click the saved item later to buy.")));
            player.openInventory(inv);
        });
    }
    private static String totalPrice(long unit, int amount) {
        return Money.formatNumber(java.math.BigInteger.valueOf(Math.max(0L, unit)).multiply(java.math.BigInteger.valueOf(amount)));
    }
    private void back(Player player, int quickSlot, String category, int page) {
        if (quickSlot >= 0) openQuickshop(player);
        else if (gui != null) gui.returnTo(player, category, page);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory().getHolder() != h || h.transitioning) return;
        if (!player.hasPermission("veltryx.shop")) { messages.send(player, "shop.no-permission"); return; }
        if ((h instanceof EffectHolder e && e.selling) || (h instanceof BookHolder b && b.selling)
                || (h instanceof LevelHolder l && l.selling)) {
            if (!player.hasPermission("veltryx.sell")) { messages.send(player, "general.no-permission"); return; }
        }
        org.bukkit.event.inventory.ClickType click = event.getClick();
        if (click != org.bukkit.event.inventory.ClickType.LEFT && click != org.bukkit.event.inventory.ClickType.RIGHT
                && click != org.bukkit.event.inventory.ClickType.SHIFT_LEFT && click != org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) return;
        int slot = event.getSlot();
        if (h instanceof QuickHolder quick) {
            if (slot < 0 || slot >= QuickShopStore.SLOTS) return;
            QuickShopStore.QuickSlot saved = slots.get(player.getUniqueId(), slot);
            QuickQuote shown = quick.quotes.get(slot);
            h.transitioning = true;
            if (saved != null && event.isRightClick() && event.isShiftClick()) { slots.set(player.getUniqueId(), slot, null); openQuickshop(player); }
            else if (event.isRightClick() || event.isShiftClick() || shown == null) prompt(player, slot);
            else if (!shown.slot().equals(saved)) { messages.send(player, "shop.quick-changed"); openQuickshop(player); }
            else if (gui != null) {
                gui.buy(player, saved.purchase(), saved.amount(), shown.unitPrice());
                openQuickshop(player);
            } else h.transitioning = false;
        } else if (h instanceof SearchHolder search) {
            if (slot >= 0 && slot < 45) {
                int index = search.page * 45 + slot;
                if (index >= search.results.size()) return;
                PriceTable.Entry entry = search.results.get(index);
                boolean selling = search.quickSlot < 0 && event.isRightClick();
                if (selling && !player.hasPermission("veltryx.sell")) { messages.send(player, "general.no-permission"); return; }
                String origin = search.quickSlot < 0 ? ShopGui.searchOrigin(search.query) : null;
                h.transitioning = true;
                if (SurvivalItems.needsChoice(entry.material())) openChoice(player, entry.material(), search.quickSlot, origin, search.page, selling);
                else selected(player, Purchase.of(entry.material()), search.quickSlot, origin, search.page, selling);
            } else if (slot == 45 && search.page > 0) { h.transitioning = true; openSearch(player, search.query, search.page - 1, search.quickSlot); }
            else if (slot == 53 && (search.page + 1) * 45 < search.results.size()) {
                h.transitioning = true; openSearch(player, search.query, search.page + 1, search.quickSlot);
            } else if (slot == 49) { h.transitioning = true; back(player, search.quickSlot, null, 0); }
            else if (slot == 50) { h.transitioning = true; prompt(player, search.quickSlot); }
        } else if (h instanceof EffectHolder effects) {
            if (slot == 49) { h.transitioning = true; back(player, effects.quickSlot, effects.category, effects.page); }
            else if (slot >= 45 && slot < 49) {
                String form = POTION_FORMS.get(slot - 45);
                if (shop.prices().buyCents(form) <= 0 || (effects.selling && shop.prices().sellCents(form) <= 0)) {
                    messages.send(player, effects.selling ? "shop.no-sell" : "shop.no-price"); return;
                }
                if (!form.equals(effects.material)) {
                    h.transitioning = true; openEffects(player, form, effects.quickSlot, effects.category, effects.page, effects.selling);
                }
            } else if (slot >= 0 && slot < effects.options.size() && slot < 45) {
                ChoiceCatalog.PotionOption option = effects.options.get(slot);
                if (resolvePotion(option.key()) == null) { messages.send(player, "shop.item-unavailable"); return; }
                h.transitioning = true;
                selected(player, new Purchase(effects.material, option.key(), 0), effects.quickSlot, effects.category, effects.page, effects.selling);
            }
        } else if (h instanceof BookHolder books) {
            if (slot == 49) { h.transitioning = true; back(player, books.quickSlot, books.category, books.page); }
            else if (slot >= 0 && slot < books.options.size() && slot < 45) {
                h.transitioning = true; openLevels(player, books, books.options.get(slot));
            }
        } else if (h instanceof LevelHolder levels) {
            if (slot == 22) { h.transitioning = true; openBooks(player, levels.quickSlot, levels.category, levels.page, levels.selling); }
            else if (slot >= 10 && slot <= 9 + levels.option.maxLevel()) {
                h.transitioning = true;
                selected(player, new Purchase("ENCHANTED_BOOK", levels.option.key(), slot - 9), levels.quickSlot, levels.category, levels.page, levels.selling);
            }
        } else if (h instanceof AmountHolder amount) {
            int change = switch (slot) { case 9 -> -32; case 10 -> -16; case 11 -> -1; case 15 -> 1; case 16 -> 16; case 17 -> 32; default -> 0; };
            if (change != 0) { h.transitioning = true; openAmount(player, amount.purchase, amount.quickSlot, amount.amount + change); }
            else if (slot == 18) { h.transitioning = true; openQuickshop(player); }
            else if (slot == 22) {
                if (amount.purchase.unitPrice(shop.prices()) <= 0) { messages.send(player, "shop.no-price", Map.of()); return; }
                h.transitioning = true;
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
