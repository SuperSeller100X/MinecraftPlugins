package dev.superseller.veltryx.economy;

import dev.superseller.veltryx.api.VeltryxEconomy;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.model.Account;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Money;
import java.util.List;
import java.util.UUID;

/**
 * The Veltryx economy: exact cent-based balances on top of the {@link DataStore}.
 * Money operations are saturated (never overflow) and never go negative.
 */
public final class EconomyService implements VeltryxEconomy {

    private final DataStore store;
    private final VeltryxConfig config;

    public EconomyService(DataStore store, VeltryxConfig config) {
        this.store = store;
        this.config = config;
    }

    @Override
    public long getBalance(UUID player) {
        return store.getBalance(player);
    }

    @Override
    public boolean has(UUID player, long amountCents) {
        return amountCents >= 0 && store.getBalance(player) >= amountCents;
    }

    @Override
    public long deposit(UUID player, String name, long amountCents) {
        if (amountCents <= 0) {
            return store.getBalance(player);
        }
        long balance = Money.addSaturated(store.getBalance(player), amountCents);
        store.setBalance(player, name, balance);
        return balance;
    }

    @Override
    public boolean withdraw(UUID player, String name, long amountCents) {
        if (amountCents < 0) {
            return false;
        }
        long balance = store.getBalance(player);
        if (balance < amountCents) {
            return false;
        }
        store.setBalance(player, name, balance - amountCents);
        return true;
    }

    @Override
    public boolean transfer(UUID from, String fromName, UUID to, String toName, long amountCents) {
        if (amountCents <= 0 || from.equals(to)) {
            return false;
        }
        long balance = store.getBalance(from);
        if (balance < amountCents) {
            return false;
        }
        store.setBalance(from, fromName, balance - amountCents);
        store.setBalance(to, toName, Money.addSaturated(store.getBalance(to), amountCents));
        return true;
    }

    @Override
    public void setBalance(UUID player, String name, long amountCents) {
        store.setBalance(player, name, Math.max(0L, amountCents));
    }

    @Override
    public List<Account> top(int limit) {
        return store.top(limit);
    }

    @Override
    public String format(long amountCents) {
        return config.currencySymbol() + Money.formatNumber(amountCents);
    }

    /** Whether the player has ever been seen (has an account/profile). */
    public boolean isKnown(UUID player) {
        return store.getProfile(player) != null;
    }

    /** Grants the configured starting balance to a brand-new player. */
    public long grantStartingBalance(UUID player, String name) {
        long starting = config.startingBalance();
        if (starting <= 0) {
            return 0L;
        }
        store.setBalance(player, name, starting);
        return starting;
    }
}
