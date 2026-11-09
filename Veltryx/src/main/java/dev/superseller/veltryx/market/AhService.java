package dev.superseller.veltryx.market;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.AhListing;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Ids;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.SellNotices;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Fixed-total-price auctions, with the actual main-hand stack held in escrow. */
public final class AhService {
    private static final Logger LOG = Logger.getLogger(AhService.class.getName());

    public record Result(boolean ok, String templateKey, Map<String, String> values) {
        static Result ok(String key, Map<String, String> values) { return new Result(true, key, values); }
        static Result fail(String key, Map<String, String> values) { return new Result(false, key, values); }
    }

    private final DataStore store;
    private final EconomyService economy;
    private final Messages messages;
    private final VeltryxConfig config;

    public AhService(DataStore store, EconomyService economy, Messages messages, VeltryxConfig config) {
        this.store = store; this.economy = economy; this.messages = messages; this.config = config;
    }

    /** Lists exactly the whole main-hand stack for the specified total, not a per-item price. */
    public synchronized Result listHeldItem(Player seller, BigInteger totalCents) {
        ItemStack hand = seller.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR || hand.getAmount() <= 0)
            return Result.fail("ah.hold-item", Map.of());
        if (hand.getType().isLegacy() || !hand.getType().isItem())
            return Result.fail("ah.unknown-item", Map.of("item", hand.getType().name()));
        if (hand.getAmount() > 23040) return Result.fail("ah.bad-amount", Map.of());
        if (totalCents == null || totalCents.signum() <= 0) return Result.fail("ah.bad-price", Map.of());
        long now = System.currentTimeMillis();
        int active = 0;
        for (AhListing listing : store.allAhListings())
            if (listing.seller().equals(seller.getUniqueId()) && listing.isLiveAt(now)) active++;
        if (active >= config.maxListings())
            return Result.fail("ah.too-many", Map.of("max", String.valueOf(config.maxListings())));

        ItemStack snapshot = hand.clone();
        String data;
        try {
            data = Base64.getEncoder().encodeToString(snapshot.serializeAsBytes());
            ItemStack restored = ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
            if (restored.getAmount() != snapshot.getAmount() || !restored.isSimilar(snapshot))
                throw new IllegalStateException("Item serialization did not preserve the held stack");
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Could not safely serialize an auction item", ex);
            return Result.fail("ah.item-error", Map.of());
        }
        ItemStack current = seller.getInventory().getItemInMainHand();
        if (current == null || current.getAmount() != snapshot.getAmount() || !current.isSimilar(snapshot))
            return Result.fail("ah.hand-changed", Map.of());
        String id;
        do { id = Ids.next(); } while (find(id) != null);
        AhListing listing = new AhListing(id, seller.getUniqueId(), seller.getName(),
                snapshot.getType().name(), snapshot.getAmount(), totalCents, now,
                now + config.auctionHours() * 3_600_000L, false, data);
        // No material-wide inventory removal: only this exact hand slot is moved.
        seller.getInventory().setItemInMainHand(null);
        try { store.saveAhListing(listing); }
        catch (RuntimeException ex) {
            seller.getInventory().setItemInMainHand(snapshot);
            LOG.log(Level.SEVERE, "Could not save auction " + id + "; returned held item", ex);
            return Result.fail("ah.storage-error", Map.of());
        }
        return Result.ok("ah.listed", Map.of("id", id, "count", String.valueOf(snapshot.getAmount()),
                "item", displayName(snapshot.getType().name()), "money", Money.formatNumber(totalCents)));
    }

    public Result listHeldItem(Player seller, long totalCents) {
        return listHeldItem(seller, BigInteger.valueOf(totalCents));
    }

    /** Legacy entry point; never removes a matching stack elsewhere in the inventory. */
    public Result list(Player seller, String materialName, int amount, long totalCents) {
        Material material = Material.matchMaterial(materialName);
        if (material == Material.SPAWNER) return Result.fail("spawner.market-disabled", Map.of());
        if (material == null || material == Material.AIR) return Result.fail("ah.unknown-item", Map.of("item", materialName));
        ItemStack hand = seller.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != material || hand.getAmount() != amount)
            return Result.fail("ah.hold-item", Map.of());
        return listHeldItem(seller, BigInteger.valueOf(totalCents));
    }

    /** Decode new exact items; old material-only listings remain redeemable. Null means fail closed. */
    public ItemStack listingItem(AhListing listing) {
        try {
            Material material = Material.matchMaterial(listing.material());
            if (material == null || material == Material.AIR || !material.isItem()
                    || (material == Material.SPAWNER && listing.itemData() == null)) return null;
            ItemStack item = listing.itemData() == null
                    ? new ItemStack(material, listing.amount())
                    : ItemStack.deserializeBytes(Base64.getDecoder().decode(listing.itemData()));
            return item.getType() == material && item.getAmount() == listing.amount() ? item : null;
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Could not read auction item " + listing.id(), ex);
            return null;
        }
    }

    /** Buy once, rolling back ordinary save/payment failures and partial inventory insertion. */
    public synchronized Result buy(Player buyer, String listingId) {
        AhListing listing = find(listingId);
        if (listing == null || !listing.isLiveAt(System.currentTimeMillis())) return Result.fail("ah.gone", Map.of());
        if (listing.seller().equals(buyer.getUniqueId())) return Result.fail("ah.own-listing", Map.of());
        ItemStack item = listingItem(listing);
        if (item == null) return Result.fail("ah.item-error", Map.of());
        if (economy.getBalanceExact(buyer.getUniqueId()).compareTo(listing.priceExactCents()) < 0)
            return cannotAfford(buyer, listing.priceExactCents());
        ItemStack[] before = copyStorage(buyer);
        try {
            if (!buyer.getInventory().addItem(item.clone()).isEmpty()) {
                buyer.getInventory().setStorageContents(before);
                return Result.fail("shop.no-space", Map.of());
            }
            store.saveAhListing(listing.withClosed(true));
        } catch (RuntimeException ex) {
            buyer.getInventory().setStorageContents(before);
            LOG.log(Level.SEVERE, "Could not complete auction " + listing.id(), ex);
            return Result.fail("ah.storage-error", Map.of());
        }
        boolean transferred;
        try {
            transferred = economy.transferExact(buyer.getUniqueId(), buyer.getName(),
                    listing.seller(), listing.sellerName(), listing.priceExactCents());
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "Could not transfer auction payment " + listing.id(), ex);
            rollbackSale(buyer, before, listing);
            return Result.fail("ah.storage-error", Map.of());
        }
        if (!transferred) {
            return rollbackSale(buyer, before, listing)
                    ? cannotAfford(buyer, listing.priceExactCents()) : Result.fail("ah.storage-error", Map.of());
        }
        try {
            Player seller = Bukkit.getPlayer(listing.seller());
            if (seller != null) {
                BigInteger balance = economy.getBalanceExact(listing.seller());
                PlatformScheduler.runForPlayer(seller, () -> SellNotices.show(seller, messages, "ah.sold", Map.of(
                        "count", String.valueOf(listing.amount()), "item", displayName(listing.material()),
                        "balance", Money.formatCompact(balance)), listing.priceExactCents()));
            }
        } catch (RuntimeException ex) {
            // A failed notification cannot undo or turn a completed purchase into a retry.
            LOG.log(Level.WARNING, "Could not notify auction seller " + listing.id(), ex);
        }
        return Result.ok("ah.bought", Map.of("count", String.valueOf(listing.amount()),
                "item", displayName(listing.material()), "money", Money.formatNumber(listing.priceExactCents())));
    }

    private Result cannotAfford(Player buyer, BigInteger price) {
        return Result.fail("shop.cannot-afford", Map.of("price", Money.formatNumber(price),
                "balance", Money.formatNumber(economy.getBalanceExact(buyer.getUniqueId()))));
    }

    private boolean rollbackSale(Player buyer, ItemStack[] before, AhListing listing) {
        boolean reopened = true;
        try { store.saveAhListing(listing); }
        catch (RuntimeException ex) {
            reopened = false;
            LOG.log(Level.SEVERE, "Auction rollback requires administrator review: " + listing.id(), ex);
        }
        buyer.getInventory().setStorageContents(before);
        return reopened;
    }

    public synchronized Result reclaim(Player owner, String listingId) {
        AhListing listing = find(listingId);
        if (listing == null || listing.closed() || !listing.seller().equals(owner.getUniqueId()))
            return Result.fail("ah.gone", Map.of());
        ItemStack item = listingItem(listing);
        if (item == null) return Result.fail("ah.item-error", Map.of());
        ItemStack[] before = copyStorage(owner);
        try {
            if (!owner.getInventory().addItem(item.clone()).isEmpty()) {
                owner.getInventory().setStorageContents(before);
                return Result.fail("shop.no-space", Map.of());
            }
            store.saveAhListing(listing.withClosed(true));
        } catch (RuntimeException ex) {
            owner.getInventory().setStorageContents(before);
            LOG.log(Level.SEVERE, "Could not reclaim auction " + listing.id(), ex);
            return Result.fail("ah.storage-error", Map.of());
        }
        return Result.ok("ah.reclaimed", Map.of("count", String.valueOf(listing.amount()),
                "item", displayName(listing.material())));
    }

    private static ItemStack[] copyStorage(Player player) {
        ItemStack[] copy = player.getInventory().getStorageContents().clone();
        for (int i = 0; i < copy.length; i++) if (copy[i] != null) copy[i] = copy[i].clone();
        return copy;
    }

    public List<AhListing> liveListings(long nowMillis) {
        List<AhListing> out = new ArrayList<>();
        for (AhListing listing : store.allAhListings()) if (listing.isLiveAt(nowMillis)) out.add(listing);
        out.sort(Comparator.comparingLong(AhListing::listed).reversed()); return out;
    }
    public List<AhListing> listingsOf(java.util.UUID seller) {
        List<AhListing> out = new ArrayList<>();
        for (AhListing listing : store.allAhListings()) if (listing.seller().equals(seller) && !listing.closed()) out.add(listing);
        out.sort(Comparator.comparingLong(AhListing::listed).reversed()); return out;
    }
    private AhListing find(String id) {
        for (AhListing listing : store.allAhListings()) if (listing.id().equals(id)) return listing;
        return null;
    }

    /** Removes up to max plain items of a material for buy-order fulfillment. */
    static int removeFromInventory(Player player, Material material, int max) {
        int removed = 0;
        ItemStack plain = new ItemStack(material);
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && removed < max; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material || stack.getAmount() <= 0
                    || !stack.isSimilar(plain) || dev.superseller.veltryx.shop.ItemValue.isValued(stack)) continue;
            int take = Math.min(stack.getAmount(), max - removed);
            removed += take; int left = stack.getAmount() - take;
            stack.setAmount(left); contents[i] = left > 0 ? stack : null;
            player.getInventory().setItem(i, contents[i]);
        }
        return removed;
    }

    static String displayName(String materialName) {
        String[] parts = materialName.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
