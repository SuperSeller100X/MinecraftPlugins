package dev.superseller.veltryx.api;

import dev.superseller.veltryx.model.Account;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public economy API for Veltryx modules (the upcoming shop, shops GUIs,
 * rank purchases, …) and for other plugins on the server.
 *
 * <p>All amounts are <b>cents</b> (minor units): {@code 100} = one unit of the
 * currency. Use {@link #format(long)} for display.
 */
public interface VeltryxEconomy {

    /** The registered economy implementation, if Veltryx is enabled. */
    static Optional<VeltryxEconomy> get() {
        return EconomyRegistry.get();
    }

    /** Legacy long-cent view (clamped at Long.MAX_VALUE; use getBalanceExact for full precision). */
    long getBalance(UUID player);

    /** Whether the account holds at least {@code amountCents}. */
    boolean has(UUID player, long amountCents);

    /** Adds to the exact balance. Returns a legacy long view, clamped at Long.MAX_VALUE. */
    long deposit(UUID player, String name, long amountCents);

    /** Removes from the balance. Returns false when the account cannot cover it. */
    boolean withdraw(UUID player, String name, long amountCents);

    /** Moves money between accounts atomically enough for a game server. */
    boolean transfer(UUID from, String fromName, UUID to, String toName, long amountCents);

    /** Overwrites the balance (admin operation). */
    void setBalance(UUID player, String name, long amountCents);

    /** Exact-cent APIs for balances beyond the legacy signed-long range. */
    default BigInteger getBalanceExact(UUID player) { return BigInteger.valueOf(getBalance(player)); }
    default BigInteger depositExact(UUID player, String name, BigInteger cents) {
        return BigInteger.valueOf(deposit(player, name, cents.longValueExact()));
    }
    default boolean withdrawExact(UUID player, String name, BigInteger cents) {
        return withdraw(player, name, cents.longValueExact());
    }
    default boolean transferExact(UUID from, String fromName, UUID to, String toName, BigInteger cents) {
        return transfer(from, fromName, to, toName, cents.longValueExact());
    }
    default void setBalanceExact(UUID player, String name, BigInteger cents) {
        setBalance(player, name, cents.longValueExact());
    }
    default String formatExact(BigInteger cents) { return format(cents.longValueExact()); }

    /** Top accounts by balance, highest first. */
    List<Account> top(int limit);

    /** Formats cents for chat (e.g. {@code $1,250.50}). */
    String format(long amountCents);
}
