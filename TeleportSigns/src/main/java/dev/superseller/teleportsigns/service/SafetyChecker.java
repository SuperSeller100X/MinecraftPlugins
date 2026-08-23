package dev.superseller.teleportsigns.service;

/**
 * Classifies destination blocks from material names so the rules can be
 * unit-tested without a running server.
 */
public final class SafetyChecker {

    private SafetyChecker() {
    }

    public static boolean isAir(String material) {
        if (material == null) {
            return false;
        }
        return "AIR".equals(material) || "CAVE_AIR".equals(material) || "VOID_AIR".equals(material);
    }

    public static boolean isLava(String material) {
        return material != null && material.contains("LAVA");
    }

    public static boolean isFire(String material) {
        if (material == null) {
            return false;
        }
        return "FIRE".equals(material) || "SOUL_FIRE".equals(material)
                || "MAGMA_BLOCK".equals(material) || material.contains("CAMPFIRE");
    }

    public static boolean isFluidHazard(String material) {
        return isLava(material);
    }

    /**
     * @return a reason key ({@code void}, {@code lava}, {@code fire},
     * {@code solid}, {@code floor}) or {@code null} when the destination is safe
     */
    public static String evaluate(String feet, String head, String below,
                                  boolean inWorld, boolean belowSolid,
                                  boolean checkLava, boolean checkFire,
                                  boolean requireFloor, boolean rejectVoid) {
        if (rejectVoid && !inWorld) {
            return "void";
        }
        if (checkLava && (isLava(feet) || isLava(head) || isLava(below))) {
            return "lava";
        }
        if (checkFire && (isFire(feet) || isFire(head) || isFire(below))) {
            return "fire";
        }
        if (!isAir(head) && isSolidLike(head)) {
            return "solid";
        }
        if (requireFloor && !belowSolid && !isSolidLike(below)) {
            return "floor";
        }
        return null;
    }

    public static boolean isSolidLike(String material) {
        if (material == null || isAir(material)) {
            return false;
        }
        if (isLava(material) || "WATER".equals(material) || "BUBBLE_COLUMN".equals(material)) {
            return false;
        }
        if (material.endsWith("_SIGN") || material.contains("BANNER") || material.contains("BUTTON")
                || material.contains("PRESSURE_PLATE") || material.contains("TORCH")
                || material.contains("RAIL") || "LEVER".equals(material)
                || material.contains("CARPET") || "SNOW".equals(material)) {
            return false;
        }
        return true;
    }
}
