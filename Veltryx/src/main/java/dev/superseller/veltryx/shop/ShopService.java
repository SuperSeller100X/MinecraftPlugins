package dev.superseller.veltryx.shop;

import dev.superseller.veltryx.economy.EconomyService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
        long unitPrice = purchase.unitPrice(prices);
        Material material = Material.matchMaterial(purchase.material());
        if (unitPrice <= 0 || material == null || !material.isItem())
            return new BuyResult(0, 0, "shop.no-price", Map.of());
        int amount = Math.max(1, Math.min(64, requested));
        if (unitPrice > Long.MAX_VALUE / amount) return new BuyResult(0, 0, "shop.no-price", Map.of());
        long total = unitPrice * amount;
        // Withdraw before adding items so concurrent balance changes cannot grant free goods.
        if (!economy.withdraw(player.getUniqueId(), player.getName(), total)) {
            return new BuyResult(0, 0, "shop.cannot-afford", Map.of(
                    "price", dev.superseller.veltryx.util.Money.formatCompact(total),
                    "balance", dev.superseller.veltryx.util.Money.formatCompact(economy.getBalance(player.getUniqueId()))));
        }
        ItemStack stack;
        try {
            stack = createPurchaseItem(player, material, purchase, amount);
        } catch (RuntimeException error) {
            economy.deposit(player.getUniqueId(), player.getName(), total);
            java.util.logging.Logger.getLogger(ShopService.class.getName()).log(
                    java.util.logging.Level.WARNING, "Could not create purchased " + purchase.material(), error);
            return new BuyResult(0, 0, ExplorerMaps.namedMap(purchase.material())
                    ? "shop.map-unavailable" : "shop.item-unavailable", Map.of());
        }
        if (stack == null) {
            economy.deposit(player.getUniqueId(), player.getName(), total);
            return new BuyResult(0, 0, ExplorerMaps.namedMap(purchase.material())
                    ? "shop.map-unavailable" : "shop.item-unavailable", Map.of());
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        int given = amount;
        for (ItemStack rest : leftover.values()) given -= rest.getAmount();
        given = Math.max(0, Math.min(amount, given));
        if (given < amount) economy.deposit(player.getUniqueId(), player.getName(), unitPrice * (amount - given));
        if (given == 0) return new BuyResult(0, 0, "shop.no-space", Map.of());
        return new BuyResult(given, unitPrice * given, null, Map.of());
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
                book.addStoredEnchant(enchantment, purchase.level(), false);
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
        if (meta != null) stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Sells up to {@code maxAmount} of a material from the player's inventory.
     * Enchanted items sell for more (+12% per enchant level), damaged items
     * for less (scaled by the remaining durability) - see {@link ItemValue}.
     */
    public SellResult sell(Player player, String materialName, int maxAmount) {
        PriceTable.Entry entry = prices.get(materialName);
        if (entry == null || entry.sellCents() <= 0) {
            return new SellResult(0, 0, "shop.no-sell");
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return new SellResult(0, 0, "shop.no-sell");
        }
        ItemStack[] contents = player.getInventory().getContents();
        long earned = 0;
        int sold = 0;
        for (int i = 0; i < contents.length && sold < Math.max(1, maxAmount); i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            long unit = ItemValue.valueCents(entry.sellCents(), entry.buyCents(), stack);
            if (unit <= 0) continue;
            int take = Math.min(stack.getAmount(), Math.max(1, maxAmount) - sold);
            earned = dev.superseller.veltryx.util.Money.addSaturated(earned,
                    dev.superseller.veltryx.util.Money.multiplySaturated(unit, take));
            sold += take;
            int left = stack.getAmount() - take;
            stack.setAmount(left);
            if (left <= 0) {
                player.getInventory().setItem(i, new ItemStack(Material.AIR, 1));
            }
        }
        if (sold <= 0) {
            return new SellResult(0, 0, "shop.nothing");
        }
        economy.deposit(player.getUniqueId(), player.getName(), earned);
        return new SellResult(sold, earned, null);
    }

    /**
     * Sells every sellable item in the inventory; enchanted and damaged
     * items are paid out at their {@link ItemValue} worth.
     */
    public SellResult sellAll(Player player) {
        long earned = 0;
        int sold = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            PriceTable.Entry entry = prices.get(stack.getType().name());
            if (entry == null || entry.sellCents() <= 0) {
                continue;
            }
            long unit = ItemValue.valueCents(entry.sellCents(), entry.buyCents(), stack);
            if (unit <= 0) continue;
            int amount = stack.getAmount();
            earned = dev.superseller.veltryx.util.Money.addSaturated(earned,
                    dev.superseller.veltryx.util.Money.multiplySaturated(unit, amount));
            sold += amount;
            stack.setAmount(0);
        }
        if (sold <= 0) {
            return new SellResult(0, 0, "shop.nothing");
        }
        economy.deposit(player.getUniqueId(), player.getName(), earned);
        return new SellResult(sold, earned, null);
    }


    /** Money in the player's balance — used in GUI lore. */
    public long balance(Player player) {
        return economy.getBalance(player.getUniqueId());
    }

    /** Formatted money, e.g. {@code $12.50}. */
    public String format(long cents) {
        return economy.format(cents);
    }
}
