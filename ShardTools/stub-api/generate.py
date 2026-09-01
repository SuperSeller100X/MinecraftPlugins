#!/usr/bin/env python3
"""Emit compile-only Paper/Bukkit API stubs used by ShardTools offline builds.

The stubs mirror the exact signatures ShardTools uses from Paper 26.2
(paper-api 26.2.build.115-stable) so the full plugin source can be
type-checked with only a JRE + ECJ, without network access to Maven
repositories. They contain no logic - the real classes are provided by the
server at runtime. Verified API surface: every class referenced by
src/main/java (see grep in README "Offline build" section).
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "src"


def write(rel: str, content: str) -> None:
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.strip() + "\n", encoding="utf-8")


# ---------------------------------------------------------------- adventure

write("net/kyori/adventure/text/Component.java", """
package net.kyori.adventure.text;
public interface Component {
    static Component text(String content) { return new Component() {}; }
    static Component empty() { return new Component() {}; }
}
""")

write("net/kyori/adventure/text/minimessage/MiniMessage.java", """
package net.kyori.adventure.text.minimessage;
import net.kyori.adventure.text.Component;
public interface MiniMessage {
    static MiniMessage miniMessage() { return new MiniMessage() {
        public Component deserialize(String input) { return Component.empty(); }
    }; }
    Component deserialize(String input);
}
""")

# ---------------------------------------------------------------- registry

write("io/papermc/paper/registry/RegistryAccess.java", """
package io.papermc.paper.registry;
public interface RegistryAccess {
    static RegistryAccess registryAccess() { throw new UnsupportedOperationException(); }
    <T extends org.bukkit.Keyed> org.bukkit.Registry<T> getRegistry(RegistryKey<T> key);
}
""")

write("io/papermc/paper/registry/RegistryKey.java", """
package io.papermc.paper.registry;
public final class RegistryKey<T> {
    public static final RegistryKey<org.bukkit.enchantments.Enchantment> ENCHANTMENT = new RegistryKey<>();
    public static final RegistryKey<org.bukkit.Sound> SOUND_EVENT = new RegistryKey<>();
    private RegistryKey() {}
}
""")

write("org/bukkit/Keyed.java", """
package org.bukkit;
public interface Keyed {
    NamespacedKey getKey();
}
""")

write("org/bukkit/Registry.java", """
package org.bukkit;
public interface Registry<T extends Keyed> {
    T get(NamespacedKey key);
}
""")

# ---------------------------------------------------------------- core

write("org/bukkit/NamespacedKey.java", """
package org.bukkit;
import org.bukkit.plugin.Plugin;
public class NamespacedKey {
    public NamespacedKey(Plugin plugin, String key) {}
    public static NamespacedKey minecraft(String key) { return new NamespacedKey(null, key); }
}
""")

write("org/bukkit/Particle.java", """
package org.bukkit;
public enum Particle { PORTAL, END_ROD, WITCH, DUST, TOTEM }
""")

write("org/bukkit/Color.java", """
package org.bukkit;
public class Color {
    public static Color fromRGB(int rgb) { return new Color(); }
}
""")

write("org/bukkit/GameMode.java", """
package org.bukkit;
public enum GameMode { SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR }
""")

write("org/bukkit/Vector.java", """
package org.bukkit;
public class Vector {
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }
}
""")

write("org/bukkit/Location.java", """
package org.bukkit;
public class Location {
    public Location(World world, double x, double y, double z) {}
    public World getWorld() { return null; }
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }
    public Vector getDirection() { return new Vector(); }
    public Location add(double x, double y, double z) { return this; }
}
""")

write("org/bukkit/Tag.java", """
package org.bukkit;
public interface Tag<T> {
    boolean isTagged(T item);
    Tag<Material> LOGS = null;
    Tag<Material> LEAVES = null;
}
""")

write("org/bukkit/Material.java", """
package org.bukkit;
public enum Material {
    AIR, DIRT, GRASS_BLOCK,
    CHEST, TRAPPED_CHEST, BARREL, HOPPER, DISPENSER, DROPPER,
    FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND, SHULKER_BOX;

    public static Material matchMaterial(String name) { return AIR; }
    public int getMaxStackSize() { return 64; }
}
""")

write("org/bukkit/Sound.java", """
package org.bukkit;
public interface Sound extends Keyed {}
""")

write("org/bukkit/OfflinePlayer.java", """
package org.bukkit;
import java.util.UUID;
public interface OfflinePlayer {
    UUID getUniqueId();
    String getName();
    boolean isOnline();
}
""")

write("org/bukkit/World.java", """
package org.bukkit;
import java.util.function.Consumer;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
public interface World {
    String getName();
    Block getBlockAt(int x, int y, int z);
    boolean isChunkLoaded(int chunkX, int chunkZ);
    <T extends Entity> T spawn(Location location, Class<T> type, Consumer<T> consumer);
    Item dropItem(Location location, ItemStack stack);
    void spawnParticle(Particle particle, Location location, int count,
                       double offsetX, double offsetY, double offsetZ, double extra);
}
""")

write("org/bukkit/block/Block.java", """
package org.bukkit.block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
public interface Block {
    Material getType();
    void setType(Material type);
    int getX();
    int getY();
    int getZ();
    World getWorld();
    Location getLocation();
    boolean breakNaturally(ItemStack tool, boolean triggerEffect);
}
""")

write("org/bukkit/Server.java", """
package org.bukkit;
import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
public interface Server {
    PluginManager getPluginManager();
    ServicesManager getServicesManager();
    BukkitScheduler getScheduler();
    Collection<? extends Player> getOnlinePlayers();
    Player getPlayerExact(String name);
    OfflinePlayer getOfflinePlayerIfCached(String name);
}
""")

write("org/bukkit/Bukkit.java", """
package org.bukkit;
import java.util.Collection;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
public final class Bukkit {
    public static Server getServer() { return null; }
    public static BukkitScheduler getScheduler() { return null; }
    public static Collection<? extends Player> getOnlinePlayers() { return java.util.List.of(); }
    public static Player getPlayerExact(String name) { return null; }
    public static OfflinePlayer getOfflinePlayerIfCached(String name) { return null; }
    public static PluginManager getPluginManager() { return null; }
    public static Inventory createInventory(InventoryHolder owner, int size, Component title) { return null; }
    public static org.bukkit.command.CommandSender getConsoleSender() { return null; }
    public static boolean dispatchCommand(org.bukkit.command.CommandSender sender, String command) { return true; }
}
""")

# ---------------------------------------------------------------- persistence

write("org/bukkit/persistence/PersistentDataType.java", """
package org.bukkit.persistence;
public interface PersistentDataType<T, Z> {
    PersistentDataType<Byte, Byte> BYTE = null;
    PersistentDataType<Integer, Integer> INTEGER = null;
    PersistentDataType<Long, Long> LONG = null;
    PersistentDataType<String, String> STRING = null;
}
""")

write("org/bukkit/persistence/PersistentDataContainer.java", """
package org.bukkit.persistence;
import org.bukkit.NamespacedKey;
public interface PersistentDataContainer {
    <T, Z> void set(NamespacedKey key, PersistentDataType<T, Z> type, Z value);
    <T, Z> Z get(NamespacedKey key, PersistentDataType<T, Z> type);
    <T, Z> T getOrDefault(NamespacedKey key, PersistentDataType<T, Z> type, T def);
    <T, Z> boolean has(NamespacedKey key, PersistentDataType<T, Z> type);
}
""")

# ---------------------------------------------------------------- inventory

write("org/bukkit/inventory/ItemStack.java", """
package org.bukkit.inventory;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack {
    public ItemStack(Material type, int amount) {}
    public Material getType() { return null; }
    public int getAmount() { return 1; }
    public void setAmount(int amount) {}
    public ItemMeta getItemMeta() { return null; }
    public boolean setItemMeta(ItemMeta meta) { return true; }
    public int getEnchantmentLevel(org.bukkit.enchantments.Enchantment enchantment) { return 0; }
    public <M extends ItemMeta> boolean editMeta(Consumer<M> consumer) { return true; }
}
""")

write("org/bukkit/inventory/meta/ItemMeta.java", """
package org.bukkit.inventory.meta;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.persistence.PersistentDataContainer;
public interface ItemMeta {
    void displayName(Component name);
    void lore(List<Component> lore);
    List<Component> lore();
    boolean addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestrictions);
    PersistentDataContainer getPersistentDataContainer();
}
""")

write("org/bukkit/inventory/meta/PotionMeta.java", """
package org.bukkit.inventory.meta;
import org.bukkit.Color;
public interface PotionMeta extends ItemMeta {
    void setColor(Color color);
}
""")

write("org/bukkit/inventory/Inventory.java", """
package org.bukkit.inventory;
public interface Inventory {
    int getSize();
    ItemStack getItem(int index);
    void setItem(int index, ItemStack stack);
    ItemStack[] getContents();
    InventoryHolder getHolder();
    default java.util.HashMap<Integer, ItemStack> addItem(ItemStack... stacks) { return null; }
}
""")

write("org/bukkit/inventory/PlayerInventory.java", """
package org.bukkit.inventory;
public interface PlayerInventory extends Inventory {
    ItemStack getItemInMainHand();
    void setItemInMainHand(ItemStack stack);
}
""")

write("org/bukkit/inventory/InventoryHolder.java", """
package org.bukkit.inventory;
public interface InventoryHolder {
    Inventory getInventory();
}
""")

write("org/bukkit/inventory/InventoryView.java", """
package org.bukkit.inventory;
public interface InventoryView {
    Inventory getTopInventory();
    Inventory getBottomInventory();
    ItemStack getCursor();
    void setCursor(ItemStack stack);
}
""")

write("org/bukkit/inventory/AnvilInventory.java", """
package org.bukkit.inventory;
public interface AnvilInventory extends Inventory {}
""")

# ---------------------------------------------------------------- entities

write("org/bukkit/entity/Entity.java", """
package org.bukkit.entity;
public interface Entity {
    void remove();
}
""")

write("org/bukkit/entity/HumanEntity.java", """
package org.bukkit.entity;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
public interface HumanEntity extends Entity, CommandSender {
    PlayerInventory getInventory();
    GameMode getGameMode();
    void closeInventory();
    InventoryView openInventory(Inventory inventory);
    Location getLocation();
    World getWorld();
}
""")

write("org/bukkit/entity/Player.java", """
package org.bukkit.entity;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.inventory.InventoryView;
public interface Player extends HumanEntity, org.bukkit.OfflinePlayer {
    UUID getUniqueId();
    void sendMessage(Component message);
    void sendActionBar(Component message);
    void playSound(Location location, Sound sound, float volume, float pitch);
    void updateInventory();
    InventoryView getOpenInventory();
    boolean isOnline();
    boolean isSneaking();
}
""")

write("org/bukkit/entity/Item.java", """
package org.bukkit.entity;
import org.bukkit.inventory.ItemStack;
public interface Item extends Entity {
    ItemStack getItemStack();
}
""")

write("org/bukkit/entity/ExperienceOrb.java", """
package org.bukkit.entity;
public interface ExperienceOrb extends Entity {
    void setExperience(int amount);
}
""")

write("org/bukkit/enchantments/Enchantment.java", """
package org.bukkit.enchantments;
import org.bukkit.Keyed;
public abstract class Enchantment implements Keyed {
    public static final Enchantment SILK_TOUCH = new Enchantment() {
        @Override
        public org.bukkit.NamespacedKey getKey() {
            throw new UnsupportedOperationException();
        }
    };
}
""")

write("org/bukkit/potion/PotionEffectType.java", """
package org.bukkit.potion;
public class PotionEffectType {
    public static final PotionEffectType HASTE = new PotionEffectType();
}
""")

write("org/bukkit/potion/PotionEffect.java", """
package org.bukkit.potion;
public class PotionEffect {
    public PotionEffect(PotionEffectType type, int duration, int amplifier) {}
}
""")

# Player.addPotionEffect lives on Player at runtime; add it there.
write("org/bukkit/entity/Player.java", """
package org.bukkit.entity;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.inventory.InventoryView;
import org.bukkit.potion.PotionEffect;
public interface Player extends HumanEntity, org.bukkit.OfflinePlayer {
    UUID getUniqueId();
    void sendMessage(Component message);
    void sendActionBar(Component message);
    void playSound(Location location, Sound sound, float volume, float pitch);
    void updateInventory();
    InventoryView getOpenInventory();
    boolean isOnline();
    boolean isSneaking();
    boolean addPotionEffect(PotionEffect effect);
}
""")

# ---------------------------------------------------------------- events

write("org/bukkit/event/Event.java", """
package org.bukkit.event;
public abstract class Event {}
""")

write("org/bukkit/event/Listener.java", """
package org.bukkit.event;
public interface Listener {}
""")

write("org/bukkit/event/EventHandler.java", """
package org.bukkit.event;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {
    EventPriority priority() default EventPriority.NORMAL;
    boolean ignoreCancelled() default false;
}
""")

write("org/bukkit/event/EventPriority.java", """
package org.bukkit.event;
public enum EventPriority { LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR }
""")

write("com/destroystokyo/paper/event/player/PlayerArmorChangeEvent.java", """
package com.destroystokyo.paper.event.player;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
public class PlayerArmorChangeEvent extends Event {
    public Player getPlayer() { return null; }
    public ItemStack getNewItem() { return null; }
    public ItemStack getPreviousItem() { return null; }
}
""")

write("org/bukkit/event/block/BlockBreakEvent.java", """
package org.bukkit.event.block;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
public class BlockBreakEvent extends Event {
    public Player getPlayer() { return null; }
    public Block getBlock() { return null; }
}
""")

write("org/bukkit/event/player/PlayerItemHeldEvent.java", """
package org.bukkit.event.player;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
public class PlayerItemHeldEvent extends Event {
    public Player getPlayer() { return null; }
    public int getPreviousSlot() { return -1; }
    public int getNewSlot() { return -1; }
}
""")

write("org/bukkit/event/player/PlayerJoinEvent.java", """
package org.bukkit.event.player;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
public class PlayerJoinEvent extends Event {
    public Player getPlayer() { return null; }
}
""")

write("org/bukkit/event/player/PlayerItemConsumeEvent.java", """
package org.bukkit.event.player;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
public class PlayerItemConsumeEvent extends Event {
    public Player getPlayer() { return null; }
    public ItemStack getItem() { return null; }
}
""")

write("org/bukkit/event/entity/EntityPickupItemEvent.java", """
package org.bukkit.event.entity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
public class EntityPickupItemEvent extends Event {
    public Entity getEntity() { return null; }
    public Item getItem() { return null; }
    public void setCancelled(boolean cancelled) {}
}
""")

write("org/bukkit/event/inventory/InventoryClickEvent.java", """
package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
public class InventoryClickEvent extends Event {
    public HumanEntity getWhoClicked() { return null; }
    public ItemStack getCurrentItem() { return null; }
    public Inventory getClickedInventory() { return null; }
    public InventoryView getView() { return null; }
    public void setCancelled(boolean cancelled) {}
    public int getRawSlot() { return -1; }
}
""")

write("org/bukkit/event/inventory/InventoryDragEvent.java", """
package org.bukkit.event.inventory;
import java.util.Set;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.inventory.InventoryView;
public class InventoryDragEvent extends Event {
    public HumanEntity getWhoClicked() { return null; }
    public InventoryView getView() { return null; }
    public Set<Integer> getRawSlots() { return Set.of(); }
    public void setCancelled(boolean cancelled) {}
}
""")

write("org/bukkit/event/inventory/InventoryOpenEvent.java", """
package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
public class InventoryOpenEvent extends Event {
    public HumanEntity getPlayer() { return null; }
    public Inventory getInventory() { return null; }
    public InventoryView getView() { return null; }
}
""")

write("org/bukkit/event/inventory/PrepareAnvilEvent.java", """
package org.bukkit.event.inventory;
import org.bukkit.event.Event;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
public class PrepareAnvilEvent extends Event {
    public AnvilInventory getInventory() { return null; }
    public ItemStack getResult() { return null; }
    public void setResult(ItemStack result) {}
}
""")

write("org/bukkit/event/inventory/PrepareGrindstoneEvent.java", """
package org.bukkit.event.inventory;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
public class PrepareGrindstoneEvent extends Event {
    public Inventory getInventory() { return null; }
    public ItemStack getResult() { return null; }
    public void setResult(ItemStack result) {}
}
""")

# ---------------------------------------------------------------- command

write("org/bukkit/command/CommandSender.java", """
package org.bukkit.command;
import net.kyori.adventure.text.Component;
public interface CommandSender {
    void sendMessage(Component message);
    boolean hasPermission(String permission);
    String getName();
}
""")

write("org/bukkit/command/Command.java", """
package org.bukkit.command;
public abstract class Command {
    public String getName() { return ""; }
}
""")

write("org/bukkit/command/CommandExecutor.java", """
package org.bukkit.command;
public interface CommandExecutor {
    boolean onCommand(CommandSender sender, Command command, String label, String[] args);
}
""")

write("org/bukkit/command/TabCompleter.java", """
package org.bukkit.command;
import java.util.List;
public interface TabCompleter {
    List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args);
}
""")

write("org/bukkit/command/PluginCommand.java", """
package org.bukkit.command;
public final class PluginCommand extends Command {
    public void setExecutor(CommandExecutor executor) {}
    public void setTabCompleter(TabCompleter completer) {}
}
""")

# ---------------------------------------------------------------- configuration

write("org/bukkit/configuration/ConfigurationSection.java", """
package org.bukkit.configuration;
import java.util.List;
import java.util.Set;
public interface ConfigurationSection {
    default String getString(String path) { return null; }
    default int getInt(String path) { return 0; }
    default long getLong(String path) { return 0L; }
    default boolean getBoolean(String path) { return false; }
    default double getDouble(String path) { return 0D; }
    default double getDouble(String path, double def) { return def; }
    default String getString(String path, String def) { return def; }
    default List<String> getStringList(String path) { return List.of(); }
    default List<Long> getLongList(String path) { return List.of(); }
    default int getInt(String path, int def) { return def; }
    default long getLong(String path, long def) { return def; }
    default boolean getBoolean(String path, boolean def) { return def; }
    default boolean contains(String path) { return false; }
    default ConfigurationSection getConfigurationSection(String path) { return null; }
    default Set<String> getKeys(boolean deep) { return Set.of(); }
    default void set(String path, Object value) {}
    default void setDefaults(ConfigurationSection defaults) {}
}
""")

write("org/bukkit/configuration/file/FileConfiguration.java", """
package org.bukkit.configuration.file;
import org.bukkit.configuration.ConfigurationSection;
public interface FileConfiguration extends ConfigurationSection {}
""")

write("org/bukkit/configuration/file/YamlConfiguration.java", """
package org.bukkit.configuration.file;
import java.io.File;
import java.io.IOException;
public class YamlConfiguration implements FileConfiguration {
    public static YamlConfiguration loadConfiguration(File file) { return new YamlConfiguration(); }
    public static YamlConfiguration loadConfiguration(java.io.Reader reader) { return new YamlConfiguration(); }
    public void load(File file) throws IOException, org.bukkit.configuration.InvalidConfigurationException {}
    public void save(File file) throws IOException {}
}
""")

write("org/bukkit/configuration/InvalidConfigurationException.java", """
package org.bukkit.configuration;
public class InvalidConfigurationException extends Exception {}
""")

# ---------------------------------------------------------------- plugin

write("org/bukkit/plugin/Plugin.java", """
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
""")

write("org/bukkit/plugin/ServicesManager.java", """
package org.bukkit.plugin;
public interface ServicesManager {
    Object getRegistration(Class<?> service);
}
""")

write("org/bukkit/plugin/PluginManager.java", """
package org.bukkit.plugin;
import org.bukkit.event.Listener;
public interface PluginManager {
    void registerEvents(Listener listener, Plugin plugin);
    Plugin getPlugin(String name);
}
""")

write("org/bukkit/plugin/java/JavaPlugin.java", """
package org.bukkit.plugin.java;
import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
public class JavaPlugin implements Plugin {
    public void onEnable() {}
    public void onDisable() {}
    public FileConfiguration getConfig() { return null; }
    public void reloadConfig() {}
    public void saveDefaultConfig() {}
    public void saveResource(String resourcePath, boolean replace) {}
    public java.io.InputStream getResource(String resourcePath) { return null; }
    public File getDataFolder() { return null; }
    public Logger getLogger() { return Logger.getLogger("ShardTools"); }
    public Server getServer() { return null; }
    public PluginCommand getCommand(String name) { return null; }
    public String getName() { return "ShardTools"; }
}
""")

# ---------------------------------------------------------------- scheduler

write("org/bukkit/scheduler/BukkitTask.java", """
package org.bukkit.scheduler;
public interface BukkitTask {}
""")

write("org/bukkit/scheduler/BukkitScheduler.java", """
package org.bukkit.scheduler;
import org.bukkit.plugin.Plugin;
public interface BukkitScheduler {
    BukkitTask runTask(Plugin plugin, Runnable task);
    BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay);
    BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period);
    BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task);
    void cancelTasks(Plugin plugin);
}
""")

print("Wrote Paper API stubs to", ROOT)
