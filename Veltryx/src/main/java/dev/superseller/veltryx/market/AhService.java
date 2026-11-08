package dev.superseller.veltryx.market;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.AhListing;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Ids;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.SellNotices;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The player auction house: list items for a fixed price per item, other
 * players buy them from the {@code /ah} GUI. Money goes straight from buyer
 * to seller — the server never creates money here.
 */
public final class AhService {

    /** Hard sanity cap for the price of one item (prevents fat-finger disasters). */
    public static final long MAX_PRICE_EACH = 100_000_00L;

    /** Outcome of an auction action. */
    public record Result(boolean ok, String templateKey, Map<String, String> values) {
        static Result ok(String key, Map<String, String> values) {
            return new Result(true, key, values);
        }

        static Result fail(String key, Map<String, String> values) {
            return new Result(false, key, values);
        }
    }

    private final DataStore store;
    private final EconomyService economy;
    private final Messages messages;
    private final VeltryxConfig config;

    public AhService(DataStore store, EconomyService economy, Messages messages, VeltryxConfig config) {
        this.store = store;
        this.economy = economy;
        this.messages = messages;
        this.config = config;
    }

    /**
     * Lists the whole stack currently held in the main hand.
     *
     * @param priceEachCents price per item (the listing total is price × amount)
     */
    public Result listHeldItem(Player seller, long priceEachCents) {
        ItemStack held = seller.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR || held.getAmount() <= 0) {
            return Result.fail("ah.hold-item", Map.of());
        }
        return list(seller, held.getType().name(), held.getAmount(), priceEachCents);
    }

    /** Lists {@code amount} items of a material taken from the seller's inventory. */
    public Result list(Player seller, String materialName, int amount, long priceEachCents) {
        Material material = Material.matchMaterial(materialName);
        if (material == null || material == Material.AIR) {
            return Result.fail("ah.unknown-item", Map.of("item", materialName));
        }
        // Listings store only a material, so accepting a custom spawner would
        // strip its identity and permanently orphan its stored loot.
        if (material == Material.SPAWNER) return Result.fail("spawner.market-disabled", Map.of());
        if (amount < 1 || amount > 23040) {
            return Result.fail("ah.bad-amount", Map.of());
        }
        if (priceEachCents < 1 || priceEachCents > MAX_PRICE_EACH) {
            return Result.fail("ah.bad-price", Map.of(
                    "max", Money.formatCompact(MAX_PRICE_EACH)));
        }
        if (hasValuedStack(seller, material)) {
            return Result.fail("ah.no-valued", Map.of());
        }
        long now = System.currentTimeMillis();
        int active = 0;
        for (AhListing listing : store.allAhListings()) {
            if (listing.seller().equals(seller.getUniqueId()) && listing.isLiveAt(now)) {
                active++;
            }
        }
        if (active >= config.maxListings()) {
            return Result.fail("ah.too-many", Map.of("max", String.valueOf(config.maxListings())));
        }
        int removed = removeFromInventory(seller, material, amount);
        if (removed < amount) {
            // put back what was taken and abort
            seller.getInventory().addItem(new ItemStack(material, removed));
            return Result.fail("ah.not-enough", Map.of(
                    "item", displayName(materialName), "count", String.valueOf(removed)));
        }
        long total = priceEachCents * amount;
        AhListing listing = new AhListing(Ids.next(), seller.getUniqueId(), seller.getName(),
                materialName, amount, total, now,
                now + config.auctionHours() * 3_600_000L, false);
        store.saveAhListing(listing);
        return Result.ok("ah.listed", Map.of(
                "id", listing.id(),
                "count", String.valueOf(amount),
                "item", displayName(materialName),
                "money", Money.formatCompact(total)));
    }

    /** True when any stack of the material is enchanted or damaged (valued items). */
    private boolean hasValuedStack(Player seller, Material material) {
        for (ItemStack stack : seller.getInventory().getContents()) {
            if (stack != null && stack.getType() == material
                    && dev.superseller.veltryx.shop.ItemValue.isValued(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Buys one listing. */
    public Result buy(Player buyer, String listingId) {
        long now = System.currentTimeMillis();
        AhListing listing = find(listingId);
        if (listing == null || !listing.isLiveAt(now)) {
            return Result.fail("ah.gone", Map.of());
        }
        if (listing.seller().equals(buyer.getUniqueId())) {
            return Result.fail("ah.own-listing", Map.of());
        }
        if (!economy.withdraw(buyer.getUniqueId(), buyer.getName(), listing.priceCents())) {
            return Result.fail("shop.cannot-afford", Map.of(
                    "price", Money.formatCompact(listing.priceCents()),
                    "balance", Money.formatCompact(economy.getBalance(buyer.getUniqueId()))));
        }
        Material material = Material.matchMaterial(listing.material());
        if (material == null) {
            economy.deposit(buyer.getUniqueId(), buyer.getName(), listing.priceCents());
            return Result.fail("ah.gone", Map.of());
        }
        HashMap<Integer, ItemStack> leftover =
                buyer.getInventory().addItem(new ItemStack(material, listing.amount()));
        if (!leftover.isEmpty()) {
            economy.deposit(buyer.getUniqueId(), buyer.getName(), listing.priceCents());
            return Result.fail("shop.no-space", Map.of());
        }
        store.saveAhListing(new AhListing(listing.id(), listing.seller(), listing.sellerName(),
                listing.material(), listing.amount(), listing.priceCents(), listing.listed(),
                listing.expires(), true));
        // pay the seller (works offline)
        long sellerBalance = economy.deposit(listing.seller(), listing.sellerName(), listing.priceCents());
        Player seller = Bukkit.getPlayer(listing.seller());
        if (seller != null) {
            SellNotices.show(seller, messages, "ah.sold", Map.of(
                    "count", String.valueOf(listing.amount()),
                    "item", displayName(listing.material()),
                    "balance", Money.formatCompact(sellerBalance)), listing.priceCents());
        }
        return Result.ok("ah.bought", Map.of(
                "count", String.valueOf(listing.amount()),
                "item", displayName(listing.material()),
                "money", Money.formatCompact(listing.priceCents())));
    }

    /** Cancels / reclaims a listing (owner only). */
    public Result reclaim(Player owner, String listingId) {
        AhListing listing = find(listingId);
        if (listing == null || listing.closed() || !listing.seller().equals(owner.getUniqueId())) {
            return Result.fail("ah.gone", Map.of());
        }
        Material material = Material.matchMaterial(listing.material());
        if (material == null) {
            return Result.fail("ah.gone", Map.of());
        }
        HashMap<Integer, ItemStack> leftover =
                owner.getInventory().addItem(new ItemStack(material, listing.amount()));
        if (!leftover.isEmpty()) {
            return Result.fail("shop.no-space", Map.of());
        }
        store.saveAhListing(new AhListing(listing.id(), listing.seller(), listing.sellerName(),
                listing.material(), listing.amount(), listing.priceCents(), listing.listed(),
                listing.expires(), true));
        return Result.ok("ah.reclaimed", Map.of(
                "count", String.valueOf(listing.amount()),
                "item", displayName(listing.material())));
    }

    /** Live listings, newest first. */
    public List<AhListing> liveListings(long nowMillis) {
        List<AhListing> out = new ArrayList<>();
        for (AhListing listing : store.allAhListings()) {
            if (listing.isLiveAt(nowMillis)) {
                out.add(listing);
            }
        }
        out.sort(Comparator.comparingLong(AhListing::listed).reversed());
        return out;
    }

    /** One player's listings (live or expired-but-unclosed), newest first. */
    public List<AhListing> listingsOf(java.util.UUID seller) {
        List<AhListing> out = new ArrayList<>();
        for (AhListing listing : store.allAhListings()) {
            if (listing.seller().equals(seller) && !listing.closed()) {
                out.add(listing);
            }
        }
        out.sort(Comparator.comparingLong(AhListing::listed).reversed());
        return out;
    }

    private AhListing find(String id) {
        for (AhListing listing : store.allAhListings()) {
            if (listing.id().equals(id)) {
                return listing;
            }
        }
        return null;
    }

    /** Removes up to {@code max} items of a type from the player's inventory. */
    static int removeFromInventory(Player player, Material material, int max) {
        int removed = 0;
        ItemStack[] contents = player.getInventory().getContents();
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

    /** Short display name of a material ("IRON_INGOT" -> "Iron Ingot"). */
    static String displayName(String materialName) {
        String[] parts = materialName.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
