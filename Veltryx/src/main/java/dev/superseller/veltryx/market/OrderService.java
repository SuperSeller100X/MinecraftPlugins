package dev.superseller.veltryx.market;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.BuyOrder;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Ids;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.SellNotices;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Player buy orders ({@code /order}, {@code /orders}): "I want 128 iron
 * ingots for $1.10 each". The full price is escrowed up front; sellers fill
 * the order from the {@code /orders} GUI and are paid instantly. Unfilled
 * remainders are refunded on cancel or expiry — the server never creates money.
 */
public final class OrderService {

    /** Outcome of an order action. */
    public record Result(boolean ok, String templateKey, Map<String, String> values) {
        static Result ok(String key, Map<String, String> values) {
            return new Result(true, key, values);
        }

        static Result fail(String key, Map<String, String> values) {
            return new Result(false, key, values);
        }
    }

    private static final Logger LOG = Logger.getLogger(OrderService.class.getName());
    private final DataStore store;
    private final EconomyService economy;
    private final Messages messages;
    private final VeltryxConfig config;

    public OrderService(DataStore store, EconomyService economy, Messages messages, VeltryxConfig config) {
        this.store = store;
        this.economy = economy;
        this.messages = messages;
        this.config = config;
    }

    /** Creates a buy order, escrowing the full price. */
    public synchronized Result create(Player buyer, String materialName, int amount, long priceEachCents) {
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isLegacy() || !material.isItem() || material == Material.AIR) {
            return Result.fail("ah.unknown-item", Map.of("item", materialName));
        }
        // Buy orders carry a material name, not a custom spawner's identity.
        if (material == Material.SPAWNER) return Result.fail("spawner.market-disabled", Map.of());
        if (amount < 1 || amount > 23040) {
            return Result.fail("order.bad-amount", Map.of());
        }
        if (priceEachCents < 1 || priceEachCents > AhService.MAX_PRICE_EACH) {
            return Result.fail("order.bad-price", Map.of("max", Money.formatCompact(AhService.MAX_PRICE_EACH)));
        }
        long now = System.currentTimeMillis();
        int open = 0;
        for (BuyOrder order : store.allBuyOrders()) {
            if (order.buyer().equals(buyer.getUniqueId()) && order.isLiveAt(now)) {
                open++;
            }
        }
        if (open >= config.maxOrders()) {
            return Result.fail("order.too-many", Map.of("max", String.valueOf(config.maxOrders())));
        }
        // IDs are short for players, so explicitly avoid overwriting a historical order.
        String orderId;
        do { orderId = Ids.next(); } while (find(orderId) != null);
        long total = priceEachCents * amount;
        if (!economy.withdraw(buyer.getUniqueId(), buyer.getName(), total)) {
            return Result.fail("shop.cannot-afford", Map.of(
                    "price", Money.formatCompact(total),
                    "balance", Money.formatCompact(economy.getBalanceExact(buyer.getUniqueId()))));
        }
        BuyOrder order = new BuyOrder(orderId, buyer.getUniqueId(), buyer.getName(),
                material.name(), amount, 0, priceEachCents, now,
                now + config.orderHours() * 3_600_000L, false);
        try { store.saveBuyOrder(order); }
        catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Could not persist order " + order.id() + "; refunding escrow", ex);
            try { economy.deposit(buyer.getUniqueId(), buyer.getName(), total); }
            catch (RuntimeException refund) { LOG.log(Level.SEVERE, "Order escrow refund requires administrator review: " + order.id(), refund); }
            return Result.fail("order.storage-error", Map.of());
        }
        return Result.ok("order.created", Map.of(
                "id", order.id(),
                "count", String.valueOf(amount),
                "item", AhService.displayName(materialName),
                "money", Money.formatCompact(priceEachCents),
                "total", Money.formatCompact(total)));
    }

    /**
     * Fills up to {@code maxAmount} of an order from the seller's inventory.
     */
    public synchronized Result fill(Player seller, String orderId, int maxAmount) {
        long now = System.currentTimeMillis();
        BuyOrder order = find(orderId);
        if (order == null || !order.isLiveAt(now)) {
            return Result.fail("order.gone", Map.of());
        }
        if (order.buyer().equals(seller.getUniqueId())) {
            return Result.fail("order.own", Map.of());
        }
        Material material = Material.matchMaterial(order.material());
        if (material == null) {
            return Result.fail("order.gone", Map.of());
        }
        // Also protect spawners from orders created before this guard existed.
        if (material == Material.SPAWNER) return Result.fail("spawner.market-disabled", Map.of());
        if (hasValuedStack(seller, material)) return Result.fail("order.no-valued", Map.of());
        if (hasNonPlainStack(seller, material)) return Result.fail("order.no-metadata", Map.of());
        int take = Math.min(order.remaining(), Math.max(1, maxAmount));
        ItemStack[] before = copyStorage(seller);
        int removed = AhService.removeFromInventory(seller, material, take);
        if (removed <= 0) {
            return Result.fail("ah.not-enough", Map.of(
                    "item", AhService.displayName(order.material()), "count", "0"));
        }
        long payout = order.priceEachCents() * removed;
        int filled = order.filled() + removed;
        BuyOrder updated = new BuyOrder(order.id(), order.buyer(), order.buyerName(),
                order.material(), order.amount(), filled, order.priceEachCents(),
                order.created(), order.expires(), filled >= order.amount(), order.collected());
        try { store.saveBuyOrder(updated); }
        catch (RuntimeException ex) {
            seller.getInventory().setStorageContents(before);
            LOG.log(Level.SEVERE, "Could not save order fill " + order.id(), ex);
            return Result.fail("order.storage-error", Map.of());
        }
        java.math.BigInteger balance;
        try { balance = economy.depositExact(seller.getUniqueId(), seller.getName(), java.math.BigInteger.valueOf(payout)); }
        catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Could not pay order seller; rolling back " + order.id(), ex);
            try { store.saveBuyOrder(order); seller.getInventory().setStorageContents(before); }
            catch (RuntimeException rollback) {
                LOG.log(Level.SEVERE, "Order fill requires administrator review: " + order.id(), rollback);
            }
            return Result.fail("order.storage-error", Map.of());
        }
        SellNotices.show(seller, messages, "order.filled-seller", Map.of(
                "count", String.valueOf(removed),
                "item", AhService.displayName(order.material()),
                "balance", Money.formatCompact(balance)), payout);
        Player buyer = Bukkit.getPlayer(order.buyer());
        if (buyer != null) {
            String sellerName = seller.getName();
            PlatformScheduler.runForPlayer(buyer, () -> messages.send(buyer, "order.filled-buyer", Map.of(
                    "count", String.valueOf(removed),
                    "item", AhService.displayName(order.material()),
                    "player", sellerName,
                    "money", Money.formatCompact(payout),
                    "left", String.valueOf(Math.max(0, order.amount() - filled)))));
        }
        // the seller already got the payout notice via SellNotices (action bar
        // + chat), so the success template key is informational only
        return Result.ok("order.filled-seller", Map.of(
                "count", String.valueOf(removed),
                "item", AhService.displayName(order.material()),
                "money", Money.formatCompact(payout),
                "balance", Money.formatCompact(balance)));
    }

    /** Cancels an order (buyer only) and refunds the unfilled remainder. */
    public synchronized Result cancel(Player player, String orderId) {
        BuyOrder order = find(orderId);
        if (order == null || order.closed() || !order.buyer().equals(player.getUniqueId())) {
            return Result.fail("order.gone", Map.of());
        }
        return close(order, "order.cancelled", player);
    }

    /** Closes every expired open order with a refund (run lazily on browse). */
    public synchronized void sweepExpired(long nowMillis) {
        for (BuyOrder order : store.allBuyOrders()) {
            if (!order.closed() && order.expires() <= nowMillis) {
                close(order, null, null);
            }
        }
    }

    private Result close(BuyOrder order, String templateKey, Player notify) {
        int left = order.remaining();
        long refund = order.priceEachCents() * left;
        BuyOrder closed = new BuyOrder(order.id(), order.buyer(), order.buyerName(),
                order.material(), order.amount(), order.filled(), order.priceEachCents(),
                order.created(), order.expires(), true, order.collected());
        try { store.saveBuyOrder(closed); }
        catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Could not close order " + order.id(), ex);
            return Result.fail("order.storage-error", Map.of());
        }
        java.math.BigInteger balance;
        try { balance = economy.depositExact(order.buyer(), order.buyerName(), java.math.BigInteger.valueOf(refund)); }
        catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Could not refund order " + order.id(), ex);
            try { store.saveBuyOrder(order); }
            catch (RuntimeException rollback) { LOG.log(Level.SEVERE, "Order refund requires administrator review: " + order.id(), rollback); }
            return Result.fail("order.storage-error", Map.of());
        }
        if (notify != null && templateKey != null) {
            return Result.ok(templateKey, Map.of(
                    "count", String.valueOf(left),
                    "item", AhService.displayName(order.material()),
                    "money", Money.formatCompact(refund),
                    "balance", Money.formatCompact(balance)));
        }
        if (templateKey == null) {
            Player buyer = Bukkit.getPlayer(order.buyer());
            if (buyer != null) {
                PlatformScheduler.runForPlayer(buyer, () -> messages.send(buyer, "order.expired", Map.of(
                        "count", String.valueOf(left),
                        "item", AhService.displayName(order.material()),
                        "money", Money.formatCompact(refund))));
            }
        }
        return Result.ok("order.cancelled", Map.of());
    }

    /** Open orders anyone can fill, newest first. */
    public List<BuyOrder> openOrders(long nowMillis) {
        List<BuyOrder> out = new ArrayList<>();
        for (BuyOrder order : store.allBuyOrders()) {
            if (order.isLiveAt(nowMillis)) {
                out.add(order);
            }
        }
        out.sort(Comparator.comparingLong(BuyOrder::created).reversed());
        return out;
    }

    /** One player's open orders, newest first. */
    public List<BuyOrder> ordersOf(UUID buyer) {
        List<BuyOrder> out = new ArrayList<>();
        for (BuyOrder order : store.allBuyOrders()) {
            if (order.buyer().equals(buyer) && (!order.closed() || order.claimable() > 0)) {
                out.add(order);
            }
        }
        out.sort(Comparator.comparingLong(BuyOrder::created).reversed());
        return out;
    }

    private BuyOrder find(String id) {
        for (BuyOrder order : store.allBuyOrders()) {
            if (order.id().equals(id)) {
                return order;
            }
        }
        return null;
    }

    /** How many items of the material the player holds. */
    public static int heldCount(Player player, String materialName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private boolean hasValuedStack(Player seller, Material material) {
        for (ItemStack stack : seller.getInventory().getStorageContents())
            if (stack != null && stack.getType() == material && dev.superseller.veltryx.shop.ItemValue.isValued(stack)) return true;
        return false;
    }

    /** Refuse metadata-bearing stacks: an order stores only the vanilla material. */
    private boolean hasNonPlainStack(Player seller, Material material) {
        ItemStack plain = new ItemStack(material);
        for (ItemStack stack : seller.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material && !stack.isSimilar(plain)) return true;
        }
        return false;
    }

    private static ItemStack[] copyStorage(Player player) {
        ItemStack[] copy = player.getInventory().getStorageContents().clone();
        for (int i=0; i<copy.length; i++) if (copy[i] != null) copy[i] = copy[i].clone();
        return copy;
    }

    /** Claim supplied items, including on a closed or cancelled order. */
    public synchronized Result claim(Player buyer, String orderId) {
        BuyOrder order = find(orderId);
        if (order == null || !order.buyer().equals(buyer.getUniqueId())) return Result.fail("order.gone", Map.of());
        if (order.claimable() <= 0) return Result.fail("order.nothing-to-claim", Map.of());
        Material material = Material.matchMaterial(order.material());
        if (material == null || material.isLegacy() || !material.isItem() || material == Material.SPAWNER)
            return Result.fail("order.gone", Map.of());
        ItemStack[] before = copyStorage(buyer);
        int left = order.claimable();
        int maxStack = Math.max(1, new ItemStack(material).getMaxStackSize());
        try {
            while (left > 0) {
                int amount = Math.min(left, maxStack);
                Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(new ItemStack(material, amount));
                int notAdded = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                left -= amount - notAdded;
                if (notAdded > 0) break;
            }
            int claimed = order.claimable() - left;
            if (claimed == 0) return Result.fail("shop.no-space", Map.of());
            BuyOrder updated = new BuyOrder(order.id(), order.buyer(), order.buyerName(), order.material(),
                    order.amount(), order.filled(), order.priceEachCents(), order.created(), order.expires(),
                    order.closed(), order.collected() + claimed);
            store.saveBuyOrder(updated);
            return Result.ok("order.claimed", Map.of("count", String.valueOf(claimed),
                    "item", AhService.displayName(order.material()), "left", String.valueOf(left)));
        } catch (RuntimeException ex) {
            buyer.getInventory().setStorageContents(before);
            LOG.log(Level.SEVERE, "Could not claim order " + order.id(), ex);
            return Result.fail("order.storage-error", Map.of());
        }
    }
}
