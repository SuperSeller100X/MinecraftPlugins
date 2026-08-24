package dev.superseller.shardtools.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the extra block offsets for area mining. The area is the plane
 * perpendicular to the direction the player is looking, exactly like a
 * player mining a wall or a floor would expect. Pure logic.
 */
public final class AreaPlane {

    private AreaPlane() {
    }

    /**
     * @param lookX  x component of the normalized look direction
     * @param lookY  y component
     * @param lookZ  z component
     * @param radius 1 for 3x3, 2 for 5x5
     * @return offsets around the origin block, excluding the origin itself
     */
    public static List<int[]> offsets(double lookX, double lookY, double lookZ, int radius) {
        int r = Math.max(1, radius);
        double ax = Math.abs(lookX);
        double ay = Math.abs(lookY);
        double az = Math.abs(lookZ);
        List<int[]> offsets = new ArrayList<>(r * r * 4 * 2 + r * 4);
        if (ax >= ay && ax >= az) {
            // looking along X -> vary Y and Z
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dy != 0 || dz != 0) {
                        offsets.add(new int[]{0, dy, dz});
                    }
                }
            }
        } else if (ay >= ax && ay >= az) {
            // looking along Y -> vary X and Z
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx != 0 || dz != 0) {
                        offsets.add(new int[]{dx, 0, dz});
                    }
                }
            }
        } else {
            // looking along Z -> vary X and Y
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (dx != 0 || dy != 0) {
                        offsets.add(new int[]{dx, dy, 0});
                    }
                }
            }
        }
        return offsets;
    }
}
