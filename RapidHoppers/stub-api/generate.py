#!/usr/bin/env python3
"""Emit the compile-only Bukkit/Paper API stubs used by RapidHoppers offline builds.

These stubs exist ONLY so the plugin can be compiled and smoke-tested in
environments without access to repo.papermc.io (see build.sh). The real build
is `mvn -B clean package`, which compiles against the genuine
io.papermc.paper:paper-api artifact for Minecraft 26.2.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "src"

FILES = {
"net/kyori/adventure/text/Component.java": """
package net.kyori.adventure.text;
public interface Component {
    static Component text(String value) { return new Component() { public String toString() { return value; } }; }
}
""",
"net/kyori/adventure/text/minimessage/MiniMessage.java": """
package net.kyori.adventure.text.minimessage;
import net.kyori.adventure.text.Component;
public interface MiniMessage {
    static MiniMessage miniMessage() {
        return input -> new Component() { public String toString() { return input; } };
    }
    Component deserialize(String input);
}
""",
"org/bukkit/Bukkit.java": """
package org.bukkit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitScheduler;
public final class Bukkit {
    private static Server server;
    private Bukkit() {}
    public static Server getServer() { return server; }
    public static void setServer(Server s) { server = s; }
    public static org.bukkit.plugin.PluginManager getPluginManager() { return server.getPluginManager(); }
    public static BukkitScheduler getScheduler() { return server.getScheduler(); }
    public static List<World> getWorlds() { return server == null ? new ArrayList<World>() : server.getWorlds(); }
    public static World getWorld(String name) { return server == null ? null : server.getWorld(name); }
    public static Collection<? extends Player> getOnlinePlayers() {
        return server == null ? new ArrayList<Player>() : server.getOnlinePlayers();
    }
    public static Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return server.createInventory(holder, size, title);
    }
}
""",
"org/bukkit/Server.java": """
package org.bukkit;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
public interface Server {
    PluginManager getPluginManager();
    BukkitScheduler getScheduler();
    List<World> getWorlds();
    World getWorld(String name);
    Collection<? extends Player> getOnlinePlayers();
    Inventory createInventory(InventoryHolder holder, int size, Component title);
}
""",
"org/bukkit/World.java": """
package org.bukkit;
import java.util.Collection;
import java.util.List;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
public interface World {
    String getName();
    int getMinHeight();
    int getMaxHeight();
    Chunk[] getLoadedChunks();
    boolean isChunkLoaded(int x, int z);
    Chunk getChunkAt(int x, int z);
    List<Player> getPlayers();
    Collection<Entity> getNearbyEntities(Location location, double x, double y, double z);
}
""",
"org/bukkit/Chunk.java": """
package org.bukkit;
import java.util.Collection;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
public interface Chunk {
    int getX();
    int getZ();
    World getWorld();
    Collection<BlockState> getTileEntities();
    Entity[] getEntities();
}
""",
"org/bukkit/Location.java": """
package org.bukkit;
import org.bukkit.block.Block;
public class Location implements Cloneable {
    private World world;
    private double x, y, z;
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
    public Block getBlock() { return world == null ? null : world.getChunkAt(getBlockX() >> 4, getBlockZ() >> 4) == null ? null : null; }
    public Location add(double dx, double dy, double dz) { x += dx; y += dy; z += dz; return this; }
    public Location clone() { return new Location(world, x, y, z); }
}
""",
"org/bukkit/Material.java": """
package org.bukkit;
public enum Material {
    AIR, HOPPER, BARRIER, CLOCK, CHEST, MINECART, REDSTONE_TORCH, IRON_BARS, MAP, PAPER,
    COMPARATOR, RED_STAINED_GLASS_PANE, GRAY_STAINED_GLASS_PANE, STONE, DROPPER, DISPENSER;
    public boolean isAir() { return this == AIR; }
    public static Material matchMaterial(String name) {
        try { return valueOf(name); } catch (IllegalArgumentException ex) { return null; }
    }
}
""",
"org/bukkit/Sound.java": """
package org.bukkit;
public enum Sound {
    BLOCK_BARREL_OPEN, BLOCK_BARREL_CLOSE, UI_BUTTON_CLICK, ENTITY_EXPERIENCE_ORB_PICKUP,
    BLOCK_NOTE_BLOCK_BASS, BLOCK_LEVER_CLICK
}
""",
"org/bukkit/block/BlockFace.java": """
package org.bukkit.block;
public enum BlockFace {
    NORTH(0, 0, -1), EAST(1, 0, 0), SOUTH(0, 0, 1), WEST(-1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0);
    private final int x, y, z;
    BlockFace(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    public int getModX() { return x; }
    public int getModY() { return y; }
    public int getModZ() { return z; }
}
""",
"org/bukkit/block/Block.java": """
package org.bukkit.block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
public interface Block {
    World getWorld();
    int getX();
    int getY();
    int getZ();
    Material getType();
    BlockData getBlockData();
    BlockState getState();
    BlockState getState(boolean useSnapshot);
    Block getRelative(int dx, int dy, int dz);
    Block getRelative(BlockFace face);
    Location getLocation();
}
""",
"org/bukkit/block/BlockState.java": """
package org.bukkit.block;
public interface BlockState {
    Block getBlock();
    boolean update();
}
""",
"org/bukkit/block/Container.java": """
package org.bukkit.block;
import org.bukkit.inventory.InventoryHolder;
public interface Container extends BlockState, InventoryHolder {}
""",
"org/bukkit/block/Hopper.java": """
package org.bukkit.block;
public interface Hopper extends Container {}
""",
"org/bukkit/block/Dropper.java": """
package org.bukkit.block;
public interface Dropper extends Container {}
""",
"org/bukkit/block/Dispenser.java": """
package org.bukkit.block;
public interface Dispenser extends Container {}
""",
"org/bukkit/block/data/BlockData.java": """
package org.bukkit.block.data;
public interface BlockData {}
""",
"org/bukkit/block/data/Directional.java": """
package org.bukkit.block.data;
import org.bukkit.block.BlockFace;
public interface Directional extends BlockData {
    BlockFace getFacing();
}
""",
"org/bukkit/entity/Entity.java": """
package org.bukkit.entity;
import org.bukkit.Location;
import org.bukkit.World;
public interface Entity {
    Location getLocation();
    World getWorld();
    boolean isDead();
    void remove();
}
""",
"org/bukkit/entity/Item.java": """
package org.bukkit.entity;
import org.bukkit.inventory.ItemStack;
public interface Item extends Entity {
    ItemStack getItemStack();
    void setItemStack(ItemStack stack);
}
""",
"org/bukkit/entity/HumanEntity.java": """
package org.bukkit.entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
public interface HumanEntity extends Entity {
    void closeInventory();
    void openInventory(Inventory inventory);
    InventoryView getOpenInventory();
}
""",
"org/bukkit/entity/Player.java": """
package org.bukkit.entity;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
public interface Player extends HumanEntity, CommandSender {
    boolean isOnline();
    World getWorld();
    void playSound(Location location, Sound sound, float volume, float pitch);
}
""",
"org/bukkit/entity/minecart/HopperMinecart.java": """
package org.bukkit.entity.minecart;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryHolder;
public interface HopperMinecart extends Entity, InventoryHolder {
    boolean isEnabled();
}
""",
"org/bukkit/entity/minecart/StorageMinecart.java": """
package org.bukkit.entity.minecart;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryHolder;
public interface StorageMinecart extends Entity, InventoryHolder {}
""",
"org/bukkit/inventory/Inventory.java": """
package org.bukkit.inventory;
import java.util.HashMap;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
public interface Inventory {
    int getSize();
    InventoryType getType();
    Location getLocation();
    InventoryHolder getHolder();
    ItemStack getItem(int index);
    void setItem(int index, ItemStack item);
    ItemStack[] getContents();
    ItemStack[] getStorageContents();
    HashMap<Integer, ItemStack> addItem(ItemStack... items);
    List<HumanEntity> getViewers();
    void clear();
}
""",
"org/bukkit/inventory/InventoryHolder.java": """
package org.bukkit.inventory;
public interface InventoryHolder {
    Inventory getInventory();
}
""",
"org/bukkit/inventory/InventoryView.java": """
package org.bukkit.inventory;
public interface InventoryView {
    Inventory getTopInventory();
}
""",
"org/bukkit/inventory/ItemStack.java": """
package org.bukkit.inventory;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack implements Cloneable {
    private Material type;
    private int amount = 1;
    private ItemMeta meta;
    public ItemStack(Material type) { this.type = type; }
    public ItemStack(Material type, int amount) { this.type = type; this.amount = amount; }
    public Material getType() { return type; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public int getMaxStackSize() { return 64; }
    public boolean isSimilar(ItemStack other) { return other != null && other.type == type; }
    public ItemMeta getItemMeta() { return meta; }
    public void setItemMeta(ItemMeta meta) { this.meta = meta; }
    public ItemStack clone() { ItemStack c = new ItemStack(type, amount); c.meta = meta; return c; }
}
""",
"org/bukkit/inventory/meta/ItemMeta.java": """
package org.bukkit.inventory.meta;
import java.util.List;
import net.kyori.adventure.text.Component;
public interface ItemMeta {
    void displayName(Component component);
    void lore(List<Component> lore);
}
""",
"org/bukkit/event/Listener.java": """
package org.bukkit.event;
public interface Listener {}
""",
"org/bukkit/event/Event.java": """
package org.bukkit.event;
public abstract class Event {}
""",
"org/bukkit/event/Cancellable.java": """
package org.bukkit.event;
public interface Cancellable {
    boolean isCancelled();
    void setCancelled(boolean cancel);
}
""",
"org/bukkit/event/EventPriority.java": """
package org.bukkit.event;
public enum EventPriority { LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR }
""",
"org/bukkit/event/EventHandler.java": """
package org.bukkit.event;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {
    EventPriority priority() default EventPriority.NORMAL;
    boolean ignoreCancelled() default false;
}
""",
"org/bukkit/event/inventory/InventoryType.java": """
package org.bukkit.event.inventory;
public enum InventoryType { CHEST, HOPPER, DROPPER, DISPENSER, BARREL, PLAYER }
""",
"org/bukkit/event/inventory/ClickType.java": """
package org.bukkit.event.inventory;
public enum ClickType { LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, UNKNOWN }
""",
"org/bukkit/event/inventory/InventoryEvent.java": """
package org.bukkit.event.inventory;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
public abstract class InventoryEvent extends Event {
    protected Inventory inventory;
    public Inventory getInventory() { return inventory; }
}
""",
"org/bukkit/event/inventory/InventoryClickEvent.java": """
package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.Inventory;
public class InventoryClickEvent extends InventoryEvent implements Cancellable {
    private boolean cancelled;
    private HumanEntity who;
    private Inventory clicked;
    private int rawSlot;
    private ClickType click = ClickType.LEFT;
    public InventoryClickEvent(Inventory inventory, Inventory clicked, HumanEntity who, int rawSlot, ClickType click) {
        this.inventory = inventory; this.clicked = clicked; this.who = who; this.rawSlot = rawSlot; this.click = click;
    }
    public HumanEntity getWhoClicked() { return who; }
    public Inventory getClickedInventory() { return clicked; }
    public int getRawSlot() { return rawSlot; }
    public ClickType getClick() { return click; }
    public boolean isShiftClick() { return click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
""",
"org/bukkit/event/inventory/InventoryDragEvent.java": """
package org.bukkit.event.inventory;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.Inventory;
public class InventoryDragEvent extends InventoryEvent implements Cancellable {
    private boolean cancelled;
    public InventoryDragEvent(Inventory inventory) { this.inventory = inventory; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
""",
"org/bukkit/event/inventory/InventoryCloseEvent.java": """
package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
public class InventoryCloseEvent extends InventoryEvent {
    private HumanEntity who;
    public InventoryCloseEvent(Inventory inventory, HumanEntity who) { this.inventory = inventory; this.who = who; }
    public HumanEntity getPlayer() { return who; }
}
""",
"org/bukkit/event/inventory/InventoryMoveItemEvent.java": """
package org.bukkit.event.inventory;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
public class InventoryMoveItemEvent extends Event implements Cancellable {
    private final Inventory source;
    private final Inventory destination;
    private ItemStack item;
    private boolean cancelled;
    public InventoryMoveItemEvent(Inventory source, ItemStack item, Inventory destination) {
        this.source = source; this.item = item; this.destination = destination;
    }
    public Inventory getSource() { return source; }
    public Inventory getDestination() { return destination; }
    public ItemStack getItem() { return item; }
    public void setItem(ItemStack item) { this.item = item; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
""",
"org/bukkit/permissions/Permissible.java": """
package org.bukkit.permissions;
public interface Permissible {
    boolean hasPermission(String name);
}
""",
"org/bukkit/command/Command.java": """
package org.bukkit.command;
public class Command {
    private final String name;
    public Command() { this("rapidhoppers"); }
    public Command(String name) { this.name = name; }
    public String getName() { return name; }
}
""",
"org/bukkit/command/CommandSender.java": """
package org.bukkit.command;
import net.kyori.adventure.text.Component;
import org.bukkit.permissions.Permissible;
public interface CommandSender extends Permissible {
    void sendMessage(String message);
    void sendMessage(Component message);
}
""",
"org/bukkit/command/CommandExecutor.java": """
package org.bukkit.command;
public interface CommandExecutor {
    boolean onCommand(CommandSender sender, Command command, String label, String[] args);
}
""",
"org/bukkit/command/TabCompleter.java": """
package org.bukkit.command;
import java.util.List;
public interface TabCompleter {
    List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args);
}
""",
"org/bukkit/command/PluginCommand.java": """
package org.bukkit.command;
public class PluginCommand extends Command {
    public PluginCommand(String name) { super(name); }
    public void setExecutor(CommandExecutor executor) {}
    public void setTabCompleter(TabCompleter completer) {}
}
""",
"org/bukkit/configuration/ConfigurationSection.java": """
package org.bukkit.configuration;
import java.util.List;
public interface ConfigurationSection {
    boolean contains(String path);
    Object get(String path);
    void set(String path, Object value);
    String getString(String path, String def);
    int getInt(String path, int def);
    double getDouble(String path, double def);
    boolean getBoolean(String path, boolean def);
    List<String> getStringList(String path);
}
""",
"org/bukkit/configuration/file/FileConfiguration.java": """
package org.bukkit.configuration.file;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
public class FileConfiguration implements ConfigurationSection {
    protected final Map<String, Object> values = new HashMap<>();
    public boolean contains(String path) { return values.containsKey(path); }
    public Object get(String path) { return values.get(path); }
    public void set(String path, Object value) { values.put(path, value); }
    public String getString(String path, String def) {
        Object v = values.get(path); return v == null ? def : String.valueOf(v);
    }
    public int getInt(String path, int def) {
        Object v = values.get(path); return v instanceof Number n ? n.intValue() : def;
    }
    public double getDouble(String path, double def) {
        Object v = values.get(path); return v instanceof Number n ? n.doubleValue() : def;
    }
    public boolean getBoolean(String path, boolean def) {
        Object v = values.get(path); return v instanceof Boolean b ? b : def;
    }
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object v = values.get(path);
        return v instanceof List<?> list ? (List<String>) list : new ArrayList<String>();
    }
}
""",
"org/bukkit/configuration/file/YamlConfiguration.java": """
package org.bukkit.configuration.file;
import java.io.File;
public class YamlConfiguration extends FileConfiguration {
    public static YamlConfiguration loadConfiguration(File file) { return new YamlConfiguration(); }
}
""",
"org/bukkit/plugin/Plugin.java": """
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
"org/bukkit/plugin/PluginManager.java": """
package org.bukkit.plugin;
import org.bukkit.event.Listener;
public interface PluginManager {
    void registerEvents(Listener listener, Plugin plugin);
    Plugin getPlugin(String name);
}
""",
"org/bukkit/plugin/java/JavaPlugin.java": """
package org.bukkit.plugin.java;
import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
public abstract class JavaPlugin implements Plugin {
    private final FileConfiguration config = new YamlConfiguration();
    public abstract void onEnable();
    public abstract void onDisable();
    public File getDataFolder() { return new File("target/test-data/RapidHoppers"); }
    public InputStream getResource(String filename) { return null; }
    public void saveResource(String path, boolean replace) {}
    public void saveDefaultConfig() {}
    public void saveConfig() {}
    public void reloadConfig() {}
    public FileConfiguration getConfig() { return config; }
    public Server getServer() { return Bukkit.getServer(); }
    public Logger getLogger() { return Logger.getLogger("RapidHoppers"); }
    public String getName() { return "RapidHoppers"; }
    public PluginCommand getCommand(String name) { return new PluginCommand(name); }
}
""",
"org/bukkit/scheduler/BukkitTask.java": """
package org.bukkit.scheduler;
public interface BukkitTask {
    int getTaskId();
    void cancel();
}
""",
"org/bukkit/scheduler/BukkitScheduler.java": """
package org.bukkit.scheduler;
import org.bukkit.plugin.Plugin;
public interface BukkitScheduler {
    BukkitTask runTask(Plugin plugin, Runnable task);
    int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period);
    void cancelTask(int taskId);
    void cancelTasks(Plugin plugin);
}
""",
}


def main() -> None:
    for rel, content in FILES.items():
        path = ROOT / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content.strip() + "\n", encoding="utf-8")
    print(f"wrote {len(FILES)} stub files to {ROOT}")


if __name__ == "__main__":
    main()
