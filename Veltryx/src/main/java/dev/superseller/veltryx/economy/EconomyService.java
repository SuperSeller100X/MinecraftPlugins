package dev.superseller.veltryx.economy;

import dev.superseller.veltryx.api.VeltryxEconomy;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.model.Account;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Money;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The Veltryx economy: exact nonnegative cent balances. */
public final class EconomyService implements VeltryxEconomy {
    private final DataStore store;
    private final VeltryxConfig config;
    private volatile java.util.function.Consumer<UUID> onChange = id -> { };
    public void onChange(java.util.function.Consumer<UUID> listener) {
        this.onChange = listener == null ? id -> { } : listener;
    }
    public EconomyService(DataStore store, VeltryxConfig config) { this.store = store; this.config = config; }
    private void notifyChange(UUID player) {
        try { onChange.accept(player); }
        catch (RuntimeException ex) {
            // A rendering callback must never turn a committed sale into a retry.
            java.util.logging.Logger.getLogger(EconomyService.class.getName()).log(
                    java.util.logging.Level.WARNING, "Balance refresh failed after a committed transaction", ex);
        }
    }

    @Override public long getBalance(UUID player) { return Money.legacyLong(getBalanceExact(player)); }
    @Override public BigInteger getBalanceExact(UUID player) { return store.getBalanceExact(player); }
    @Override public boolean has(UUID player, long cents) {
        return cents >= 0 && getBalanceExact(player).compareTo(BigInteger.valueOf(cents)) >= 0;
    }
    @Override public long deposit(UUID player, String name, long cents) {
        return Money.legacyLong(depositExact(player, name, BigInteger.valueOf(cents)));
    }
    @Override public synchronized BigInteger depositExact(UUID player, String name, BigInteger cents) {
        Objects.requireNonNull(cents, "cents");
        BigInteger balance = store.getBalanceExact(player);
        if (cents.signum() <= 0) return balance;
        balance = balance.add(cents);
        store.setBalanceExact(player, name, balance);
        notifyChange(player);
        return balance;
    }
    @Override public boolean withdraw(UUID player, String name, long cents) {
        return withdrawExact(player, name, BigInteger.valueOf(cents));
    }
    @Override public synchronized boolean withdrawExact(UUID player, String name, BigInteger cents) {
        Objects.requireNonNull(cents, "cents");
        if (cents.signum() < 0) return false;
        BigInteger balance = store.getBalanceExact(player);
        if (balance.compareTo(cents) < 0) return false;
        store.setBalanceExact(player, name, balance.subtract(cents));
        notifyChange(player);
        return true;
    }
    @Override public boolean transfer(UUID from, String fromName, UUID to, String toName, long cents) {
        return transferExact(from, fromName, to, toName, BigInteger.valueOf(cents));
    }
    @Override public synchronized boolean transferExact(UUID from, String fromName, UUID to, String toName, BigInteger cents) {
        Objects.requireNonNull(cents, "cents");
        if (cents.signum() <= 0 || from.equals(to)) return false;
        BigInteger source = store.getBalanceExact(from);
        if (source.compareTo(cents) < 0) return false;
        BigInteger destination = store.getBalanceExact(to);
        store.setBalancesExact(List.of(new Account(from, fromName, source.subtract(cents)),
                new Account(to, toName, destination.add(cents))));
        notifyChange(from); notifyChange(to);
        return true;
    }
    @Override public void setBalance(UUID player, String name, long cents) {
        setBalanceExact(player, name, BigInteger.valueOf(Math.max(0L, cents)));
    }
    @Override public synchronized void setBalanceExact(UUID player, String name, BigInteger cents) {
        Objects.requireNonNull(cents, "cents");
        if (cents.signum() < 0) throw new IllegalArgumentException("negative balance");
        store.setBalanceExact(player, name, cents);
        notifyChange(player);
    }
    @Override public List<Account> top(int limit) { return store.top(limit); }
    @Override public String format(long cents) { return config.currencySymbol() + Money.formatNumber(cents); }
    @Override public String formatExact(BigInteger cents) { return config.currencySymbol() + Money.formatNumber(cents); }
    public String format(BigInteger cents) { return formatExact(cents); }
    public boolean isKnown(UUID player) { return store.getProfile(player) != null; }
    public long grantStartingBalance(UUID player, String name) {
        return Money.legacyLong(grantStartingBalanceExact(player, name));
    }
    public synchronized BigInteger grantStartingBalanceExact(UUID player, String name) {
        BigInteger starting = config.startingBalanceExact();
        if (starting.signum() <= 0) return BigInteger.ZERO;
        store.setBalanceExact(player, name, starting);
        notifyChange(player);
        return starting;
    }
}
