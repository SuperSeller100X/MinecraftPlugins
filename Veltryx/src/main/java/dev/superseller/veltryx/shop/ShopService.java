package dev.superseller.veltryx.shop;

import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.util.Money;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Shop transactions: buy with money, sell for money. Prices come from
 * {@link PriceTable} (loaded from the server's {@code shop.yml}).
 */
public final class ShopService {

    private final EconomyService economy;
    private final Path shopFile;
    private volatile PriceTable prices;

    public ShopService(EconomyService economy, Path shopFile) {
        this.economy = economy;
        this.shopFile = shopFile;
        this.prices = load();
    }

    /** Re-reads shop.yml (/veltryx reload). */
    public void reload() {
        this.prices = load();
    }

    private PriceTable load() {
        try {
            String recipes;
            try (java.io.InputStream in = ShopService.class.getResourceAsStream("/shop-recipes.txt")) {
                if (in == null) throw new IOException("Missing bundled shop-recipes.txt");
                recipes = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (recipes.isBlank()) throw new IOException("Empty bundled shop-recipes.txt");
            }
            return PriceTable.parse(Files.readString(shopFile), recipes, name -> {
                Material material = Material.matchMaterial(name);
                return material != null && material.isItem();
            });
        } catch (IOException | RuntimeException e) {
            // Fail closed: trading without its recipe safety model could create money.
            java.util.logging.Logger.getLogger(ShopService.class.getName()).log(
                    java.util.logging.Level.SEVERE, "Could not load shop; trading is disabled until it is repaired", e);
            return PriceTable.parse("");
        }
    }

    public PriceTable prices() {
        return prices;
    }

    /** Result of a buy attempt. */
    public record BuyResult(int given, long spentCents, String failKey, Map<String, String> failValues) {
        public boolean ok() {
            return failKey == null;
        }
    }

    /** Result of a sell attempt. */
    public record SellResult(int sold, long earnedCents, String failKey) {
        public boolean ok() {
            return failKey == null;
        }
    }

    /**
     * Buys {@code amount} of a material. Charges only for what actually fits
     * in the inventory — never money for thin air.
     */
    public BuyResult buy(Player player, String materialName, int amount) {
        return buy(player, Purchase.of(materialName), amount);
    }

    /** Buy a specific potion/book variant or ordinary item; charge only for delivered items. */
    public BuyResult buy(Player player, Purchase purchase, int requested) {
        return buy(player, purchase, requested, -1);
    }

    /** A positive expected unit price refuses a stale GUI quote. */
    public BuyResult buy(Player player, Purchase purchase, int requested, long expectedUnit) {
        PriceTable table = prices;
        long unitPrice = purchase == null ? -1 : purchase.unitPrice(table);
        if (expectedUnit > 0 && unitPrice != expectedUnit)
            return new BuyResult(0, 0, "shop.price-changed", Map.of());
        Material material = purchase == null ? null : Material.matchMaterial(purchase.material());
        if (unitPrice <= 0 || material == null || !material.isItem())
            return new BuyResult(0, 0, "shop.no-price", Map.of());
        int amount = Math.max(1, Math.min(64, requested));
        if (unitPrice > Long.MAX_VALUE / amount) return new BuyResult(0, 0, "shop.no-price", Map.of());
        long total = unitPrice * amount;
        // Withdraw before adding items so concurrent balance changes cannot grant free goods.
        try {
            if (!economy.withdraw(player.getUniqueId(), player.getName(), total)) {
                return new BuyResult(0, 0, "shop.cannot-afford", Map.of(
                        "price", Money.formatCompact(total),
                        "balance", Money.formatCompact(economy.getBalanceExact(player.getUniqueId()))));
            }
        } catch (RuntimeException error) {
            java.util.logging.Logger.getLogger(ShopService.class.getName()).log(
                    java.util.logging.Level.SEVERE, "Could not charge for shop purchase", error);
            return new BuyResult(0, 0, "shop.transaction-error", Map.of());
        }
        ItemStack stack;
        try {
            stack = createPurchaseItem(player, material, purchase, amount);
        } catch (RuntimeException error) {
            java.util.logging.Logger.getLogger(ShopService.class.getName()).log(
                    java.util.logging.Level.WARNING, "Could not create purchased " + purchase.material(), error);
            if (!refundPurchase(player, total)) return new BuyResult(0, 0, "shop.transaction-error", Map.of());
            return new BuyResult(0, 0, ExplorerMaps.namedMap(purchase.material())
                    ? "shop.map-unavailable" : "shop.item-unavailable", Map.of());
        }
        if (stack == null) {
            if (!refundPurchase(player, total)) return new BuyResult(0, 0, "shop.transaction-error", Map.of());
            return new BuyResult(0, 0, ExplorerMaps.namedMap(purchase.material())
                    ? "shop.map-unavailable" : "shop.item-unavailable", Map.of());
        }
        ItemStack[] before;
        try {
            ItemStack[] original = player.getInventory().getStorageContents();
            before = new ItemStack[original.length];
            for (int i = 0; i < original.length; i++) before[i] = original[i] == null ? null : original[i].clone();
        } catch (RuntimeException error) {
            java.util.logging.Logger.getLogger(ShopService.class.getName()).log(
                    java.util.logging.Level.SEVERE, "Could not snapshot inventory before shop purchase", error);
            refundPurchase(player, total);
            return new BuyResult(0, 0, "shop.transaction-error", Map.of());
        }
        Map<Integer, ItemStack> leftover;
        try { leftover = player.getInventory().addItem(stack); }
        catch (RuntimeException error) {
            java.util.logging.Logger.getLogger(ShopService.class.getName()).log(
                    java.util.logging.Level.SEVERE, "Could not deliver shop purchase; attempting rollback", error);
            try { player.getInventory().setStorageContents(before); }
            catch (RuntimeException rollback) {
                java.util.logging.Logger.getLogger(ShopService.class.getName()).log(
                        java.util.logging.Level.SEVERE, "Shop delivery rollback requires administrator review", rollback);
                return new BuyResult(0, 0, "shop.transaction-error", Map.of());
            }
            refundPurchase(player, total);
            return new BuyResult(0, 0, "shop.transaction-error", Map.of());
        }
        int given = amount;
        for (ItemStack rest : leftover.values()) given -= rest.getAmount();
        given = Math.max(0, Math.min(amount, given));
        if (given < amount && !refundPurchase(player, unitPrice * (amount - given)))
            return new BuyResult(given, total, "shop.transaction-error", Map.of());
        if (given == 0) return new BuyResult(0, 0, "shop.no-space", Map.of());
        return new BuyResult(given, unitPrice * given, null, Map.of());
    }

    private boolean refundPurchase(Player player, long cents) {
        try { economy.deposit(player.getUniqueId(), player.getName(), cents); return true; }
        catch (RuntimeException error) {
            java.util.logging.Logger.getLogger(ShopService.class.getName()).log(
                    java.util.logging.Level.SEVERE, "Shop refund requires administrator review for " + player.getUniqueId(), error);
            return false;
        }
    }

    private ItemStack createPurchaseItem(Player player, Material material, Purchase purchase, int amount) {
        ItemStack stack = new ItemStack(material, amount);
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (SurvivalItems.needsChoice(purchase.material())) {
            if (purchase.material().equals("ENCHANTED_BOOK")) {
                org.bukkit.enchantments.Enchantment enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                        .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                        .get(org.bukkit.NamespacedKey.minecraft(purchase.choice().toLowerCase(java.util.Locale.ROOT)));
                if (enchantment == null || !(meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta book)) return null;
                if (!book.addStoredEnchant(enchantment, purchase.level(), false)) return null;
            } else {
                org.bukkit.potion.PotionType potion = ShopExtrasGui.resolvePotion(purchase.choice());
                if (potion == null || !(meta instanceof org.bukkit.inventory.meta.PotionMeta potionMeta)) return null;
                potionMeta.setBasePotionType(potion);
            }
        } else if (purchase.material().equals("WRITTEN_BOOK") && meta instanceof org.bukkit.inventory.meta.BookMeta book) {
            book.title(dev.superseller.veltryx.util.Txt.legacy("§0Book"));
            book.author(dev.superseller.veltryx.util.Txt.legacy(player.getName()));
            book.addPages(dev.superseller.veltryx.util.Txt.legacy("§0A book from the server shop."));
        } else if (ExplorerMaps.namedMap(purchase.material())) {
            if (!(meta instanceof org.bukkit.inventory.meta.MapMeta map)
                    || !ExplorerMaps.prepare(player, stack, map)) return null;
            return stack;
        } else if (purchase.material().equals("FILLED_MAP")) {
            if (!(meta instanceof org.bukkit.inventory.meta.MapMeta map)) return null;
            org.bukkit.map.MapView view = org.bukkit.Bukkit.createMap(player.getWorld());
            if (view == null) return null;
            view.setCenterX(player.getLocation().getBlockX());
            view.setCenterZ(player.getLocation().getBlockZ());
            map.setMapView(view);
        } else if (purchase.material().endsWith("_MAP")) {
            // Unknown future explorer maps must never silently become ordinary maps.
            return null;
        }
        if (meta != null && !stack.setItemMeta(meta)) return null;
        return stack;
    }

    /** Per-item value from the current shop table, including variant, enchantments and wear. */
    public long sellUnitCents(ItemStack stack) {
        return sellUnitCents(prices, stack);
    }

    private long sellUnitCents(PriceTable table, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) return 0;
        String material = stack.getType().name();
        PriceTable.Entry entry = table.get(material);
        if (entry == null || entry.sellCents() <= 0) return 0;
        if (SurvivalItems.needsChoice(material) && !material.equals("ENCHANTED_BOOK")
                && stack.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potion
                && potion.getBasePotionType() != null) {
            String key = potion.getBasePotionType().name();
            if (ChoiceCatalog.potion(key) != null) {
                long base = VariantPricing.sellCents(table, material, key);
                double cost = VariantPricing.potionCost(table, material, key);
                long cap = Double.isFinite(cost) ? (long) Math.min(Long.MAX_VALUE, Math.floor(cost)) : 0;
                if (base <= 0 || cap <= 0) return 0;
                return ItemValue.valueCents(base, Math.min(cap, VariantPricing.buyCents(table, material, key)), stack);
            }
        }
        return ItemValue.valueCents(entry.sellCents(), entry.buyCents(), stack);
    }

    private record SalePortion(int slot, ItemStack stack) { }
    private record SalePlan(ItemStack[] before, ItemStack[] after, SellResult result, java.util.List<SalePortion> portions) { }

    /** Immutable preview; the private item snapshots protect against equal-value swaps. */
    public static final class SaleQuote {
        private final SellResult result;
        private final java.util.List<SalePortion> portions;
        private SaleQuote(SellResult result, java.util.List<SalePortion> portions) {
            this.result = result; this.portions = java.util.List.copyOf(portions);
        }
        public SellResult result() { return result; }
        /** Copies of the exact stack portions the quote would consume, for GUI previews. */
        public java.util.List<ItemStack> previewItems() {
            java.util.List<ItemStack> copies = new java.util.ArrayList<>();
            for (SalePortion portion : portions) copies.add(portion.stack().clone());
            return java.util.List.copyOf(copies);
        }
        private boolean matches(SalePlan plan) {
            if (!result.equals(plan.result()) || portions.size() != plan.portions().size()) return false;
            for (int i = 0; i < portions.size(); i++) {
                SalePortion a = portions.get(i), b = plan.portions().get(i);
                if (a.slot() != b.slot() || a.stack().getAmount() != b.stack().getAmount()
                        || !a.stack().isSimilar(b.stack())) return false;
            }
            return true;
        }
    }

    private SalePlan planSale(Player player, java.util.function.Predicate<ItemStack> matches, int maxAmount, PriceTable table) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack[] before = new ItemStack[storage.length], after = new ItemStack[storage.length];
        for (int i = 0; i < storage.length; i++) {
            before[i] = storage[i] == null ? null : storage[i].clone();
            after[i] = before[i] == null ? null : before[i].clone();
        }
        long earned = 0; int sold = 0;
        java.util.List<SalePortion> portions = new java.util.ArrayList<>();
        int limit = Math.max(1, maxAmount);
        for (int i = 0; i < after.length && sold < limit; i++) {
            ItemStack stack = after[i];
            if (stack == null || stack.getAmount() <= 0 || !matches.test(stack)) continue;
            long unit = sellUnitCents(table, stack);
            if (unit <= 0) continue;
            int take = Math.min(stack.getAmount(), limit - sold);
            earned = Money.addSaturated(earned, Money.multiplySaturated(unit, take));
            sold += take;
            ItemStack portion = stack.clone(); portion.setAmount(take);
            portions.add(new SalePortion(i, portion));
            if (take == stack.getAmount()) after[i] = null;
            else stack.setAmount(stack.getAmount() - take);
        }
        SellResult result = sold == 0 ? new SellResult(0, 0, "shop.nothing") : new SellResult(sold, earned, null);
        return new SalePlan(before, after, result, java.util.List.copyOf(portions));
    }

    private SellResult completeSale(Player player, SalePlan plan) {
        if (!plan.result().ok()) return plan.result();
        try {
            player.getInventory().setStorageContents(plan.after());
            economy.deposit(player.getUniqueId(), player.getName(), plan.result().earnedCents());
            return plan.result();
        } catch (RuntimeException error) {
            try { player.getInventory().setStorageContents(plan.before()); }
            catch (RuntimeException rollback) {
                error.addSuppressed(rollback);
                java.util.logging.Logger.getLogger(ShopService.class.getName()).log(
                        java.util.logging.Level.SEVERE, "Shop sale rollback requires administrator review", error);
                return new SellResult(0, 0, "shop.transaction-error");
            }
            java.util.logging.Logger.getLogger(ShopService.class.getName()).log(
                    java.util.logging.Level.WARNING, "Could not complete shop sale; restored inventory", error);
            return new SellResult(0, 0, "shop.transaction-error");
        }
    }

    /** A non-destructive quote for the selected material/variant and amount. */
    public SaleQuote quoteSell(Player player, Purchase purchase, int maxAmount) {
        PriceTable table = prices;
        if (purchase == null || purchase.unitPrice(table) <= 0 || table.sellCents(purchase.material()) <= 0)
            return new SaleQuote(new SellResult(0, 0, "shop.no-sell"), java.util.List.of());
        SalePlan plan = planSale(player, purchase::matches, maxAmount, table);
        return new SaleQuote(plan.result(), plan.portions());
    }

    public SellResult previewSell(Player player, Purchase purchase, int maxAmount) {
        return quoteSell(player, purchase, maxAmount).result();
    }

    /** Sell only matching items from storage; equipped armor and offhand are untouched. */
    public SellResult sell(Player player, Purchase purchase, int maxAmount) {
        return sell(player, purchase, maxAmount, null);
    }

    /** When a quote is supplied, never sell different items, quantity or payout. */
    public SellResult sell(Player player, Purchase purchase, int maxAmount, SaleQuote expected) {
        PriceTable table = prices;
        if (purchase == null || purchase.unitPrice(table) <= 0 || table.sellCents(purchase.material()) <= 0)
            return new SellResult(0, 0, expected == null ? "shop.no-sell" : "shop.sale-changed");
        SalePlan plan = planSale(player, purchase::matches, maxAmount, table);
        if (expected != null && !expected.matches(plan)) return new SellResult(0, 0, "shop.sale-changed");
        return completeSale(player, plan);
    }

    /** Legacy material-only entry point: sells matching variants from storage. */
    public SellResult sell(Player player, String materialName, int maxAmount) {
        PriceTable table = prices;
        PriceTable.Entry entry = table.get(materialName);
        if (entry == null || entry.sellCents() <= 0) return new SellResult(0, 0, "shop.no-sell");
        return completeSale(player, planSale(player, item -> item.getType().name().equals(materialName), maxAmount, table));
    }

    /** Explicit API for selling all eligible storage items; /sell itself uses the deposit tray. */
    public SellResult sellAll(Player player) {
        return completeSale(player, planSale(player, item -> true, Integer.MAX_VALUE, prices));
    }

    /** Sell only the explicitly deposited stacks; unsellable stacks stay untouched. */
    public SellResult sellDeposited(Player player, java.util.List<ItemStack> stacks) {
        long earned = 0; int sold = 0;
        java.util.List<ItemStack> accepted = new java.util.ArrayList<>();
        java.util.Set<ItemStack> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        PriceTable table = prices;
        for (ItemStack stack : stacks) {
            if (stack == null || !seen.add(stack)) continue;
            long unit = sellUnitCents(table, stack);
            if (unit <= 0) continue;
            earned = Money.addSaturated(earned, Money.multiplySaturated(unit, stack.getAmount()));
            sold += stack.getAmount(); accepted.add(stack);
        }
        if (sold == 0) return new SellResult(0, 0, "shop.nothing");
        try { economy.deposit(player.getUniqueId(), player.getName(), earned); }
        catch (RuntimeException error) {
            java.util.logging.Logger.getLogger(ShopService.class.getName()).log(
                    java.util.logging.Level.WARNING, "Could not credit deposited sale; items remain in the tray", error);
            return new SellResult(0, 0, "shop.transaction-error");
        }
        for (ItemStack stack : accepted) stack.setAmount(0);
        return new SellResult(sold, earned, null);
    }

    /** Money in the player's balance — used in GUI lore. */
    public java.math.BigInteger balance(Player player) {
        return economy.getBalanceExact(player.getUniqueId());
    }

    /** Formatted money, e.g. {@code $12.50}. */
    public String format(long cents) {
        return economy.format(cents);
    }
    public String format(java.math.BigInteger cents) {
        return economy.formatExact(cents);
    }
}
