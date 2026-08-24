package dev.superseller.shardtools.tools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Breadth-first tree detection used by the Shard Axe. Works on a simple
 * {@link Grid} abstraction so the algorithm can be unit tested without a
 * real world. Pure logic.
 */
public final class TreeFeller {

    /** Minimal world view: material name at a position, or {@code null} if unloaded. */
    public interface Grid {
        String materialNameAt(int x, int y, int z);
    }

    private static final int[][] NEIGHBOURS = buildNeighbours();

    private TreeFeller() {
    }

    private static int[][] buildNeighbours() {
        List<int[]> offsets = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        offsets.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        return offsets.toArray(new int[0][]);
    }

    /**
     * Collects all blocks belonging to the tree that contains the origin log.
     *
     * @param originX        origin log coordinates
     * @param originMaterial material name of the origin log (e.g. "OAK_LOG")
     * @param grid           world view
     * @param isLog          decides whether a material name counts as a log
     * @param sameMaterialOnly only chain through the origin material
     * @param maxBlocks      hard cap, always respected
     * @return positions of the additional logs (origin excluded)
     */
    public static List<int[]> collect(int originX, int originY, int originZ,
                                      String originMaterial, Grid grid,
                                      Predicate<String> isLog,
                                      boolean sameMaterialOnly, int maxBlocks) {
        List<int[]> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        visited.add(key(originX, originY, originZ));
        queue.add(new int[]{originX, originY, originZ});
        int budget = Math.max(0, maxBlocks - 1);

        while (!queue.isEmpty() && result.size() < budget) {
            int[] current = queue.poll();
            for (int[] offset : NEIGHBOURS) {
                if (result.size() >= budget) {
                    break;
                }
                int x = current[0] + offset[0];
                int y = current[1] + offset[1];
                int z = current[2] + offset[2];
                String key = key(x, y, z);
                if (visited.contains(key)) {
                    continue;
                }
                visited.add(key);
                String material = grid.materialNameAt(x, y, z);
                if (material == null) {
                    continue;
                }
                if (sameMaterialOnly ? !material.equals(originMaterial) : !isLog.test(material)) {
                    continue;
                }
                int[] found = new int[]{x, y, z};
                result.add(found);
                queue.add(found);
            }
        }
        return result;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * Collects leaves directly adjacent to the given log positions - the
     * "whole tree" DonutSMP behaviour (mines all logs and leaves connected
     * to the mined log). Does not chain through leaves so neighbouring
     * trees stay intact.
     *
     * @param logs   already collected log positions
     * @param grid   world view
     * @param isLeaf decides whether a material name counts as a leaf
     * @param cap    maximum leaves returned
     * @return leaf positions (never contains log positions)
     */
    public static List<int[]> collectLeaves(List<int[]> logs, Grid grid,
                                            Predicate<String> isLeaf, int cap) {
        List<int[]> leaves = new ArrayList<>();
        if (logs == null || logs.isEmpty() || cap <= 0) {
            return leaves;
        }
        Set<String> leafKeys = new HashSet<>();
        for (int[] log : logs) {
            for (int[] offset : NEIGHBOURS) {
                if (leaves.size() >= cap) {
                    return leaves;
                }
                int x = log[0] + offset[0];
                int y = log[1] + offset[1];
                int z = log[2] + offset[2];
                String key = key(x, y, z);
                if (leafKeys.contains(key)) {
                    continue;
                }
                String material = grid.materialNameAt(x, y, z);
                if (material == null || !isLeaf.test(material)) {
                    continue;
                }
                leafKeys.add(key);
                leaves.add(new int[]{x, y, z});
            }
        }
        return leaves;
    }
}
