package dev.superseller.chunkvoter.hook;

import dev.superseller.chunkvoter.config.ChunkVoterConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional, reflective integration with WorldGuard for region ownership.
 *
 * <p>WorldGuard is a Bukkit-only plugin and is not Folia-safe, so it is
 * detected at runtime via {@link org.bukkit.plugin.PluginManager#getPlugin}
 * and accessed purely through reflection. When WorldGuard is absent the
 * plugin behaves as an unclaimed area: any player may start/vote. When a
 * chunk sits inside a region, only region owners (and admins/bypass) can
 * decide its regeneration.
 */
public final class WorldGuardHook {

    private final JavaPlugin plugin;
    private final Logger logger;

    private boolean present = false;
    private boolean enabled = false;
    private String version = "none";

    private Method regionContainerMethod;      // WorldGuardPlugin#getRegionContainer()
    private Method containerGet;               // RegionContainer#get(World)
    private Method regionManagerGetApplicable; // RegionManager#getApplicableRegions(BlockVector3)
    private Method regionGetOwners;            // ProtectedRegion#getOwners()
    private Method blockVector3At;             // BlockVector3#at(int,int,int)

    public WorldGuardHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Detects WorldGuard and, when enabled by config, wires up reflection. */
    public void init(ChunkVoterConfig config) {
        present = false;
        enabled = false;
        version = "none";

        if (!config.wgEnabled()) {
            logger.info("WorldGuard integration disabled by config.");
            return;
        }
        Object wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null) {
            logger.info("WorldGuard not found — owner control disabled.");
            return;
        }
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            regionContainerMethod = wgClass.getMethod("getRegionContainer");
            Object container = regionContainerMethod.invoke(wg);
            containerGet = container.getClass().getMethod("get", World.class);

            Class<?> bv = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            blockVector3At = bv.getMethod("at", int.class, int.class, int.class);

            Class<?> rmClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionManager");
            regionManagerGetApplicable = rmClass.getMethod("getApplicableRegions", bv);

            Class<?> prClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            regionGetOwners = prClass.getMethod("getOwners");

            present = true;
            enabled = true;
            version = wg.getClass().getSimpleName();
        } catch (Throwable t) {
            present = false;
            enabled = false;
            logger.warning("WorldGuard present but hook setup failed: " + t.getMessage());
        }
        logger.info("WorldGuard hook: " + describe());
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String describe() {
        if (enabled) {
            return "enabled (WorldGuard)";
        }
        return present ? "disabled (WorldGuard present)" : "disabled (WorldGuard not loaded)";
    }

    /** True when the chunk's centre sits inside at least one WG region. */
    public boolean isProtected(World world, int x, int z) {
        return !regions(world, x, z).isEmpty();
    }

    /** Union of owner UUIDs of every region covering the chunk's centre. */
    public Set<UUID> owners(World world, int x, int z) {
        Set<UUID> result = new HashSet<>();
        for (Object region : regions(world, x, z)) {
            try {
                Object ownersObj = regionGetOwners.invoke(region);
                if (ownersObj == null) {
                    continue;
                }
                Method getId = ownersObj.getClass().getMethod("getUniqueIds");
                Object ids = getId.invoke(ownersObj);
                if (ids instanceof Collection<?> col) {
                    for (Object id : col) {
                        if (id instanceof UUID uuid) {
                            result.add(uuid);
                        } else if (id != null) {
                            try {
                                result.add(UUID.fromString(String.valueOf(id)));
                            } catch (IllegalArgumentException ignored) {
                                // non-UUID id
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // skip this region
            }
        }
        return result;
    }

    /** Builds the applicable-region set for a chunk, or null on any failure. */
    private Object applicableRegionSet(World world, int x, int z) {
        if (enabled) {
            try {
                Object wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
                if (wg == null) {
                    return null;
                }
                Object container = regionContainerMethod.invoke(wg);
                Object manager = containerGet.invoke(container, world);
                if (manager == null) {
                    return null;
                }
                Object pos = blockVector3At.invoke(null, (x << 4) + 8, 64, (z << 4) + 8);
                return regionManagerGetApplicable.invoke(manager, pos);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private List<Object> regions(World world, int x, int z) {
        List<Object> out = new ArrayList<>();
        Object set = applicableRegionSet(world, x, z);
        if (set == null) {
            return out;
        }
        if (set instanceof Iterable<?> iterable) {
            for (Object r : iterable) {
                out.add(r);
            }
            return out;
        }
        try {
            Method m = set.getClass().getMethod("iterator");
            Iterator<?> it = (Iterator<?>) m.invoke(set);
            while (it.hasNext()) {
                out.add(it.next());
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return out;
    }
}
