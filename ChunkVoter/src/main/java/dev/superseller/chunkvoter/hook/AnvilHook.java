package dev.superseller.chunkvoter.hook;

import dev.superseller.chunkvoter.ChunkVoterPlugin;
import dev.superseller.chunkvoter.config.ChunkVoterConfig;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Direct Anvil (.mca) chunk regeneration hook that functions without WorldEdit.
 *
 * <p>Modern Paper/Purpur/Folia platforms do not implement Bukkit's deprecated
 * {@link World#regenerateChunk(int, int)} API. When WorldEdit is absent, this hook
 * unloads the chunk without saving, clears the chunk's 4-byte sector offset/length
 * and timestamp in the Anvil region file headers (.mca), and loads the chunk back so
 * that the server's world generator reconstructs it fresh from the seed.</p>
 */
public final class AnvilHook {

    public enum Status {
        SUCCESS,
        DISABLED,
        FAILED
    }

    public record Result(Status status, Throwable error) {
        public static Result success() {
            return new Result(Status.SUCCESS, null);
        }

        public static Result disabled() {
            return new Result(Status.DISABLED, null);
        }

        public static Result failed(Throwable error) {
            return new Result(Status.FAILED, error);
        }
    }

    private static final byte[] ZERO_BYTES = new byte[]{0, 0, 0, 0};

    private final ChunkVoterPlugin plugin;
    private volatile boolean enabled;
    private volatile boolean safeTeleport;

    public AnvilHook(ChunkVoterPlugin plugin) {
        this.plugin = plugin;
    }

    public void init(ChunkVoterConfig config) {
        this.enabled = config.anvilEnabled();
        this.safeTeleport = config.anvilSafeTeleport();
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean safeTeleport() {
        return safeTeleport;
    }

    public String describe() {
        return enabled ? "enabled" : "disabled";
    }

    /**
     * Regenerates a chunk directly using the Anvil (.mca) region format.
     * This method must be called from the chunk's synchronized context
     * (e.g. region thread on Folia).
     *
     * @param world  the Bukkit world
     * @param chunkX the chunk X coordinate
     * @param chunkZ the chunk Z coordinate
     * @return the result of the regeneration operation
     */
    public Result regenerate(World world, int chunkX, int chunkZ) {
        if (!enabled) {
            return Result.disabled();
        }
        if (world == null) {
            return Result.failed(new IllegalArgumentException("World cannot be null"));
        }

        try {
            // 1. Relocate players standing inside the chunk to safety before unloading
            if (safeTeleport) {
                relocatePlayers(world, chunkX, chunkZ);
            }

            // 2. Unload the chunk without saving modified state
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                boolean unloaded = world.unloadChunk(chunkX, chunkZ, false);
                if (!unloaded) {
                    world.unloadChunkRequest(chunkX, chunkZ);
                }
            }

            // 3. Clear chunk location headers in all matching MCA region files
            clearAnvilHeaders(world, chunkX, chunkZ);

            // 4. Force-load the chunk back (server regenerates from seed when header offset is 0)
            boolean loaded = world.loadChunk(chunkX, chunkZ, true);
            if (!loaded) {
                Chunk c = world.getChunkAt(chunkX, chunkZ);
                if (c == null) {
                    return Result.failed(new IllegalStateException("Failed to load chunk after Anvil header wipe"));
                }
            }

            // 5. Refresh the chunk for nearby players
            refresh(world, chunkX, chunkZ);

            return Result.success();
        } catch (Throwable t) {
            return Result.failed(t);
        }
    }

    private void relocatePlayers(World world, int chunkX, int chunkZ) {
        int minBlockX = chunkX << 4;
        int maxBlockX = minBlockX + 15;
        int minBlockZ = chunkZ << 4;
        int maxBlockZ = minBlockZ + 15;

        for (Player p : world.getPlayers()) {
            Location loc = p.getLocation();
            int bx = loc.getBlockX();
            int bz = loc.getBlockZ();
            if (bx >= minBlockX && bx <= maxBlockX && bz >= minBlockZ && bz <= maxBlockZ) {
                int safeX = minBlockX - 1;
                int safeZ = minBlockZ - 1;
                int safeY = Math.max(world.getMinHeight() + 1,
                        Math.min(world.getMaxHeight() - 2, world.getHighestBlockYAt(safeX, safeZ) + 1));
                Location safeLoc = new Location(world, safeX + 0.5, safeY, safeZ + 0.5, loc.getYaw(), loc.getPitch());
                try {
                    p.teleport(safeLoc);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void clearAnvilHeaders(World world, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, 32);
        int regionZ = Math.floorDiv(chunkZ, 32);
        int localX = Math.floorMod(chunkX, 32);
        int localZ = Math.floorMod(chunkZ, 32);
        int chunkIndex = localX + (localZ * 32);

        long locationOffset = chunkIndex * 4L;
        long timestampOffset = 4096L + (chunkIndex * 4L);

        List<File> mcaFiles = findMcaFiles(world, regionX, regionZ);
        for (File file : mcaFiles) {
            if (file.exists() && file.isFile() && file.canWrite()) {
                wipeMcaEntry(file, locationOffset, timestampOffset);
            }
        }
    }

    private void wipeMcaEntry(File file, long locationOffset, long timestampOffset) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            if (raf.length() >= 4096) {
                raf.seek(locationOffset);
                raf.write(ZERO_BYTES);
            }
            if (raf.length() >= 8192) {
                raf.seek(timestampOffset);
                raf.write(ZERO_BYTES);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not wipe Anvil MCA entry in " + file.getName() + ": " + t.getMessage());
        }
    }

    private List<File> findMcaFiles(World world, int regionX, int regionZ) {
        List<File> list = new ArrayList<>();
        File worldFolder = world.getWorldFolder();
        if (worldFolder == null || !worldFolder.isDirectory()) {
            return list;
        }

        String fileName = "r." + regionX + "." + regionZ + ".mca";
        List<String> subdirs = List.of("region", "poi", "entities");

        for (String sub : subdirs) {
            File dir = new File(worldFolder, sub);
            if (dir.isDirectory()) {
                list.add(new File(dir, fileName));
            }
        }

        File[] children = worldFolder.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && child.getName().startsWith("DIM")) {
                    for (String sub : subdirs) {
                        File dir = new File(child, sub);
                        if (dir.isDirectory()) {
                            list.add(new File(dir, fileName));
                        }
                    }
                }
            }
        }

        return list;
    }

    private void refresh(World world, int chunkX, int chunkZ) {
        try {
            world.refreshChunk(chunkX, chunkZ);
        } catch (Throwable t) {
            plugin.getLogger().fine("Could not refresh regenerated chunk "
                    + world.getName() + " (" + chunkX + ", " + chunkZ + "): " + t.getMessage());
        }
    }
}
