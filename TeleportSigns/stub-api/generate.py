#!/usr/bin/env python3
"""Emit compile-only Paper API stubs used by TeleportSigns offline builds."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "src"


def write(rel: str, content: str) -> None:
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.strip() + "\n", encoding="utf-8")


write(
    "net/kyori/adventure/text/Component.java",
    """
package net.kyori.adventure.text;
public interface Component {}
""",
)

write(
    "net/kyori/adventure/text/minimessage/MiniMessage.java",
    """
package net.kyori.adventure.text.minimessage;
import net.kyori.adventure.text.Component;
public interface MiniMessage {
    static MiniMessage miniMessage() { return new MiniMessage() {
        public Component deserialize(String input) { return new Component() {}; }
    }; }
    Component deserialize(String input);
}
""",
)

write(
    "org/bukkit/plugin/Plugin.java",
    """
package org.bukkit.plugin;
import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;
public interface Plugin {
    Server getServer();
    File getDataFolder();
    Logger getLogger();
    String getName();
}
""",
)

write(
    "org/bukkit/plugin/PluginManager.java",
    """
package org.bukkit.plugin;
import org.bukkit.event.Listener;
public interface PluginManager {
    void registerEvents(Listener listener, Plugin plugin);
    Plugin getPlugin(String name);
}
""",
)

write(
    "org/bukkit/plugin/ServicesManager.java",
    """
package org.bukkit.plugin;
public interface ServicesManager {
    <T> RegisteredServiceProvider<T> getRegistration(Class<T> service);
}
""",
)

write(
    "org/bukkit/plugin/RegisteredServiceProvider.java",
    """
package org.bukkit.plugin;
public class RegisteredServiceProvider<T> {
    private final T provider;
    public RegisteredServiceProvider(T provider) { this.provider = provider; }
    public T getProvider() { return provider; }
}
""",
)

write(
    "org/bukkit/plugin/java/JavaPlugin.java",
    """
package org.bukkit.plugin.java;
import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
public abstract class JavaPlugin implements Plugin {
    public abstract void onEnable();
    public abstract void onDisable();
    public File getDataFolder() { return new File("target/test-data/TeleportSigns"); }
    public InputStream getResource(String filename) { return null; }
    public void saveResource(String path, boolean replace) {}
    public void saveDefaultConfig() {}
    public void reloadConfig() {}
    public org.bukkit.configuration.file.FileConfiguration getConfig() {
        return new org.bukkit.configuration.file.YamlConfiguration();
    }
    public Server getServer() { return Bukkit.getServer(); }
    public Logger getLogger() { return Logger.getLogger("TeleportSigns"); }
    public String getName() { return "TeleportSigns"; }
    public PluginCommand getCommand(String name) { return new PluginCommand(name); }
}
""",
)

write(
    "org/bukkit/Server.java",
    """
package org.bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
public interface Server {
    PluginManager getPluginManager();
    ServicesManager getServicesManager();
    BukkitScheduler getScheduler();
}
""",
)

write(
    "org/bukkit/Bukkit.java",
    """
package org.bukkit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
public final class Bukkit {
    private static Server server;
    private Bukkit() {}
    public static Server getServer() { return server; }
    public static void setServer(Server s) { server = s; }
    public static org.bukkit.plugin.PluginManager getPluginManager() { return server.getPluginManager(); }
    public static ServicesManager getServicesManager() { return server.getServicesManager(); }
    public static BukkitScheduler getScheduler() { return server.getScheduler(); }
    public static World getWorld(String name) { return null; }
    public static List<World> getWorlds() { return new ArrayList<World>(); }
    public static OfflinePlayer getOfflinePlayer(UUID uuid) { return new OfflinePlayer() {
        public UUID getUniqueId() { return uuid; }
        public String getName() { return "offline"; }
    }; }
}
""",
)

write(
    "org/bukkit/World.java",
    """
package org.bukkit;
import org.bukkit.block.Block;
public interface World {
    String getName();
    int getMinHeight();
    int getMaxHeight();
    Block getBlockAt(int x, int y, int z);
}
""",
)

write(
    "org/bukkit/Location.java",
    """
package org.bukkit;
public class Location implements Cloneable {
    private World world;
    private double x, y, z;
    private float yaw, pitch;
    public Location(World world, double x, double y, double z) {
        this.world = world; this.x = x; this.y = y; this.z = z;
    }
    public World getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public int getBlockX() { return (int) Math.floor(x); }
    public int getBlockY() { return (int) Math.floor(y); }
    public int getBlockZ() { return (int) Math.floor(z); }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }
    public Location clone() {
        Location c = new Location(world, x, y, z);
        c.yaw = yaw; c.pitch = pitch; return c;
    }
    public Location add(double dx, double dy, double dz) {
        x += dx; y += dy; z += dz; return this;
    }
}
""",
)

write(
    "org/bukkit/Material.java",
    """
package org.bukkit;
public enum Material {
    AIR, CAVE_AIR, VOID_AIR, STONE, LAVA, FIRE, WATER;
    public boolean isSolid() { return this == STONE; }
}
""",
)

write(
    "org/bukkit/NamespacedKey.java",
    """
package org.bukkit;
import org.bukkit.plugin.Plugin;
public class NamespacedKey {
    public NamespacedKey(Plugin plugin, String key) {}
}
""",
)

write(
    "org/bukkit/OfflinePlayer.java",
    """
package org.bukkit;
import java.util.UUID;
public interface OfflinePlayer {
    UUID getUniqueId();
    String getName();
}
""",
)

write(
    "org/bukkit/Particle.java",
    """
package org.bukkit;
public enum Particle { PORTAL, REVERSE_PORTAL, POOF, FLAME }
""",
)

write(
    "org/bukkit/Sound.java",
    """
package org.bukkit;
public enum Sound { ENTITY_ENDERMAN_TELEPORT, ENTITY_EXPERIENCE_ORB_PICKUP }
""",
)

write(
    "org/bukkit/FluidCollisionMode.java",
    """
package org.bukkit;
public enum FluidCollisionMode { NEVER, SOURCE_ONLY, ALWAYS }
""",
)

write(
    "org/bukkit/block/Block.java",
    """
package org.bukkit.block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
public interface Block {
    World getWorld();
    int getX();
    int getY();
    int getZ();
    Material getType();
    BlockState getState();
    Location getLocation();
}
""",
)

write(
    "org/bukkit/block/BlockState.java",
    """
package org.bukkit.block;
import org.bukkit.persistence.PersistentDataContainer;
public interface BlockState {
    Block getBlock();
    PersistentDataContainer getPersistentDataContainer();
    boolean update();
    boolean update(boolean force, boolean applyPhysics);
}
""",
)

write(
    "org/bukkit/block/Sign.java",
    """
package org.bukkit.block;
public interface Sign extends BlockState {
    void setWaxed(boolean waxed);
    boolean isWaxed();
}
""",
)

write(
    "org/bukkit/command/CommandSender.java",
    """
package org.bukkit.command;
import net.kyori.adventure.text.Component;
import org.bukkit.permissions.Permissible;
public interface CommandSender extends Permissible {
    void sendMessage(String message);
    void sendMessage(Component message);
}
""",
)

write(
    "org/bukkit/command/Command.java",
    """
package org.bukkit.command;
public class Command {
    public String getName() { return "teleportsigns"; }
}
""",
)

write(
    "org/bukkit/command/CommandExecutor.java",
    """
package org.bukkit.command;
public interface CommandExecutor {
    boolean onCommand(CommandSender sender, Command command, String label, String[] args);
}
""",
)

write(
    "org/bukkit/command/TabCompleter.java",
    """
package org.bukkit.command;
import java.util.List;
public interface TabCompleter {
    List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args);
}
""",
)

write(
    "org/bukkit/command/PluginCommand.java",
    """
package org.bukkit.command;
public class PluginCommand extends Command {
    public PluginCommand(String name) {}
    public void setExecutor(CommandExecutor executor) {}
    public void setTabCompleter(TabCompleter completer) {}
}
""",
)

write(
    "org/bukkit/permissions/Permissible.java",
    """
package org.bukkit.permissions;
public interface Permissible {
    boolean hasPermission(String permission);
}
""",
)

write(
    "org/bukkit/configuration/ConfigurationSection.java",
    """
package org.bukkit.configuration;
import java.util.Set;
public interface ConfigurationSection {
    Set<String> getKeys(boolean deep);
    String getString(String path);
    String getString(String path, String def);
    int getInt(String path);
    int getInt(String path, int def);
    double getDouble(String path, double def);
    boolean getBoolean(String path, boolean def);
    java.util.List<String> getStringList(String path);
    boolean isConfigurationSection(String path);
    ConfigurationSection getConfigurationSection(String path);
    void set(String path, Object value);
}
""",
)

write(
    "org/bukkit/configuration/file/FileConfiguration.java",
    """
package org.bukkit.configuration.file;
import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.ConfigurationSection;
public abstract class FileConfiguration implements ConfigurationSection {
    public abstract void save(File file) throws IOException;
}
""",
)

write(
    "org/bukkit/configuration/file/YamlConfiguration.java",
    """
package org.bukkit.configuration.file;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
public class YamlConfiguration extends FileConfiguration {
    private final Map<String, Object> data = new HashMap<String, Object>();
    public static YamlConfiguration loadConfiguration(File file) { return new YamlConfiguration(); }
    public void save(File file) throws IOException {}
    public Set<String> getKeys(boolean deep) { return data.keySet(); }
    public String getString(String path) { return getString(path, null); }
    public String getString(String path, String def) {
        Object v = data.get(path); return v == null ? def : String.valueOf(v);
    }
    public int getInt(String path) { return getInt(path, 0); }
    public int getInt(String path, int def) {
        Object v = data.get(path); return v instanceof Number ? ((Number) v).intValue() : def;
    }
    public double getDouble(String path, double def) {
        Object v = data.get(path); return v instanceof Number ? ((Number) v).doubleValue() : def;
    }
    public boolean getBoolean(String path, boolean def) {
        Object v = data.get(path); return v instanceof Boolean ? ((Boolean) v).booleanValue() : def;
    }
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object v = data.get(path);
        return v instanceof List ? (List<String>) v : Collections.<String>emptyList();
    }
    public boolean isConfigurationSection(String path) { return false; }
    public ConfigurationSection getConfigurationSection(String path) { return this; }
    public void set(String path, Object value) { data.put(path, value); }
}
""",
)

write(
    "org/bukkit/entity/Entity.java",
    """
package org.bukkit.entity;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
public interface Entity {
    UUID getUniqueId();
    Location getLocation();
    boolean teleport(Location location);
    CompletableFuture<Boolean> teleportAsync(Location location);
}
""",
)

write(
    "org/bukkit/entity/Player.java",
    """
package org.bukkit.entity;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
public interface Player extends Entity, OfflinePlayer, CommandSender {
    boolean isOnline();
    boolean isSneaking();
    World getWorld();
    Block getTargetBlockExact(int maxDistance);
    Block getTargetBlockExact(int maxDistance, FluidCollisionMode mode);
    void playSound(Location location, Sound sound, float volume, float pitch);
    void spawnParticle(Particle particle, Location location, int count, double ox, double oy, double oz, double extra);
}
""",
)

write(
    "org/bukkit/event/Event.java",
    """
package org.bukkit.event;
public abstract class Event {}
""",
)

write(
    "org/bukkit/event/Cancellable.java",
    """
package org.bukkit.event;
public interface Cancellable {
    boolean isCancelled();
    void setCancelled(boolean cancel);
}
""",
)

write(
    "org/bukkit/event/Listener.java",
    """
package org.bukkit.event;
public interface Listener {}
""",
)

write(
    "org/bukkit/event/EventHandler.java",
    """
package org.bukkit.event;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {
    EventPriority priority() default EventPriority.NORMAL;
    boolean ignoreCancelled() default false;
}
""",
)

write(
    "org/bukkit/event/EventPriority.java",
    """
package org.bukkit.event;
public enum EventPriority { LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR }
""",
)

write(
    "org/bukkit/event/block/Action.java",
    """
package org.bukkit.event.block;
public enum Action { LEFT_CLICK_BLOCK, RIGHT_CLICK_BLOCK, LEFT_CLICK_AIR, RIGHT_CLICK_AIR, PHYSICAL }
""",
)

write(
    "org/bukkit/event/block/BlockEvent.java",
    """
package org.bukkit.event.block;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
public abstract class BlockEvent extends Event {
    public Block getBlock() { return null; }
}
""",
)

write(
    "org/bukkit/event/block/BlockBreakEvent.java",
    """
package org.bukkit.event.block;
import org.bukkit.event.Cancellable;
public class BlockBreakEvent extends BlockEvent implements Cancellable {
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
""",
)

write(
    "org/bukkit/event/block/BlockBurnEvent.java",
    """
package org.bukkit.event.block;
import org.bukkit.event.Cancellable;
public class BlockBurnEvent extends BlockEvent implements Cancellable {
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
""",
)

write(
    "org/bukkit/event/block/BlockExplodeEvent.java",
    """
package org.bukkit.event.block;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
public class BlockExplodeEvent extends BlockEvent implements Cancellable {
    public List<Block> blockList() { return new ArrayList<Block>(); }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
""",
)

write(
    "org/bukkit/event/entity/EntityEvent.java",
    """
package org.bukkit.event.entity;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
public abstract class EntityEvent extends Event {
    public Entity getEntity() { return null; }
}
""",
)

write(
    "org/bukkit/event/entity/EntityDamageEvent.java",
    """
package org.bukkit.event.entity;
import org.bukkit.event.Cancellable;
public class EntityDamageEvent extends EntityEvent implements Cancellable {
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
""",
)

write(
    "org/bukkit/event/entity/EntityExplodeEvent.java",
    """
package org.bukkit.event.entity;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
public class EntityExplodeEvent extends EntityEvent implements Cancellable {
    public List<Block> blockList() { return new ArrayList<Block>(); }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
""",
)

write(
    "org/bukkit/event/player/PlayerEvent.java",
    """
package org.bukkit.event.player;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
public abstract class PlayerEvent extends Event {
    public Player getPlayer() { return null; }
}
""",
)

write(
    "org/bukkit/event/player/PlayerInteractEvent.java",
    """
package org.bukkit.event.player;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
public class PlayerInteractEvent extends PlayerEvent implements Cancellable {
    public Action getAction() { return Action.RIGHT_CLICK_BLOCK; }
    public EquipmentSlot getHand() { return EquipmentSlot.HAND; }
    public Block getClickedBlock() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
""",
)

write(
    "org/bukkit/event/player/PlayerMoveEvent.java",
    """
package org.bukkit.event.player;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {
    public Location getTo() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
""",
)

write(
    "org/bukkit/event/player/PlayerQuitEvent.java",
    """
package org.bukkit.event.player;
public class PlayerQuitEvent extends PlayerEvent {}
""",
)

write(
    "org/bukkit/inventory/EquipmentSlot.java",
    """
package org.bukkit.inventory;
public enum EquipmentSlot { HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET }
""",
)

write(
    "org/bukkit/persistence/PersistentDataContainer.java",
    """
package org.bukkit.persistence;
import org.bukkit.NamespacedKey;
public interface PersistentDataContainer {
    <T, Z> void set(NamespacedKey key, PersistentDataType<T, Z> type, Z value);
    <T, Z> Z get(NamespacedKey key, PersistentDataType<T, Z> type);
    <T, Z> boolean has(NamespacedKey key, PersistentDataType<T, Z> type);
    void remove(NamespacedKey key);
}
""",
)

write(
    "org/bukkit/persistence/PersistentDataType.java",
    """
package org.bukkit.persistence;
public interface PersistentDataType<T, Z> {
    PersistentDataType<String, String> STRING = new PersistentDataType<String, String>() {};
}
""",
)

write(
    "org/bukkit/scheduler/BukkitTask.java",
    """
package org.bukkit.scheduler;
public interface BukkitTask {
    void cancel();
}
""",
)

write(
    "org/bukkit/scheduler/BukkitScheduler.java",
    """
package org.bukkit.scheduler;
import org.bukkit.plugin.Plugin;
public interface BukkitScheduler {
    BukkitTask runTask(Plugin plugin, Runnable task);
    BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay);
}
""",
)

print("wrote stubs under", ROOT)
