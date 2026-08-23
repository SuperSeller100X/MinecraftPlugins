package dev.superseller.teleportsigns.model;

import java.util.Locale;
import java.util.Objects;

/** World name + block coordinates identifying a placed sign. */
public final class SignKey {

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    public SignKey(String worldName, int x, int y, int z) {
        this.worldName = worldName == null ? "" : worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String worldName() {
        return worldName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public String id() {
        return worldName.toLowerCase(Locale.ROOT) + ":" + x + ":" + y + ":" + z;
    }

    public String display() {
        return worldName + " " + x + " " + y + " " + z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SignKey)) {
            return false;
        }
        SignKey other = (SignKey) obj;
        return x == other.x && y == other.y && z == other.z
                && worldName.equalsIgnoreCase(other.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName.toLowerCase(Locale.ROOT), x, y, z);
    }
}
