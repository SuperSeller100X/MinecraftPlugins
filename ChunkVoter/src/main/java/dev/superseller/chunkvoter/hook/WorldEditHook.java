package dev.superseller.chunkvoter.hook;

import dev.superseller.chunkvoter.ChunkVoterPlugin;
import dev.superseller.chunkvoter.config.ChunkVoterConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Optional, reflective WorldEdit integration for modern chunk regeneration.
 *
 * <p>Paper/Purpur intentionally do not implement Bukkit's deprecated
 * {@link World#regenerateChunk(int, int)} on current Minecraft versions. The
 * supported path is WorldEdit's regeneration implementation, which delegates to
 * the version-specific Bukkit/NMS adapter. ChunkVoter keeps WorldEdit as a soft
 * dependency by reflecting the same types WorldEdit uses for {@code //regen}.</p>
 */
public final class WorldEditHook {

    public enum Status {
        SUCCESS,
        DISABLED,
        UNAVAILABLE,
        FAILED
    }

    public record Result(Status status, Throwable error) {
        public static Result success() {
            return new Result(Status.SUCCESS, null);
        }

        public static Result of(Status status) {
            return new Result(status, null);
        }

        public static Result failed(Throwable error) {
            return new Result(Status.FAILED, error);
        }
    }

    private final ChunkVoterPlugin plugin;
    private volatile boolean enabled;
    private volatile boolean required;
    private volatile boolean present;
    private volatile ClassLoader worldEditClassLoader;

    public WorldEditHook(ChunkVoterPlugin plugin) {
        this.plugin = plugin;
    }

    public void init(ChunkVoterConfig config) {
        enabled = config.worldEditEnabled();
        required = config.worldEditRequired();
        present = detectWorldEdit();
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean required() {
        return required;
    }

    public boolean present() {
        return present;
    }

    public String describe() {
        if (!enabled) {
            return "disabled";
        }
        if (present) {
            return required ? "required, available" : "auto, available";
        }
        return required ? "required, missing" : "auto, missing";
    }

    /**
     * Regenerates the exact chunk area using WorldEdit's Bukkit implementation
     * adapter. This method must be called from the chunk's synchronized context.
     */
    public Result regenerate(World world, int chunkX, int chunkZ) {
        if (!enabled) {
            return Result.of(Status.DISABLED);
        }
        if (!detectWorldEdit()) {
            present = false;
            return Result.of(Status.UNAVAILABLE);
        }
        present = true;

        Object editSession = null;
        try {
            Object bukkitWorld = createBukkitWorld(world);
            Object region = createChunkRegion(bukkitWorld, world, chunkX, chunkZ);
            editSession = createEditSession(bukkitWorld);
            Object adapter = bukkitImplAdapter();

            boolean ok;
            if (adapter != null) {
                ok = invokeAdapterRegenerate(adapter, world, bukkitWorld, region, editSession);
            } else {
                ok = invokeBukkitWorldRegenerate(bukkitWorld, region, editSession);
            }

            flush(editSession);
            refresh(world, chunkX, chunkZ);
            return ok ? Result.success()
                    : Result.failed(new IllegalStateException("WorldEdit regeneration returned false"));
        } catch (InvocationTargetException e) {
            return Result.failed(e.getTargetException());
        } catch (Throwable t) {
            return Result.failed(t);
        } finally {
            close(editSession);
        }
    }

    private boolean detectWorldEdit() {
        Plugin we = plugin.getServer().getPluginManager().getPlugin("WorldEdit");
        if (we == null || !we.isEnabled()) {
            worldEditClassLoader = null;
            return false;
        }
        worldEditClassLoader = we.getClass().getClassLoader();
        return true;
    }

    private Class<?> worldEditClass(String name) throws ClassNotFoundException {
        ClassLoader loader = worldEditClassLoader;
        if (loader == null) {
            Plugin we = plugin.getServer().getPluginManager().getPlugin("WorldEdit");
            if (we != null) {
                loader = we.getClass().getClassLoader();
                worldEditClassLoader = loader;
            }
        }
        return Class.forName(name, true, loader == null ? getClass().getClassLoader() : loader);
    }

    private Object createBukkitWorld(World world) throws ReflectiveOperationException {
        Class<?> bukkitWorldClass = worldEditClass("com.sk89q.worldedit.bukkit.BukkitWorld");
        Constructor<?> ctor = bukkitWorldClass.getConstructor(World.class);
        return ctor.newInstance(world);
    }

    private Object createChunkRegion(Object bukkitWorld, World world, int chunkX, int chunkZ)
            throws ReflectiveOperationException {
        Class<?> vectorClass = worldEditClass("com.sk89q.worldedit.math.BlockVector3");
        Method at = vectorClass.getMethod("at", int.class, int.class, int.class);

        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;

        Object min = at.invoke(null, minX, minY, minZ);
        Object max = at.invoke(null, maxX, maxY, maxZ);

        Class<?> regionWorldClass = worldEditClass("com.sk89q.worldedit.world.World");
        Class<?> cuboidClass = worldEditClass("com.sk89q.worldedit.regions.CuboidRegion");
        try {
            return cuboidClass.getConstructor(regionWorldClass, vectorClass, vectorClass)
                    .newInstance(bukkitWorld, min, max);
        } catch (NoSuchMethodException ignored) {
            return cuboidClass.getConstructor(vectorClass, vectorClass).newInstance(min, max);
        }
    }

    private Object createEditSession(Object bukkitWorld) throws ReflectiveOperationException {
        Class<?> worldEditClass = worldEditClass("com.sk89q.worldedit.WorldEdit");
        Object worldEdit = worldEditClass.getMethod("getInstance").invoke(null);

        Class<?> weWorldClass = worldEditClass("com.sk89q.worldedit.world.World");
        try {
            return worldEditClass.getMethod("newEditSession", weWorldClass).invoke(worldEdit, bukkitWorld);
        } catch (NoSuchMethodException ignored) {
            Object factory = worldEditClass.getMethod("getEditSessionFactory").invoke(worldEdit);
            return factory.getClass().getMethod("getEditSession", weWorldClass, int.class)
                    .invoke(factory, bukkitWorld, -1);
        }
    }

    private Object bukkitImplAdapter() throws ReflectiveOperationException {
        Class<?> pluginClass = worldEditClass("com.sk89q.worldedit.bukkit.WorldEditPlugin");
        Object worldEditPlugin;
        try {
            worldEditPlugin = pluginClass.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            worldEditPlugin = plugin.getServer().getPluginManager().getPlugin("WorldEdit");
        }
        if (worldEditPlugin == null) {
            return null;
        }
        try {
            return pluginClass.getMethod("getBukkitImplAdapter").invoke(worldEditPlugin);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private boolean invokeAdapterRegenerate(Object adapter, World bukkitWorld, Object worldEditWorld,
                                            Object region, Object editSession) throws ReflectiveOperationException {
        Object options = createRegenOptions();
        Method method = Arrays.stream(adapter.getClass().getMethods())
                .filter(m -> m.getName().equals("regenerate"))
                .filter(m -> m.getParameterCount() == 4 || m.getParameterCount() == 3)
                .sorted(Comparator.comparingInt(Method::getParameterCount).reversed())
                .filter(m -> canSupply(m, bukkitWorld, worldEditWorld, region, editSession, options))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("No compatible BukkitImplAdapter#regenerate method found"));

        method.setAccessible(true);
        Object result = method.invoke(adapter, argsFor(method, bukkitWorld, worldEditWorld, region, editSession, options));
        return !(result instanceof Boolean) || (Boolean) result;
    }

    private boolean invokeBukkitWorldRegenerate(Object worldEditWorld, Object region, Object editSession)
            throws ReflectiveOperationException {
        Object options = createRegenOptions();
        Method method = Arrays.stream(worldEditWorld.getClass().getMethods())
                .filter(m -> m.getName().equals("regenerate"))
                .filter(m -> canSupply(m, null, worldEditWorld, region, editSession, options))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("No compatible BukkitWorld#regenerate method found"));
        method.setAccessible(true);
        Object result = method.invoke(worldEditWorld, argsFor(method, null, worldEditWorld, region, editSession, options));
        return !(result instanceof Boolean) || (Boolean) result;
    }

    private Object createRegenOptions() throws ReflectiveOperationException {
        try {
            Class<?> optionsClass = worldEditClass("com.sk89q.worldedit.world.RegenOptions");
            Object builder = optionsClass.getMethod("builder").invoke(null);
            try {
                Method regenBiomes = builder.getClass().getMethod("regenBiomes", boolean.class);
                regenBiomes.setAccessible(true);
                regenBiomes.invoke(builder, false);
            } catch (NoSuchMethodException ignored) {
                // Older WorldEdit builds may not expose biome options.
            }
            Method build = builder.getClass().getMethod("build");
            build.setAccessible(true);
            return build.invoke(builder);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private boolean canSupply(Method method, Object bukkitWorld, Object worldEditWorld, Object region,
                              Object editSession, Object options) {
        for (Class<?> type : method.getParameterTypes()) {
            if (valueFor(type, bukkitWorld, worldEditWorld, region, editSession, options) == Missing.VALUE) {
                return false;
            }
        }
        return true;
    }

    private Object[] argsFor(Method method, Object bukkitWorld, Object worldEditWorld, Object region,
                             Object editSession, Object options) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = valueFor(types[i], bukkitWorld, worldEditWorld, region, editSession, options);
        }
        return args;
    }

    private Object valueFor(Class<?> type, Object bukkitWorld, Object worldEditWorld, Object region,
                            Object editSession, Object options) {
        if (bukkitWorld != null && type.isInstance(bukkitWorld)) {
            return bukkitWorld;
        }
        if (region != null && type.isInstance(region)) {
            return region;
        }
        if (editSession != null && type.isInstance(editSession)) {
            return editSession;
        }
        if (options != null && type.isInstance(options)) {
            return options;
        }
        if (worldEditWorld != null && type.isInstance(worldEditWorld)) {
            return worldEditWorld;
        }
        return Missing.VALUE;
    }

    private void flush(Object editSession) {
        if (editSession == null) {
            return;
        }
        try {
            editSession.getClass().getMethod("flushSession").invoke(editSession);
        } catch (Throwable ignored) {
            // Newer WorldEdit flushes on close(); this is a best-effort nudge.
        }
    }

    private void close(Object editSession) {
        if (editSession == null) {
            return;
        }
        try {
            editSession.getClass().getMethod("close").invoke(editSession);
        } catch (Throwable ignored) {
            // Nothing useful to do during plugin-facing cleanup.
        }
    }

    private void refresh(World world, int chunkX, int chunkZ) {
        try {
            world.refreshChunk(chunkX, chunkZ);
        } catch (Throwable t) {
            plugin.getLogger().fine("Could not refresh regenerated chunk "
                    + world.getName() + " (" + chunkX + ", " + chunkZ + "): " + t.getMessage());
        }
    }

    private enum Missing {
        VALUE
    }
}
