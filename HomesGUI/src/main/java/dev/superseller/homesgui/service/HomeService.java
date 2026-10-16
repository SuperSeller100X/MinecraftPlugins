package dev.superseller.homesgui.service;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import dev.superseller.homesgui.HomesGUIPlugin;
import dev.superseller.homesgui.config.HomesSettings;
import dev.superseller.homesgui.economy.EconomyHook;
import dev.superseller.homesgui.model.HomeLocation;
import dev.superseller.homesgui.model.PlayerHomes;
import dev.superseller.homesgui.pricing.HomePriceCalculator;
import dev.superseller.homesgui.scheduler.PlatformScheduler;
import dev.superseller.homesgui.storage.HomesStorage;
import dev.superseller.homesgui.storage.HomesGUIStorageLimits;
import dev.superseller.homesgui.util.SoundPlayer;

/** Coordinates home management, pricing, economy transactions, and teleports. */
public final class HomeService {

    private final HomesGUIPlugin plugin;
    private final HomesStorage storage;
    private final EconomyHook economy;

    public HomeService(HomesGUIPlugin plugin, HomesStorage storage, EconomyHook economy) {
        this.plugin = plugin;
        this.storage = storage;
        this.economy = economy;
    }

    public PlayerHomes profile(UUID owner) {
        return storage.profile(owner);
    }

    public int availableSlots(Player player) {
        return availableSlots(profile(player.getUniqueId()), permissionBonus(player));
    }

    /** Capacity without online permission bonuses, useful for offline admin views. */
    public int availableSlots(UUID owner) {
        return availableSlots(profile(owner), 0);
    }

    private int availableSlots(PlayerHomes data, int permissionBonus) {
        HomesSettings.Homes homes = plugin.settings().homes();
        long total = (long) homes.freeSlots() + data.purchasedSlots() + data.bonusSlots() + permissionBonus;
        total = Math.clamp(total, 0L, HomesSettings.ABSOLUTE_MAX_HOMES);
        if (homes.maxTotal() >= 0) {
            total = Math.min(total, homes.maxTotal());
        }
        return (int) total;
    }

    public boolean canUseSlot(Player player, int slot) {
        if (slot < 1 || slot > HomesGUIStorageLimits.MAX_SLOT) {
            return false;
        }
        PlayerHomes data = profile(player.getUniqueId());
        return slot <= availableSlots(player) || data.hasHome(slot);
    }

    /** Returns the first empty unlocked slot, or {@code -1} when all slots are occupied. */
    public int firstEmptySlot(Player player) {
        PlayerHomes data = profile(player.getUniqueId());
        int available = availableSlots(player);
        for (int slot = 1; slot <= available; slot++) {
            if (!data.hasHome(slot)) {
                return slot;
            }
        }
        return -1;
    }

    public boolean setHome(Player player, int slot) {
        if (!plugin.settings().homes().allowSetting()) {
            plugin.messages().send(player, "set-disabled");
            return false;
        }
        if (slot < 1 || slot > HomesGUIStorageLimits.MAX_SLOT) {
            plugin.messages().send(player, "invalid-slot");
            return false;
        }
        int available = availableSlots(player);
        if (slot > available) {
            plugin.messages().send(player, "slot-too-high", "%slot%", Integer.toString(slot),
                    "%available%", Integer.toString(available));
            return false;
        }
        HomeLocation destination;
        try {
            destination = HomeLocation.from(player.getLocation());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read a home location for " + player.getUniqueId(), exception);
            plugin.messages().send(player, "set-disabled");
            return false;
        }
        PlayerHomes data = profile(player.getUniqueId());
        boolean replacing;
        synchronized (data) {
            replacing = data.hasHome(slot);
            data.putHome(slot, destination);
        }
        storage.markDirty();
        plugin.messages().send(player, replacing ? "set-updated" : "set-created",
                "%slot%", Integer.toString(slot), "%world%", destination.world(),
                "%x%", coordinate(destination.x()), "%y%", coordinate(destination.y()),
                "%z%", coordinate(destination.z()));
        SoundPlayer.play(plugin, player, "set");
        return true;
    }

    public boolean deleteHome(Player player, int slot) {
        if (!plugin.settings().homes().allowDeleting()) {
            plugin.messages().send(player, "delete-disabled");
            return false;
        }
        if (slot < 1 || slot > HomesGUIStorageLimits.MAX_SLOT) {
            plugin.messages().send(player, "invalid-slot");
            return false;
        }
        boolean removed = removeHome(player.getUniqueId(), slot);
        if (!removed) {
            plugin.messages().send(player, "slot-empty", "%slot%", Integer.toString(slot));
            return false;
        }
        plugin.messages().send(player, "delete-success", "%slot%", Integer.toString(slot));
        SoundPlayer.play(plugin, player, "delete");
        return true;
    }

    /** Removes a location only; unlocked paid/admin slots remain untouched. */
    public boolean removeHome(UUID owner, int slot) {
        PlayerHomes data = profile(owner);
        synchronized (data) {
            if (data.removeHome(slot) == null) {
                return false;
            }
        }
        storage.markDirty();
        return true;
    }

    public void teleportHome(Player viewer, int slot) {
        teleportHome(viewer, viewer.getUniqueId(), slot);
    }

    /** Teleports a player to a stored destination; caller performs the permission check. */
    public void teleportHome(Player viewer, UUID homeOwner, int slot) {
        PlayerHomes data = profile(homeOwner);
        HomeLocation saved = data.home(slot);
        if (saved == null) {
            plugin.messages().send(viewer, "slot-empty", "%slot%", Integer.toString(slot));
            SoundPlayer.play(plugin, viewer, "failure");
            return;
        }
        World world = plugin.getServer().getWorld(saved.world());
        if (world == null) {
            plugin.messages().send(viewer, "world-unavailable", "%world%", saved.world());
            SoundPlayer.play(plugin, viewer, "failure");
            return;
        }
        Location target = saved.toLocation(world);
        plugin.messages().send(viewer, "teleport-started", "%slot%", Integer.toString(slot));
        SoundPlayer.play(plugin, viewer, "teleport");
        try {
            viewer.teleportAsync(target).whenComplete((success, error) ->
                    PlatformScheduler.runForPlayer(plugin, viewer, () -> {
                        if (error != null || !Boolean.TRUE.equals(success)) {
                            plugin.messages().send(viewer, "teleport-failed", "%slot%", Integer.toString(slot));
                            SoundPlayer.play(plugin, viewer, "failure");
                            if (error != null) {
                                plugin.getLogger().log(Level.WARNING, "Teleport to home " + slot + " failed.", error);
                            }
                        } else {
                            plugin.messages().send(viewer, "teleport-success", "%slot%", Integer.toString(slot));
                        }
                    }));
        } catch (RuntimeException exception) {
            plugin.messages().send(viewer, "teleport-failed", "%slot%", Integer.toString(slot));
            plugin.getLogger().log(Level.WARNING, "Could not start teleport to home " + slot + ".", exception);
        }
    }

    /** Charges once, then grants all requested paid slots; failed transactions grant none. */
    public PurchaseResult purchase(Player player, int count) {
        HomesSettings settings = plugin.settings();
        HomesSettings.Homes homeSettings = settings.homes();
        PlayerHomes data = profile(player.getUniqueId());
        if (!homeSettings.allowPurchases()) {
            plugin.messages().send(player, "buy-disabled");
            SoundPlayer.play(plugin, player, "failure");
            return new PurchaseResult(PurchaseStatus.DISABLED, 0, 0.0, availableSlots(player));
        }
        if (count < 1 || count > homeSettings.maxBulkPurchase()) {
            plugin.messages().send(player, "buy-invalid-count", "%max%", Integer.toString(homeSettings.maxBulkPurchase()));
            SoundPlayer.play(plugin, player, "failure");
            return new PurchaseResult(PurchaseStatus.INVALID_COUNT, 0, 0.0, availableSlots(player));
        }

        synchronized (data) {
            int currentCapacity = availableSlots(data, permissionBonus(player));
            long resultingCapacity = (long) currentCapacity + count;
            int configuredMaximum = homeSettings.maxTotal() < 0
                    ? HomesSettings.ABSOLUTE_MAX_HOMES : homeSettings.maxTotal();
            if (resultingCapacity > configuredMaximum || resultingCapacity > HomesSettings.ABSOLUTE_MAX_HOMES) {
                plugin.messages().send(player, "buy-limit", "%max%", Integer.toString(configuredMaximum));
                SoundPlayer.play(plugin, player, "failure");
                return new PurchaseResult(PurchaseStatus.LIMIT, 0, 0.0, currentCapacity);
            }

            HomePriceCalculator calculator = calculator(settings);
            double total = calculator.total(data.purchasedSlots(), count);
            if (total > 0.0 && (!settings.economy().enabled() || !economy.available())) {
                if (settings.economy().freeIfUnavailable()) {
                    total = 0.0;
                } else {
                    plugin.messages().send(player, "buy-economy-unavailable");
                    SoundPlayer.play(plugin, player, "failure");
                    return new PurchaseResult(PurchaseStatus.NO_ECONOMY, 0, 0.0, currentCapacity);
                }
            }

            if (total > 0.0) {
                if (!economy.has(player, total)) {
                    plugin.messages().send(player, "buy-insufficient-funds", "%price%", formatMoney(total),
                            "%count%", Integer.toString(count));
                    SoundPlayer.play(plugin, player, "failure");
                    return new PurchaseResult(PurchaseStatus.INSUFFICIENT_FUNDS, 0, total, currentCapacity);
                }
                if (!economy.withdraw(player, total)) {
                    plugin.messages().send(player, "buy-withdraw-failed");
                    SoundPlayer.play(plugin, player, "failure");
                    return new PurchaseResult(PurchaseStatus.WITHDRAW_FAILED, 0, total, currentCapacity);
                }
            }

            data.addPurchasedSlots(count);
            storage.markDirty();
            int resultingAvailable = availableSlots(data, permissionBonus(player));
            plugin.messages().send(player, "buy-success", "%count%", Integer.toString(count),
                    "%price%", formatMoney(total), "%available%", Integer.toString(resultingAvailable));
            SoundPlayer.play(plugin, player, "purchase");
            return new PurchaseResult(PurchaseStatus.SUCCESS, count, total, resultingAvailable);
        }
    }

    public double nextPrice(UUID owner) {
        PlayerHomes data = profile(owner);
        return calculator(plugin.settings()).price(data.purchasedSlots());
    }

    public double priceFor(UUID owner, int count) {
        PlayerHomes data = profile(owner);
        return calculator(plugin.settings()).total(data.purchasedSlots(), Math.max(1, count));
    }

    public String formatMoney(double amount) {
        HomesSettings.Pricing pricing = plugin.settings().pricing();
        String pattern = pricing.decimalPlaces() == 0 ? "#,##0" : "#,##0." + "0".repeat(pricing.decimalPlaces());
        DecimalFormat formatter = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
        String fallback = pricing.currencySymbol() + formatter.format(amount);
        return economy.format(amount, fallback);
    }

    public void sendInfo(Player player) {
        PlayerHomes data = profile(player.getUniqueId());
        plugin.messages().send(player, "info-header");
        plugin.messages().send(player, "info-slots", "%saved%", Integer.toString(data.savedCount()),
                "%available%", Integer.toString(availableSlots(player)),
                "%purchased%", Integer.toString(data.purchasedSlots()));
        plugin.messages().send(player, "info-next-price", "%price%", formatMoney(nextPrice(player.getUniqueId())));
        plugin.messages().send(player, "info-economy", "%provider%",
                plugin.settings().economy().enabled() ? economy.providerName() : "disabled");
    }

    public void sendPrice(Player player, int count) {
        if (count < 1 || count > plugin.settings().homes().maxBulkPurchase()) {
            plugin.messages().send(player, "buy-invalid-count",
                    "%max%", Integer.toString(plugin.settings().homes().maxBulkPurchase()));
            return;
        }
        plugin.messages().send(player, "price", "%count%", Integer.toString(count),
                "%price%", formatMoney(priceFor(player.getUniqueId(), count)));
    }

    private static HomePriceCalculator calculator(HomesSettings settings) {
        HomesSettings.Pricing pricing = settings.pricing();
        return new HomePriceCalculator(pricing.firstExtraSlot(), pricing.multiplier(), pricing.decimalPlaces());
    }

    private static String coordinate(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private int permissionBonus(Player player) {
        String prefix = plugin.settings().homes().permissionSlotPrefix();
        int maximum = 0;
        for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
            if (!permission.getValue()) {
                continue;
            }
            String node = permission.getPermission();
            if (node == null || !node.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            String suffix = node.substring(prefix.length());
            try {
                int amount = Integer.parseInt(suffix);
                if (amount > 0) {
                    maximum = Math.max(maximum, Math.min(amount, HomesSettings.ABSOLUTE_MAX_HOMES));
                }
            } catch (NumberFormatException ignored) {
                // Not a homesgui.slots.<number> node.
            }
        }
        return maximum;
    }

    public enum PurchaseStatus {
        SUCCESS, INVALID_COUNT, LIMIT, NO_ECONOMY, INSUFFICIENT_FUNDS, WITHDRAW_FAILED, DISABLED
    }

    public record PurchaseResult(PurchaseStatus status, int bought, double amount, int available) { }
}
