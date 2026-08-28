package dev.superseller.playervault.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.playervault.config.Messages;
import dev.superseller.playervault.config.Settings;
import dev.superseller.playervault.economy.EconomyHook;
import dev.superseller.playervault.gui.VaultHolder;
import dev.superseller.playervault.model.VaultData;
import dev.superseller.playervault.pricing.PriceCalculator;
import dev.superseller.playervault.scheduler.PlatformScheduler;
import dev.superseller.playervault.storage.VaultStore;
import dev.superseller.playervault.util.Numbers;

/**
 * Owns the vault cache and every rule about how a vault grows.
 *
 * <p>Vaults are cached in memory and written back asynchronously. The cache is a
 * {@link ConcurrentHashMap} and each {@link VaultData} guards its own fields, so a
 * region thread editing a vault and an async task saving it never corrupt each other.
 */
public final class VaultService {

    /** Permission node pattern that grants free starting rows, e.g. {@code playervault.rows.5}. */
    public static final String ROWS_PERMISSION_PREFIX = "playervault.rows.";

    /** How long a bulk purchase confirmation stays valid. */
    private static final long CONFIRM_TIMEOUT_MS = 30_000L;

    private record Pending(int rows, double price, long expiresAt) {
    }

    private final JavaPlugin plugin;
    private final Messages messages;
    private final PlatformScheduler scheduler;
    private final EconomyHook economy;
    private final Supplier<Settings> settings;

    private final ConcurrentHashMap<UUID, VaultData> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> pages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Pending> pending = new ConcurrentHashMap<>();

    private volatile VaultStore store;
    private volatile java.util.function.Consumer<Player> afterUpgrade = player -> {
    };

    public VaultService(JavaPlugin plugin, Messages messages, PlatformScheduler scheduler,
                        EconomyHook economy, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.messages = messages;
        this.scheduler = scheduler;
        this.economy = economy;
        this.settings = settings;
    }

    public void store(VaultStore store) {
        this.store = store;
    }

    public VaultStore store() {
        return store;
    }

    /**
     * Registers a callback invoked after a player's vault grew, so the GUI layer can
     * repaint an open inventory without the service depending on it.
     */
    public void afterUpgrade(java.util.function.Consumer<Player> hook) {
        this.afterUpgrade = hook == null ? player -> {
        } : hook;
    }

    public Settings settings() {
        return settings.get();
    }

    public PriceCalculator pricing() {
        return settings.get().pricing();
    }

    // ── cache ────────────────────────────────────────────────────────────────

    /**
     * Returns the cached vault, loading it from storage on a miss.
     *
     * <p>A miss is normally only possible before the async preload finished or when
     * an admin inspects an offline player, so the synchronous read is acceptable.
     */
    public VaultData vault(OfflinePlayer player) {
        VaultData data = vaultById(player.getUniqueId());
        if (player.getName() != null) {
            data.name(player.getName());
        }
        return data;
    }

    /** Same as {@link #vault(OfflinePlayer)} but addressed by UUID. */
    public VaultData vaultById(UUID owner) {
        VaultData cached = cache.get(owner);
        if (cached != null) {
            return cached;
        }
        VaultData loaded = null;
        VaultStore backend = store;
        if (backend != null) {
            try {
                loaded = backend.load(owner);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not load the vault of " + owner, ex);
            }
        }
        if (loaded == null) {
            // No name lookup here: OfflinePlayer.getName() can block on a profile
            // lookup, and this path is reachable from any thread. The display name is
            // filled in by vault(OfflinePlayer) whenever the player is resolvable.
            loaded = new VaultData(owner, null, settings.get().vault().startingRows());
        }
        VaultData previous = cache.putIfAbsent(owner, loaded);
        return previous == null ? loaded : previous;
    }

    /** Queues an asynchronous load so the vault is ready before the player needs it. */
    public void preload(OfflinePlayer player) {
        UUID owner = player.getUniqueId();
        String name = player.getName();
        scheduler.runAsync(() -> {
            VaultStore backend = store;
            if (backend == null) {
                return;
            }
            try {
                VaultData loaded = backend.load(owner);
                if (loaded == null) {
                    loaded = new VaultData(owner, name, settings.get().vault().startingRows());
                }
                if (name != null) {
                    loaded.name(name);
                }
                cache.putIfAbsent(owner, loaded);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not preload the vault of " + owner, ex);
            }
        });
    }

    public void saveAsync(VaultData data) {
        VaultStore backend = store;
        if (backend == null || data == null) {
            return;
        }
        scheduler.runAsync(() -> {
            try {
                backend.save(data);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save the vault of " + data.owner(), ex);
            }
        });
    }

    /** Writes every cached vault. Returns how many were written. */
    public int saveAll() {
        VaultStore backend = store;
        if (backend == null) {
            return 0;
        }
        int written = 0;
        for (VaultData data : cache.values()) {
            try {
                backend.save(data);
                written++;
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save the vault of " + data.owner(), ex);
            }
        }
        return written;
    }

    /** Drops a player's vault from the cache. Call after saving. */
    public void forget(UUID owner) {
        cache.remove(owner);
        pages.remove(owner);
        pending.remove(owner);
    }

    public int cachedCount() {
        return cache.size();
    }

    // ── pages ────────────────────────────────────────────────────────────────

    public int page(UUID owner) {
        return pages.getOrDefault(owner, 0);
    }

    public void page(UUID owner, int page) {
        pages.put(owner, Math.max(0, page));
    }

    // ── rows ─────────────────────────────────────────────────────────────────

    /** Rows granted by {@code playervault.rows.<n>} permissions, or 0. */
    public int bonusRows(Permissible permissible) {
        Settings.BonusMode mode = settings.get().vault().bonusMode();
        if (mode == Settings.BonusMode.OFF || permissible == null) {
            return 0;
        }
        int best = 0;
        for (PermissionAttachmentInfo info : permissible.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String node = info.getPermission();
            if (node == null || !node.regionMatches(true, 0, ROWS_PERMISSION_PREFIX, 0, ROWS_PERMISSION_PREFIX.length())) {
                continue;
            }
            String suffix = node.substring(ROWS_PERMISSION_PREFIX.length());
            if (Numbers.isPositiveInt(suffix)) {
                best = Math.max(best, Integer.parseInt(suffix.trim()));
            }
        }
        return best;
    }

    /**
     * Grows a vault to match the player's permission bonus rows.
     *
     * <p>Only ever grows: taking a rank away never deletes stored items. An admin has
     * to shrink a vault explicitly.
     *
     * @return the row count the vault should show right now
     */
    public int syncBonusRows(Player player, VaultData data) {
        Settings.Vault config = settings.get().vault();
        if (config.bonusMode() == Settings.BonusMode.OFF) {
            return data.rows();
        }
        int bonus = bonusRows(player);
        if (bonus <= 0) {
            return data.rows();
        }
        int target = config.bonusMode() == Settings.BonusMode.ADD
                ? Math.max(data.rows(), config.startingRows() + bonus)
                : Math.max(data.rows(), bonus);
        if (target > data.rows()) {
            data.resize(target);
            saveAsync(data);
        }
        return data.rows();
    }

    /** Price of the next row for this vault. */
    public double nextPrice(VaultData data) {
        return pricing().priceOfRow(data.purchasedRows());
    }

    /** Formats money using the hooked economy, or plainly when there is none. */
    public String money(double amount) {
        return economy.format(amount);
    }

    /** Current balance according to the hooked economy. */
    public double balance(OfflinePlayer player) {
        return economy.balance(player.getUniqueId());
    }

    public EconomyHook economy() {
        return economy;
    }

    // ── upgrades ─────────────────────────────────────────────────────────────

    /**
     * Buys rows for a player.
     *
     * @param player the buyer
     * @param requested how many rows they asked for
     * @param confirmed {@code true} to skip the bulk purchase confirmation prompt
     * @return {@code true} when rows were actually added
     */
    public boolean upgrade(Player player, int requested, boolean confirmed) {
        Settings config = settings.get();
        VaultData data = vault(player);
        int count = Math.clamp(requested, 1, config.upgrade().maxRowsPerPurchase());

        if (requested > config.upgrade().maxRowsPerPurchase()) {
            messages.send(player, "upgrade.too-many", "%max%", String.valueOf(config.upgrade().maxRowsPerPurchase()));
            return false;
        }

        Settings.Vault vaultConfig = config.vault();
        boolean bypassLimit = player.hasPermission("playervault.bypass.limit");
        if (vaultConfig.limited() && !bypassLimit) {
            int remaining = vaultConfig.maxRows() - data.rows();
            if (remaining <= 0) {
                messages.send(player, "upgrade.already-max", "%rows%", String.valueOf(vaultConfig.maxRows()));
                return false;
            }
            if (count > remaining) {
                messages.send(player, "upgrade.would-exceed-max",
                        "%count%", String.valueOf(remaining),
                        "%max%", String.valueOf(vaultConfig.maxRows()));
                count = remaining;
            }
        }

        boolean bypass = config.economy().enabled() && player.hasPermission("playervault.bypass.cost");
        boolean free = !config.economy().enabled() || bypass;
        if (free) {
            return grantRows(player, data, count, 0.0d, true, bypass);
        }

        if (!economy.isEnabled()) {
            messages.send(player, "upgrade.no-economy");
            return false;
        }

        double price = pricing().total(data.purchasedRows(), count);
        if (config.upgrade().confirmThreshold() > 0 && count >= config.upgrade().confirmThreshold()
                && !confirmed) {
            pending.put(player.getUniqueId(), new Pending(count, price, System.currentTimeMillis() + CONFIRM_TIMEOUT_MS));
            messages.send(player, "upgrade.confirm-required",
                    "%count%", String.valueOf(count),
                    "%price%", money(price));
            return false;
        }

        if (!economy.has(player.getUniqueId(), price)) {
            messages.send(player, "upgrade.not-enough-money",
                    "%price%", money(price),
                    "%balance%", money(economy.balance(player.getUniqueId())));
            return false;
        }
        if (!economy.withdraw(player.getUniqueId(), price)) {
            messages.send(player, "upgrade.not-enough-money",
                    "%price%", money(price),
                    "%balance%", money(economy.balance(player.getUniqueId())));
            return false;
        }
        pending.remove(player.getUniqueId());
        if (config.economy().logTransactions()) {
            plugin.getLogger().info(player.getName() + " bought " + count + " vault row(s) for " + money(price));
        }
        return grantRows(player, data, count, price, false, false);
    }

    /** Replays a pending bulk purchase confirmation. */
    public boolean confirm(Player player) {
        Pending request = pending.get(player.getUniqueId());
        if (request == null || request.expiresAt() < System.currentTimeMillis()) {
            pending.remove(player.getUniqueId());
            messages.send(player, "upgrade.confirm-expired");
            return false;
        }
        return upgrade(player, request.rows(), true);
    }

    public boolean hasPending(Player player) {
        Pending request = pending.get(player.getUniqueId());
        return request != null && request.expiresAt() >= System.currentTimeMillis();
    }

    private boolean grantRows(Player player, VaultData data, int count, double price, boolean free,
                              boolean bypass) {
        data.resize(data.rows() + count);
        // The ladder always advances, even for an upgrade that cost nothing. The price
        // is indexed by purchasedRows, so skipping this froze every later row at the
        // base price -- and a player who later lost the bypass could re-buy rows they
        // already owned at row-one prices.
        data.addPurchasedRows(count);
        if (!free) {
            data.totalSpent(data.totalSpent() + price);
        }
        saveAsync(data);
        String resultKey = bypass ? "upgrade.success-bypass"
                : free ? "upgrade.success-free" : "upgrade.success";
        messages.send(player, resultKey,
                "%count%", String.valueOf(count),
                "%price%", money(price),
                "%rows%", String.valueOf(data.rows()),
                "%slots%", String.valueOf(data.capacity()));
        afterUpgrade.accept(player);
        return true;
    }

    // ── admin ────────────────────────────────────────────────────────────────

    /** Sets an exact row count, evicting and returning anything that no longer fits. */
    public void setRows(OfflinePlayer target, int rows, CommandSender actor) {
        Settings config = settings.get();
        VaultData data = vault(target);
        int before = data.rows();
        int next = Math.max(1, rows);
        List<ItemStack> evicted = data.resize(next);
        refundIfShrunk(target, data, before, next, config);
        saveAsync(data);
        messages.send(actor, "admin-messages.rows-set",
                "%player%", String.valueOf(target.getName()),
                "%rows%", String.valueOf(next));
        afterRemoteChange(target, evicted, before);
    }

    /** Grants or removes rows without charging, and without moving the price ladder. */
    public void addRows(OfflinePlayer target, int count, CommandSender actor) {
        VaultData data = vault(target);
        int before = data.rows();
        if (count >= 0) {
            data.resize(data.rows() + count);
            saveAsync(data);
            messages.send(actor, "admin-messages.rows-added",
                    "%player%", String.valueOf(target.getName()),
                    "%count%", String.valueOf(count),
                    "%rows%", String.valueOf(data.rows()));
            afterRemoteChange(target, List.of(), before);
            return;
        }
        int next = Math.max(1, before + count);
        List<ItemStack> evicted = data.resize(next);
        refundIfShrunk(target, data, before, next, settings.get());
        saveAsync(data);
        messages.send(actor, "admin-messages.rows-removed",
                "%player%", String.valueOf(target.getName()),
                "%count%", String.valueOf(-count),
                "%rows%", String.valueOf(next));
        afterRemoteChange(target, evicted, before);
    }

    /** Back to the configured starting size, keeping the items that still fit. */
    public void reset(OfflinePlayer target, CommandSender actor) {
        Settings config = settings.get();
        VaultData data = vault(target);
        int before = data.rows();
        int next = Math.max(1, config.vault().startingRows());
        List<ItemStack> evicted = data.resize(next);
        data.purchasedRows(0);
        refundIfShrunk(target, data, before, next, config);
        saveAsync(data);
        messages.send(actor, "admin-messages.reset",
                "%player%", String.valueOf(target.getName()),
                "%rows%", String.valueOf(next));
        afterRemoteChange(target, evicted, before);
    }

    /** Empties a vault without touching its size. */
    public void clear(OfflinePlayer target, CommandSender actor) {
        VaultData data = vault(target);
        data.clear();
        saveAsync(data);
        messages.send(actor, "admin-messages.cleared", "%player%", String.valueOf(target.getName()));
        afterRemoteChange(target, List.of(), data.rows());
    }

    private void refundIfShrunk(OfflinePlayer target, VaultData data, int before, int after, Settings settings) {
        double percent = settings.economy().refundPercent();
        int removed = before - after;
        if (percent <= 0.0d || removed <= 0 || !economy.isEnabled()) {
            return;
        }
        // Price the most recently purchased rows, which are the ones being taken away.
        double paid = pricing().total(Math.max(0, data.purchasedRows() - removed), removed);
        double refund = Numbers.round(paid * percent / 100.0d, settings.upgrade().roundDecimals());
        if (refund > 0.0d && economy.deposit(target.getUniqueId(), refund)) {
            if (settings.economy().logTransactions()) {
                plugin.getLogger().info("Refunded " + money(refund) + " to " + target.getName()
                        + " for " + removed + " removed vault row(s).");
            }
            if (target.isOnline() && target.getPlayer() != null) {
                messages.send(target.getPlayer(), "admin-messages.refund", "%amount%", money(refund));
            }
        }
    }

    /**
     * Reacts to a change made by someone else: close a stale GUI and hand back any
     * evicted items on the region thread that owns the player.
     */
    private void afterRemoteChange(OfflinePlayer target, List<ItemStack> evicted, int rowsBefore) {
        Player online = target.getPlayer();
        if (online == null || !online.isOnline()) {
            return;
        }
        List<ItemStack> returned = new ArrayList<>(evicted);
        scheduler.runEntity(online, () -> {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof VaultHolder holder
                    && holder.owner().equals(online.getUniqueId())) {
                online.closeInventory();
            }
            for (ItemStack stack : returned) {
                online.getWorld().dropItemNaturally(online.getLocation(), stack);
            }
            if (!returned.isEmpty()) {
                messages.send(online, "admin-messages.items-dropped", "%count%", String.valueOf(returned.size()));
            }
            int rowsNow = vault(online).rows();
            String key = rowsNow > rowsBefore
                    ? "admin-messages.target-upgraded"
                    : rowsNow < rowsBefore
                            ? "admin-messages.target-shrunk"
                            : "admin-messages.target-updated";
            messages.send(online, key, "%rows%", String.valueOf(rowsNow));
        });
    }
}
