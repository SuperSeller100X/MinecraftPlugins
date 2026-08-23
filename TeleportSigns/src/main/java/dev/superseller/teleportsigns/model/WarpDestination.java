package dev.superseller.teleportsigns.model;

import dev.superseller.teleportsigns.util.Numbers;

/** A resolved teleport destination. Immutable and free of Bukkit types. */
public final class WarpDestination {

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final boolean hasRotation;

    public WarpDestination(String worldName, double x, double y, double z,
                           float yaw, float pitch, boolean hasRotation) {
        this.worldName = worldName == null ? "" : worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.hasRotation = hasRotation;
    }

    public String worldName() {
        return worldName;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public boolean hasRotation() {
        return hasRotation;
    }

    public String compact() {
        return worldName + " " + Numbers.prettyCoord(x) + " " + Numbers.prettyCoord(y)
                + " " + Numbers.prettyCoord(z);
    }

    public String rotationSuffix() {
        if (!hasRotation) {
            return "";
        }
        return " <gray>(yaw " + Numbers.pretty(yaw) + ", pitch " + Numbers.pretty(pitch) + ")</gray>";
    }
}
