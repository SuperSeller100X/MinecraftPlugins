package dev.superseller.xpbank.bank;

import dev.superseller.xpbank.config.XPBankConfig;
import dev.superseller.xpbank.storage.BankStorage;
import dev.superseller.xpbank.util.ExperienceUtil;
import dev.superseller.xpbank.util.Numbers;

import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * The heart of the plugin: converts between a player's XP-in-hand and their
 * banked XP. All mutating methods must run on the player's owning thread
 * (entity scheduler / main thread) because they read and write live XP.
 *
 * <p>Amount handling supports the {@link Numbers#ALL}/{@link Numbers#HALF}
 * sentinels so commands and the GUI can share the same resolution logic.
 */
public final class BankService {

    private final XPBankConfig config;
    private final BankStorage storage;

    public BankService(XPBankConfig config, BankStorage storage) {
        this.config = config;
        this.storage = storage;
    }

    public long getBalance(UUID uuid) {
        return storage.get(uuid);
    }

    /** Resolves an ALL/HALF/explicit amount against a base value. */
    private long resolve(long requested, long base) {
        if (requested == Numbers.ALL) {
            return base;
        }
        if (requested == Numbers.HALF) {
            return base / 2;
        }
        return requested;
    }

    /**
     * Moves XP from the player's experience bar into their bank.
     *
     * @param requested a positive amount, or {@link Numbers#ALL}/{@link Numbers#HALF}
     */
    public TransactionResult deposit(Player player, long requested) {
        UUID uuid = player.getUniqueId();
        long onHand = ExperienceUtil.getPlayerExp(player);
        long banked = storage.get(uuid);
        long amount = resolve(requested, onHand);

        if (amount <= 0) {
            return new TransactionResult(TransactionResult.Status.ZERO, 0, onHand, banked);
        }
        if (requested != Numbers.ALL && requested != Numbers.HALF && amount < config.minDeposit()) {
            return new TransactionResult(TransactionResult.Status.BELOW_MINIMUM,
                    config.minDeposit(), onHand, banked);
        }
        if (amount > onHand) {
            return new TransactionResult(TransactionResult.Status.NOT_ENOUGH_XP, amount, onHand, banked);
        }

        ExperienceUtil.changePlayerExp(player, (int) -Math.min(amount, Integer.MAX_VALUE));
        long newBanked = banked + amount;
        storage.set(uuid, player.getName(), newBanked);
        long newOnHand = ExperienceUtil.getPlayerExp(player);
        return new TransactionResult(TransactionResult.Status.OK, amount, newOnHand, newBanked);
    }

    /**
     * Moves XP from the player's bank back onto their experience bar.
     *
     * @param requested a positive amount, or {@link Numbers#ALL}/{@link Numbers#HALF}
     */
    public TransactionResult withdraw(Player player, long requested) {
        UUID uuid = player.getUniqueId();
        long onHand = ExperienceUtil.getPlayerExp(player);
        long banked = storage.get(uuid);
        long amount = resolve(requested, banked);

        if (amount <= 0) {
            return new TransactionResult(TransactionResult.Status.ZERO, 0, onHand, banked);
        }
        if (requested != Numbers.ALL && requested != Numbers.HALF && amount < config.minWithdraw()) {
            return new TransactionResult(TransactionResult.Status.BELOW_MINIMUM,
                    config.minWithdraw(), onHand, banked);
        }
        if (amount > banked) {
            return new TransactionResult(TransactionResult.Status.NOT_ENOUGH_BANK, amount, onHand, banked);
        }

        long newBanked = banked - amount;
        storage.set(uuid, player.getName(), newBanked);
        ExperienceUtil.changePlayerExp(player, (int) Math.min(amount, Integer.MAX_VALUE));
        long newOnHand = ExperienceUtil.getPlayerExp(player);
        return new TransactionResult(TransactionResult.Status.OK, amount, newOnHand, newBanked);
    }

    /**
     * Transfers banked XP from one player to another player's bank. Does not
     * touch either player's XP bar. Runs against storage only, so it is safe
     * even when the recipient is offline.
     */
    public TransactionResult transfer(Player from, UUID toId, String toName, long requested) {
        if (!config.transfersEnabled()) {
            return new TransactionResult(TransactionResult.Status.DISABLED, 0, 0,
                    storage.get(from.getUniqueId()));
        }
        UUID fromId = from.getUniqueId();
        if (toId == null) {
            return new TransactionResult(TransactionResult.Status.TARGET_UNKNOWN, 0, 0, storage.get(fromId));
        }
        if (fromId.equals(toId)) {
            return new TransactionResult(TransactionResult.Status.SELF, 0, 0, storage.get(fromId));
        }
        long fromBank = storage.get(fromId);
        long amount = resolve(requested, fromBank);
        if (amount <= 0) {
            return new TransactionResult(TransactionResult.Status.ZERO, 0, 0, fromBank);
        }
        if (requested != Numbers.ALL && requested != Numbers.HALF && amount < config.minTransfer()) {
            return new TransactionResult(TransactionResult.Status.BELOW_MINIMUM,
                    config.minTransfer(), 0, fromBank);
        }
        if (amount > fromBank) {
            return new TransactionResult(TransactionResult.Status.NOT_ENOUGH_BANK, amount, 0, fromBank);
        }

        long newFrom = fromBank - amount;
        long newTo = storage.get(toId) + amount;
        storage.set(fromId, from.getName(), newFrom);
        storage.set(toId, toName, newTo);
        return new TransactionResult(TransactionResult.Status.OK, amount, 0, newFrom);
    }

    // ---- Admin operations (operate directly on storage) --------------------

    public long adminSet(UUID uuid, String name, long amount) {
        long safe = Math.max(0L, amount);
        storage.set(uuid, name, safe);
        return safe;
    }

    public long adminAdd(UUID uuid, String name, long delta) {
        long updated = Math.max(0L, storage.get(uuid) + delta);
        storage.set(uuid, name, updated);
        return updated;
    }

    public long adminTake(UUID uuid, String name, long delta) {
        long updated = Math.max(0L, storage.get(uuid) - delta);
        storage.set(uuid, name, updated);
        return updated;
    }

    public BankStorage storage() {
        return storage;
    }
}
