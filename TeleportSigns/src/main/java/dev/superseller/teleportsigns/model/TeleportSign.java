package dev.superseller.teleportsigns.model;

import java.util.UUID;

/** A bound teleport sign: location, destination, optional cost, creator. */
public final class TeleportSign {

    private final SignKey key;
    private final WarpDestination destination;
    private final double cost;
    private final UUID creator;
    private final long createdAt;

    public TeleportSign(SignKey key, WarpDestination destination, double cost,
                        UUID creator, long createdAt) {
        this.key = key;
        this.destination = destination;
        this.cost = cost;
        this.creator = creator;
        this.createdAt = createdAt;
    }

    public SignKey key() {
        return key;
    }

    public WarpDestination destination() {
        return destination;
    }

    public double cost() {
        return cost;
    }

    public UUID creator() {
        return creator;
    }

    public long createdAt() {
        return createdAt;
    }

    public TeleportSign withCost(double newCost) {
        return new TeleportSign(key, destination, newCost, creator, createdAt);
    }

    public TeleportSign withDestination(WarpDestination dest) {
        return new TeleportSign(key, dest, cost, creator, createdAt);
    }
}
