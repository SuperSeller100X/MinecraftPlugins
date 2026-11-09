package dev.superseller.veltryx.api;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holder for the active {@link VeltryxEconomy} implementation. Set on enable,
 * cleared on disable.
 */
public final class EconomyRegistry {

    private static final AtomicReference<VeltryxEconomy> INSTANCE = new AtomicReference<>();

    private EconomyRegistry() {
    }

    public static void register(VeltryxEconomy economy) {
        INSTANCE.set(economy);
    }

    public static void unregister() {
        INSTANCE.set(null);
    }

    static Optional<VeltryxEconomy> get() {
        return Optional.ofNullable(INSTANCE.get());
    }
}
