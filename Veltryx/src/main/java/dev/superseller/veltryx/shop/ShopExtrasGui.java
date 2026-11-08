package dev.superseller.veltryx.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;

/**
 * The shop extras:
 * <ul>
 *   <li><b>Quick shop</b> ({@code /quickshop}, {@code /qs}, or the ender
 *       chest in the shop) — 5 rows of the common items to buy straight
 *       from the shop without searching.</li>
 *   <li><b>Search</b> — type a query in chat, get paged results to buy.</li>
 *   <li><b>Choice purchases</b> — potions (choose the type), tipped arrows
 *       (choose the effect) and enchanted books (choose the enchantment
 *       and its level).</li>
 * </ul>
 */
public final class ShopExtrasGui implements Listener {

    /** Marks the quick-shop (5 rows of items). */
    public static final class QuickHolder implements InventoryHolder {
        private final List<PriceTable.Entry> items = new ArrayList<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Marks paged search results. */
    public static final class SearchHolder implements InventoryHolder {
        private final String query;
        private final List<PriceTable.Entry> results;
        private final int page;
        private Inventory inventory;

        SearchHolder(String query, List<PriceTable.Entry> results, int page) {
            this.query = query;
            this.results = results;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Marks the potion or tipped-arrow effect picker (kind = "potion"/"arrow"). */
    public static final class EffectHolder implements InventoryHolder {
        private final String kind;
        private final List<ChoiceCatalog.PotionOption> options = new ArrayList<>();
        private Inventory inventory;

        EffectHolder(String kind) {
            this.kind = kind;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Marks the enchanted-book enchantment picker. */
    public static final class BookHolder implements InventoryHolder {
        private final List<ChoiceCatalog.BookOption> options = new ArrayList<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Marks the level picker of one enchantment. */
    public static final class LevelHolder implements InventoryHolder {
        private final ChoiceCatalog.BookOption option;
        private Inventory inventory;

        LevelHolder(ChoiceCatalog.BookOption option) {
            this.option = option;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Players currently typing a search query in chat. */
    private final Map<UUID, Boolean> pendingSearch = new ConcurrentHashMap<>();

    private final ShopService shop;
    private final EconomyService economy;
    private final Messages messages;
    private final VeltryxConfig config;

    public ShopExtrasGui(ShopService shop, EconomyService economy, Messages messages, VeltryxConfig config) {
        this.shop = shop;
        this.economy = economy;
        this.messages = messages;
        this.config = config;
    }

    // ------------------------------------------------------------- quick shop

    /** Opens the quick shop: 5 rows of the most used items (Folia-safe). */
    public void openQuickshop(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            QuickHolder holder = new QuickHolder();
            Inventory inventory = Bukkit.createInventory(holder, 54, Txt.legacy("§8§lQuick shop"));
            holder.inventory = inventory;
            List<String> wanted = config.quickshopItems();
            for (String name : wanted) {
                if (holder.items.size() >= 45) {
                    break;
                }
                PriceTable.Entry entry = shop.prices().get(name);
                if (entry == null) {
                    continue;
                }
                int slot = holder.items.size();
                holder.items.add(entry);
                inventory.setItem(slot, item(Material.matchMaterial(entry.material()),
                        Txt.legacy("§f" + PriceTable.displayName(entry.material())),
                        List.of(Txt.legacy("§7Buy: §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(entry.buyCents())
                                        + " §8×1 §7| §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(entry.buyCents() * 64)
                                        + " §8×64"),
                                Txt.legacy("§7Sell: §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(entry.sellCents())),
                                net.kyori.adventure.text.Component.text(""),
                                Txt.legacy("§eLeft-click§7: buy 1"),
                                Txt.legacy("§eShift-click§7: buy 64"))));
            }
            inventory.setItem(45, item(Material.BARRIER, Txt.legacy("§cBack to the shop"), List.of()));
            inventory.setItem(49, item(Material.GOLD_INGOT, Txt.legacy("§6Your balance: §a$§f"
                    + dev.superseller.veltryx.util.Money.formatCompact(shop.balance(player))), List.of()));
            player.openInventory(inventory);
        });
    }

    // ----------------------------------------------------------------- search

    /** Asks for a search query in chat. */
    public void openSearchPrompt(Player player) {
        player.closeInventory();
        pendingSearch.put(player.getUniqueId(), Boolean.TRUE);
        messages.send(player, "shop.search-prompt", Map.of());
    }

    /** Opens paged search results for a query. */
    public void openSearch(Player player, String query, int page) {
        PlatformScheduler.runForPlayer(player, () -> {
            List<PriceTable.Entry> results = search(query);
            int pages = Math.max(1, (results.size() + 44) / 45);
            int current = Math.max(0, Math.min(page, pages - 1));
            SearchHolder holder = new SearchHolder(query, results, current);
            Inventory inventory = Bukkit.createInventory(holder, 54,
                    Txt.legacy("§8§lSearch: §f" + query + " §7(" + (current + 1) + "/" + pages + ")"));
            holder.inventory = inventory;
            for (int slot = 0; slot < 45; slot++) {
                int index = current * 45 + slot;
                if (index >= results.size()) {
                    break;
                }
                PriceTable.Entry entry = results.get(index);
                inventory.setItem(slot, item(Material.matchMaterial(entry.material()),
                        Txt.legacy("§f" + PriceTable.displayName(entry.material())),
                        List.of(Txt.legacy("§7Buy: §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(entry.buyCents())
                                        + " §8×1 §7| §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(entry.buyCents() * 64)
                                        + " §8×64"),
                                Txt.legacy("§7Sell: §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(entry.sellCents())),
                                net.kyori.adventure.text.Component.text(""),
                                Txt.legacy("§eLeft-click§7: buy 1"),
                                Txt.legacy("§eShift-click§7: buy 64"))));
            }
            if (current > 0) {
                inventory.setItem(45, item(Material.ARROW, Txt.legacy("§ePrevious page"), List.of()));
            }
            inventory.setItem(49, item(Material.BARRIER, Txt.legacy("§cBack to the shop"), List.of()));
            if (current < pages - 1) {
                inventory.setItem(53, item(Material.ARROW, Txt.legacy("§eNext page"), List.of()));
            }
            player.openInventory(inventory);
        });
    }

    /** All shop items whose display or material name contains the query. */
    List<PriceTable.Entry> search(String query) {
        List<PriceTable.Entry> out = new ArrayList<>();
        for (PriceTable.Category category : shop.prices().categories()) {
            for (PriceTable.Entry entry : category.items()) {
                if (ChoiceCatalog.matches(query, PriceTable.displayName(entry.material()), entry.material())) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    /** Captures the typed search query. */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!pendingSearch.containsKey(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        pendingSearch.remove(player.getUniqueId());
        String query = event.getMessage().trim();
        if (query.equalsIgnoreCase("cancel")) {
            messages.send(player, "shop.search-cancelled", java.util.Map.of());
            return;
        }
        openSearch(player, query, 0);
    }

    // ------------------------------------------------------- choice purchases

    /** Opens the potion type picker. */
    public void openPotions(Player player) {
        openEffectPicker(player, "potion");
    }

    /** Opens the tipped-arrow effect picker. */
    public void openArrows(Player player) {
        openEffectPicker(player, "arrow");
    }

    private void openEffectPicker(Player player, String kind) {
        PlatformScheduler.runForPlayer(player, () -> {
            EffectHolder holder = new EffectHolder(kind);
            String title = "potion".equals(kind) ? "§8§lPotions — choose a type" : "§8§lTipped arrows — choose an effect";
            Inventory inventory = Bukkit.createInventory(holder, 54, Txt.legacy(title));
            holder.inventory = inventory;
            List<ChoiceCatalog.PotionOption> options = ChoiceCatalog.potions();
            for (int i = 0; i < options.size() && i < 45; i++) {
                ChoiceCatalog.PotionOption option = options.get(i);
                holder.options.add(option);
                long price = "potion".equals(kind) ? option.priceCents() : ChoiceCatalog.arrowPackCents(option);
                Material icon = Material.matchMaterial("potion".equals(kind) ? "POTION" : "TIPPED_ARROW");
                inventory.setItem(i, item(icon,
                        Txt.legacy("§b" + option.displayName()
                                + ("potion".equals(kind) ? "" : " §7(" + ChoiceCatalog.TIPPED_ARROW_PACK + "x)")),
                        List.of(Txt.legacy("§7Price: §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(price)),
                                net.kyori.adventure.text.Component.text(""),
                                Txt.legacy("§eClick to buy"))));
            }
            inventory.setItem(45, item(Material.BARRIER, Txt.legacy("§cBack to the shop"), List.of()));
            inventory.setItem(49, item(Material.GOLD_INGOT, Txt.legacy("§6Your balance: §a$§f"
                    + dev.superseller.veltryx.util.Money.formatCompact(shop.balance(player))), List.of()));
            player.openInventory(inventory);
        });
    }

    /** Opens the enchanted-book enchantment picker. */
    public void openBooks(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            BookHolder holder = new BookHolder();
            Inventory inventory = Bukkit.createInventory(holder, 54,
                    Txt.legacy("§8§lEnchanted books — choose an enchantment"));
            holder.inventory = inventory;
            List<ChoiceCatalog.BookOption> options = ChoiceCatalog.books();
            for (int i = 0; i < options.size() && i < 45; i++) {
                ChoiceCatalog.BookOption option = options.get(i);
                holder.options.add(option);
                inventory.setItem(i, item(Material.ENCHANTED_BOOK,
                        Txt.legacy("§b" + option.displayName()),
                        List.of(Txt.legacy("§7Max level: §f" + roman(option.maxLevel())),
                                Txt.legacy("§7Price: §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(option.pricePerLevelCents())
                                        + " §7per level"),
                                net.kyori.adventure.text.Component.text(""),
                                Txt.legacy("§eClick to pick the level"))));
            }
            inventory.setItem(45, item(Material.BARRIER, Txt.legacy("§cBack to the shop"), List.of()));
            inventory.setItem(49, item(Material.GOLD_INGOT, Txt.legacy("§6Your balance: §a$§f"
                    + dev.superseller.veltryx.util.Money.formatCompact(shop.balance(player))), List.of()));
            player.openInventory(inventory);
        });
    }

    /** Opens the level picker of one enchantment. */
    public void openLevels(Player player, ChoiceCatalog.BookOption option) {
        PlatformScheduler.runForPlayer(player, () -> {
            LevelHolder holder = new LevelHolder(option);
            Inventory inventory = Bukkit.createInventory(holder, 27,
                    Txt.legacy("§8§l" + option.displayName() + " — choose the level"));
            holder.inventory = inventory;
            for (int level = 1; level <= option.maxLevel(); level++) {
                long price = ChoiceCatalog.bookPriceCents(option, level);
                inventory.setItem(9 + level, item(Material.ENCHANTED_BOOK,
                        Txt.legacy("§b" + option.displayName() + " " + roman(level)),
                        List.of(Txt.legacy("§7Price: §a$§f"
                                        + dev.superseller.veltryx.util.Money.formatCompact(price)),
                                net.kyori.adventure.text.Component.text(""),
                                Txt.legacy("§eClick to buy"))));
            }
            inventory.setItem(22, item(Material.BARRIER, Txt.legacy("§cBack"), List.of()));
            player.openInventory(inventory);
        });
    }

    // -------------------------------------------------------------- click bus

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holder = event.getInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (holder instanceof QuickHolder quick) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != quick) {
                return;
            }
            int slot = event.getSlot();
            if (slot == 45) {
                openShop(player);
            } else if (slot < 45 && slot < quick.items.size()) {
                buyFlat(player, quick.items.get(slot), event.isShiftClick() ? 64 : 1);
            }
            return;
        }
        if (holder instanceof SearchHolder searchHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != searchHolder) {
                return;
            }
            int slot = event.getSlot();
            if (slot < 45) {
                int index = searchHolder.page * 45 + slot;
                if (index < searchHolder.results.size()) {
                    buyFlat(player, searchHolder.results.get(index), event.isShiftClick() ? 64 : 1);
                }
            } else if (slot == 45) {
                openSearch(player, searchHolder.query, searchHolder.page - 1);
            } else if (slot == 49) {
                openShop(player);
            } else if (slot == 53) {
                openSearch(player, searchHolder.query, searchHolder.page + 1);
            }
            return;
        }
        if (holder instanceof EffectHolder effects) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != effects) {
                return;
            }
            int slot = event.getSlot();
            if (slot == 45) {
                openShop(player);
            } else if (slot >= 0 && slot < effects.options.size()) {
                buyEffect(player, effects.kind, effects.options.get(slot));
            }
            return;
        }
        if (holder instanceof BookHolder books) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != books) {
                return;
            }
            int slot = event.getSlot();
            if (slot == 45) {
                openShop(player);
            } else if (slot >= 0 && slot < books.options.size()) {
                openLevels(player, books.options.get(slot));
            }
            return;
        }
        if (holder instanceof LevelHolder levels) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != levels) {
                return;
            }
            int slot = event.getSlot();
            if (slot == 22) {
                openBooks(player);
            } else if (slot > 9 && slot <= 9 + levels.option.maxLevel()) {
                buyBook(player, levels.option, slot - 9);
            }
        }
    }

    // --------------------------------------------------------------- purchases

    /** The shop GUI opens back through this hook (set in the wiring). */
    private java.util.function.Consumer<Player> shopOpener;

    public void attachShopOpener(java.util.function.Consumer<Player> opener) {
        this.shopOpener = opener;
    }

    private void openShop(Player player) {
        if (shopOpener != null) {
            shopOpener.accept(player);
        }
    }

    private void buyFlat(Player player, PriceTable.Entry entry, int amount) {
        ShopService.BuyResult result = shop.buy(player, entry.material(), amount);
        if (result.ok()) {
            player.sendMessage(messages.render("shop.buy-done", Map.of(
                    "amount", String.valueOf(result.given()),
                    "item", entry.material(),
                    "price", dev.superseller.veltryx.util.Money.formatCompact(result.spentCents()),
                    "balance", dev.superseller.veltryx.util.Money.formatCompact(shop.balance(player)))));
        } else {
            player.sendMessage(messages.render(result.failKey(), result.failValues()));
        }
    }

    private void buyEffect(Player player, String kind, ChoiceCatalog.PotionOption option) {
        long price = "potion".equals(kind) ? option.priceCents() : ChoiceCatalog.arrowPackCents(option);
        org.bukkit.potion.PotionType type = resolvePotion(option.key());
        if (type == null) {
            messages.send(player, "shop.no-price", Map.of());
            return;
        }
        Material material = Material.matchMaterial("potion".equals(kind) ? "POTION" : "TIPPED_ARROW");
        if (material == null) {
            messages.send(player, "shop.no-price", Map.of());
            return;
        }
        int amount = "potion".equals(kind) ? 1 : ChoiceCatalog.TIPPED_ARROW_PACK;
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(type);
        } else if (meta != null) {
            // tipped arrows: some API versions carry the potion type on a
            // different meta type — call setBasePotionType if it exists
            try {
                meta.getClass().getMethod("setBasePotionType", org.bukkit.potion.PotionType.class)
                        .invoke(meta, type);
            } catch (ReflectiveOperationException ignored) {
                // leave the arrow plain rather than fail the purchase
            }
        }
        if (meta != null) {
            stack.setItemMeta(meta);
        }
        buyCustom(player, stack, price, option.displayName());
    }

    private void buyBook(Player player, ChoiceCatalog.BookOption option, int level) {
        Enchantment enchantment = enchant(option.key());
        if (enchantment == null) {
            messages.send(player, "shop.no-price", Map.of());
            return;
        }
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            storage.addStoredEnchant(enchantment, level, true);
            stack.setItemMeta(meta);
        }
        buyCustom(player, stack, ChoiceCatalog.bookPriceCents(option, level),
                option.displayName() + " " + roman(level));
    }

    /** Charges the price and gives the custom-built stack. */
    private void buyCustom(Player player, ItemStack stack, long priceCents, String label) {
        if (!economy.has(player.getUniqueId(), priceCents)) {
            player.sendMessage(messages.render("shop.cannot-afford", Map.of(
                    "price", dev.superseller.veltryx.util.Money.formatCompact(priceCents),
                    "balance", dev.superseller.veltryx.util.Money.formatCompact(shop.balance(player)))));
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            player.sendMessage(messages.render("shop.no-space", Map.of()));
            return;
        }
        economy.withdraw(player.getUniqueId(), player.getName(), priceCents);
        player.sendMessage(messages.render("shop.buy-done", Map.of(
                "amount", String.valueOf(stack.getAmount()),
                "item", label,
                "price", dev.superseller.veltryx.util.Money.formatCompact(priceCents),
                "balance", dev.superseller.veltryx.util.Money.formatCompact(shop.balance(player)))));
    }

    // ----------------------------------------------------------------- helpers

    /** Runtime-safe potion type resolution (handles the SPEED/SWIFTNESS rename). */
    static org.bukkit.potion.PotionType resolvePotion(String key) {
        if ("SPEED".equals(key)) {
            try {
                return org.bukkit.potion.PotionType.valueOf("SWIFTNESS");
            } catch (IllegalArgumentException ignored) {
                // older naming
            }
        }
        try {
            return org.bukkit.potion.PotionType.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Compile-checked enchantment constant lookup for the book picker. */
    static Enchantment enchant(String key) {
        switch (key) {
            case "PROTECTION": return Enchantment.PROTECTION;
            case "FIRE_PROTECTION": return Enchantment.FIRE_PROTECTION;
            case "BLAST_PROTECTION": return Enchantment.BLAST_PROTECTION;
            case "PROJECTILE_PROTECTION": return Enchantment.PROJECTILE_PROTECTION;
            case "FEATHER_FALLING": return Enchantment.FEATHER_FALLING;
            case "EFFICIENCY": return Enchantment.EFFICIENCY;
            case "SHARPNESS": return Enchantment.SHARPNESS;
            case "SMITE": return Enchantment.SMITE;
            case "BANE_OF_ARTHROPODS": return Enchantment.BANE_OF_ARTHROPODS;
            case "POWER": return Enchantment.POWER;
            case "UNBREAKING": return Enchantment.UNBREAKING;
            case "THORNS": return Enchantment.THORNS;
            case "RESPIRATION": return Enchantment.RESPIRATION;
            case "AQUA_AFFINITY": return Enchantment.AQUA_AFFINITY;
            case "DEPTH_STRIDER": return Enchantment.DEPTH_STRIDER;
            case "FORTUNE": return Enchantment.FORTUNE;
            case "LOOTING": return Enchantment.LOOTING;
            case "KNOCKBACK": return Enchantment.KNOCKBACK;
            case "FIRE_ASPECT": return Enchantment.FIRE_ASPECT;
            case "PUNCH": return Enchantment.PUNCH;
            case "FLAME": return Enchantment.FLAME;
            case "LURE": return Enchantment.LURE;
            case "LUCK_OF_THE_SEA": return Enchantment.LUCK_OF_THE_SEA;
            case "LOYALTY": return Enchantment.LOYALTY;
            case "IMPALING": return Enchantment.IMPALING;
            case "RIPTIDE": return Enchantment.RIPTIDE;
            case "CHANNELING": return Enchantment.CHANNELING;
            case "MENDING": return Enchantment.MENDING;
            case "SILK_TOUCH": return Enchantment.SILK_TOUCH;
            case "INFINITY": return Enchantment.INFINITY;
            default: return null;
        }
    }

    /** Roman numerals for the level pickers (I..V). */
    static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    private static ItemStack item(Material material, net.kyori.adventure.text.Component name,
                                  List<net.kyori.adventure.text.Component> lore) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(new ArrayList<>(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
