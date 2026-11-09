package dev.superseller.veltryx.rtp;

/**
 * Pure safe-block rules for random teleports. Works on material names so
 * the smoke suite can verify lava/cactus/void cases without a world.
 */
public final class RtpSafety {

    private RtpSafety() {
    }

    /**
     * Whether standing at a spot is safe: the feet and head blocks must be
     * plain air, and the ground must be solid and not dangerous (lava,
     * magma, cactus, fire, campfire, sweet berries, powder snow, …).
     *
     * @param groundName  material name of the block under the feet
     * @param groundSolid whether that block is solid
     * @param feetName    material name at the feet position
     * @param headName    material name at the head position
     */
    public static boolean isSafe(String groundName, boolean groundSolid, String feetName, String headName) {
        if (!isAir(feetName) || !isAir(headName)) {
            return false;
        }
        if (!groundSolid || isDangerous(groundName)) {
            return false;
        }
        return true;
    }

    /** Plain air variants only — anything else (water, plants) is not a teleport spot. */
    public static boolean isAir(String name) {
        return "AIR".equals(name) || "CAVE_AIR".equals(name) || "VOID_AIR".equals(name);
    }

    /** Ground you must never stand on. */
    public static boolean isDangerous(String name) {
        if (name == null) {
            return true;
        }
        return name.equals("LAVA") || name.equals("WATER")
                || name.equals("MAGMA_BLOCK") || name.equals("CACTUS")
                || name.equals("FIRE") || name.equals("SOUL_FIRE")
                || name.equals("CAMPFIRE") || name.equals("SOUL_CAMPFIRE")
                || name.equals("SWEET_BERRY_BUSH") || name.equals("WITHER_ROSE")
                || name.equals("POWDER_SNOW") || name.equals("BEDROCK")
                || name.equals("BARRIER") || name.equals("LAVA_CAULDRON")
                || name.equals("KELP") || name.equals("KELP_PLANT")
                || name.equals("SEAGRASS") || name.equals("TALL_SEAGRASS");
    }
}
