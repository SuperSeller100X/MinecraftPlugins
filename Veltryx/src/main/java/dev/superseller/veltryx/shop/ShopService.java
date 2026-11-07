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
            return PriceTable.parse(Files.readString(shopFile));
        } catch (IOException | RuntimeException e) {
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
        PriceTable.Entry entry = prices.get(materialName);
        if (entry == null) {
            return new BuyResult(0, 0, "shop.no-price", Map.of());
        }
        amount = Math.max(1, Math.min(64, amount));
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return new BuyResult(0, 0, "shop.no-price", Map.of());
        }
        long unitPrice = entry.buyCents();
        long total = unitPrice * amount;
        if (!economy.has(player.getUniqueId(), total)) {
            return new BuyResult(0, 0, "shop.cannot-afford", Map.of(
                    "price", economy.format(total),
                    "balance", economy.format(economy.getBalance(player.getUniqueId()))));
        }
        ItemStack stack = new ItemStack(material, amount);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        int given = amount;
        for (ItemStack rest : leftover.values()) {
            given -= rest.getAmount();
        }
        if (given <= 0) {
            return new BuyResult(0, 0, "shop.no-space", Map.of());
        }
        long spent = unitPrice * given;
        economy.withdraw(player.getUniqueId(), player.getName(), spent);
        return new BuyResult(given, spent, null, Map.of());
    }

    /** Sells up to {@code maxAmount} of a material from the player's inventory. */
    public SellResult sell(Player player, String materialName, int maxAmount) {
        long unitSell = prices.sellCents(materialName);
        if (unitSell <= 0) {
            return new SellResult(0, 0, "shop.no-sell");
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return new SellResult(0, 0, "shop.no-sell");
        }
        ItemStack[] contents = player.getInventory().getContents();
        int removed = removeFrom(contents, material, Math.max(1, maxAmount), player);
        if (removed <= 0) {
            return new SellResult(0, 0, "shop.nothing");
        }
        long earned = unitSell * removed;
        economy.deposit(player.getUniqueId(), player.getName(), earned);
        return new SellResult(removed, earned, null);
    }

    /** Sells every sellable item in the inventory; returns the total earned. */
    public SellResult sellAll(Player player) {
        long earned = 0;
        int sold = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            long unitSell = prices.sellCents(stack.getType().name());
            if (unitSell <= 0) {
                continue;
            }
            int amount = stack.getAmount();
            earned += unitSell * amount;
            sold += amount;
            stack.setAmount(0);
        }
        if (sold <= 0) {
            return new SellResult(0, 0, "shop.nothing");
        }
        economy.deposit(player.getUniqueId(), player.getName(), earned);
        return new SellResult(sold, earned, null);
    }

    /** Removes up to {@code max} items of a type from the contents array. */
    private static int removeFrom(ItemStack[] contents, Material material, int max, Player player) {
        int removed = 0;
        for (int i = 0; i < contents.length && removed < max; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(stack.getAmount(), max - removed);
            removed += take;
            int left = stack.getAmount() - take;
            stack.setAmount(left);
            contents[i] = left > 0 ? stack : null;
            player.getInventory().setItem(i, contents[i]);
        }
        return removed;
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
