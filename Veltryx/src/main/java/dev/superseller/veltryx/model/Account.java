package dev.superseller.veltryx.model;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;
import dev.superseller.veltryx.util.Money;

/** A persisted economy account, in exact cents. */
public record Account(UUID uuid, String name, BigInteger balanceExact) {
    public Account {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(balanceExact, "balanceExact");
        if (balanceExact.signum() < 0) throw new IllegalArgumentException("negative balance");
    }
    public Account(UUID uuid, String name, long balance) {
        this(uuid, name, BigInteger.valueOf(Math.max(0L, balance)));
    }
    /** Legacy long view; very large balances clamp rather than wrap. */
    public long balance() { return Money.legacyLong(balanceExact); }
}
