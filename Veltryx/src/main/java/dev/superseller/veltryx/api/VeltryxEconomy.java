package dev.superseller.veltryx.api;

import dev.superseller.veltryx.model.Account;
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

    /** Stored balance in cents (0 when the account is unknown). */
    long getBalance(UUID player);

    /** Whether the account holds at least {@code amountCents}. */
    boolean has(UUID player, long amountCents);

    /** Adds to the balance (clamped at zero and Long.MAX_VALUE). Returns the new balance. */
    long deposit(UUID player, String name, long amountCents);

    /** Removes from the balance. Returns false when the account cannot cover it. */
    boolean withdraw(UUID player, String name, long amountCents);

    /** Moves money between accounts atomically enough for a game server. */
    boolean transfer(UUID from, String fromName, UUID to, String toName, long amountCents);

    /** Overwrites the balance (admin operation). */
    void setBalance(UUID player, String name, long amountCents);

    /** Top accounts by balance, highest first. */
    List<Account> top(int limit);

    /** Formats cents for chat (e.g. {@code $1,250.50}). */
    String format(long amountCents);
}
