package dev.superseller.veltryx.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * A conservative snapshot of the blocks actually touching a player's hitbox.
 * Collision-shape boxes from Paper are block-local; they must be translated to
 * world coordinates before comparing them with either movement endpoint.
 */
public final class MovementEnvironment {
    private static final double EPSILON = 1.0e-7;
    private static final double SUPPORT_DISTANCE = 0.125;
    private static final double MAX_SUPPORT_PENETRATION = 0.04;
    private static final double HONEY_REACH = 0.05;
    // Some collision shapes (for example an opening shulker) extend beyond
    // their block cell. Still test the actual box, never the padded cell.
    private static final double HORIZONTAL_SHAPE_EXTENSION = 0.5;
    // Fences/walls can have collision boxes 1.5 blocks high.
    private static final double MAX_SHAPE_HEIGHT = 1.5;
    private static final long MAX_BLOCKS_PER_SAMPLE = 512;

    private MovementEnvironment() {
    }

    /** Unknown geometry must not be treated as either flight or a collision. */
    public record Sample(boolean known, boolean supported, boolean powderSnow, boolean slowingBlock,
                         boolean liquid, boolean climbing, boolean honeyContact,
                         boolean embedded, boolean ice, boolean slimeOrHoney,
                         boolean soulTerrain, Material feet, boolean headInWater) {
        private static Sample unknown() {
            return new Sample(false, false, false, false, false, false, false, false,
                    false, false, false, Material.AIR, false);
        }
    }

    /**
     * Inspect only loaded chunks owned by this thread. If any part of the
     * relevant volume is unavailable (including at a Folia region boundary),
     * the caller must skip geometry-dependent checks rather than load chunks or
     * assume there is no support. Excessively large hitboxes are also skipped.
     */
    public static Sample sample(Player player, Location location) {
        World world = location.getWorld();
        double x = location.getX(), y = location.getY(), z = location.getZ();
        double width = player.getWidth(), height = player.getHeight();
        if (world == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0.0 || height <= 0.0 || width > 32.0 || height > 64.0
                || Math.abs(x) > 30_000_128.0 || Math.abs(z) > 30_000_128.0
                || Math.abs(y) > 30_000_128.0) {
            return Sample.unknown();
        }

        double halfWidth = width / 2.0;
        double minPlayerX = x - halfWidth, maxPlayerX = x + halfWidth;
        double minPlayerZ = z - halfWidth, maxPlayerZ = z + halfWidth;
        // Shapes such as an opening shulker can extend half a block beyond
        // their cell. The padding finds those boxes; support and media still
        // require actual overlap with the hitbox.
        int minX = floor(minPlayerX - HORIZONTAL_SHAPE_EXTENSION);
        int maxX = floor(maxPlayerX + HORIZONTAL_SHAPE_EXTENSION - EPSILON);
        int minZ = floor(minPlayerZ - HORIZONTAL_SHAPE_EXTENSION);
        int maxZ = floor(maxPlayerZ + HORIZONTAL_SHAPE_EXTENSION - EPSILON);
        int minY = Math.max(world.getMinHeight(), floor(y - SUPPORT_DISTANCE - MAX_SHAPE_HEIGHT));
        int maxY = Math.min(world.getMaxHeight() - 1, floor(y + height - EPSILON));
        long count = (long) (maxX - minX + 1) * (maxZ - minZ + 1) * Math.max(0, maxY - minY + 1);
        if (count > MAX_BLOCKS_PER_SAMPLE) {
            return Sample.unknown();
        }
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!Bukkit.isOwnedByCurrentRegion(world, cx, cz) || !world.isChunkLoaded(cx, cz)) {
                    return Sample.unknown();
                }
            }
        }

        boolean supported = false, powder = false, slowing = false, liquid = false, climbing = false;
        boolean honey = false, embedded = false, ice = false, slime = false, soul = false;
        Material feet = Material.AIR;
        int footX = floor(x), footY = floor(y), footZ = floor(z);
        double coreHalfWidth = Math.min(0.10, width * 0.2);
        double coreBottom = y + Math.min(0.10, height * 0.2);
        double coreTop = y + Math.min(0.60, height * 0.7);

        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                boolean horizontalOverlap = overlaps(minPlayerX, maxPlayerX, bx, bx + 1.0)
                        && overlaps(minPlayerZ, maxPlayerZ, bz, bz + 1.0);
                boolean nearHorizontally = distance(minPlayerX, maxPlayerX, bx, bx + 1.0) <= HONEY_REACH + EPSILON
                        && distance(minPlayerZ, maxPlayerZ, bz, bz + 1.0) <= HONEY_REACH + EPSILON;
                for (int by = minY; by <= maxY; by++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    Material type = block.getType();
                    if (bx == footX && by == footY && bz == footZ) {
                        feet = type;
                    }
                    boolean bodyOverlap = horizontalOverlap && overlaps(y, y + height, by, by + 1.0);
                    if (type == Material.POWDER_SNOW && horizontalOverlap
                            && y - SUPPORT_DISTANCE <= by + 1.0 + EPSILON && y + height > by + EPSILON) {
                        // Includes the top contact with leather boots as well
                        // as feet/body inside powder snow, not merely proximity.
                        powder = true;
                    }
                    if (bodyOverlap) {
                        liquid |= type == Material.WATER || type == Material.LAVA || type == Material.BUBBLE_COLUMN;
                        slowing |= type == Material.COBWEB || type == Material.SWEET_BERRY_BUSH;
                        climbing |= Tag.CLIMBABLE.isTagged(type);
                    }
                    if (type == Material.HONEY_BLOCK && nearHorizontally
                            && overlaps(y, y + height, by, by + 1.0)) {
                        honey = true;
                    }
                    if (type.isAir() || type == Material.WATER || type == Material.LAVA
                            || type == Material.BUBBLE_COLUMN || type == Material.POWDER_SNOW || by > coreTop + EPSILON
                            || by + MAX_SHAPE_HEIGHT < y - SUPPORT_DISTANCE - EPSILON) {
                        continue;
                    }
                    // Boxes may be non-full (slabs, stairs, fences, carpets,
                    // trapdoors, etc.); Material.isSolid() is not enough.
                    for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
                        double bxMin = bx + box.getMinX(), bxMax = bx + box.getMaxX();
                        double byMin = by + box.getMinY(), byMax = by + box.getMaxY();
                        double bzMin = bz + box.getMinZ(), bzMax = bz + box.getMaxZ();
                        double verticalGap = y - byMax;
                        if (verticalGap >= -MAX_SUPPORT_PENETRATION - EPSILON
                                && verticalGap <= SUPPORT_DISTANCE + EPSILON
                                && overlaps(minPlayerX, maxPlayerX, bxMin, bxMax)
                                && overlaps(minPlayerZ, maxPlayerZ, bzMin, bzMax)) {
                            supported = true;
                            ice |= type == Material.ICE || type == Material.PACKED_ICE
                                    || type == Material.BLUE_ICE || type == Material.FROSTED_ICE;
                            slime |= type == Material.SLIME_BLOCK || type == Material.HONEY_BLOCK;
                            soul |= type == Material.SOUL_SAND || type == Material.SOUL_SOIL;
                        }
                        // Require a sizeable inner body core to be fully within
                        // one real collision box. Grazing a stair or slab is not
                        // sufficient evidence of phasing.
                        if (x - coreHalfWidth >= bxMin + EPSILON && x + coreHalfWidth <= bxMax - EPSILON
                                && z - coreHalfWidth >= bzMin + EPSILON && z + coreHalfWidth <= bzMax - EPSILON
                                && coreBottom >= byMin + EPSILON && coreTop <= byMax - EPSILON) {
                            embedded = true;
                        }
                    }
                }
            }
        }
        int headY = floor(y + 1.5);
        Material head = headY >= world.getMinHeight() && headY < world.getMaxHeight()
                ? world.getBlockAt(footX, headY, footZ).getType() : Material.AIR;
        boolean headWater = head == Material.WATER || head == Material.BUBBLE_COLUMN;
        return new Sample(true, supported, powder, slowing, liquid, climbing, honey, embedded,
                ice, slime, soul, feet, headWater);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static boolean overlaps(double min1, double max1, double min2, double max2) {
        return max1 > min2 + EPSILON && max2 > min1 + EPSILON;
    }

    private static double distance(double min1, double max1, double min2, double max2) {
        return Math.max(0.0, Math.max(min2 - max1, min1 - max2));
    }
}
