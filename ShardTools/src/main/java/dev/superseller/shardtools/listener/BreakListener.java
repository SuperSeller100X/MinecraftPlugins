package dev.superseller.shardtools.listener;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import dev.superseller.shardtools.ShardToolsPlugin;
import dev.superseller.shardtools.command.Permissions;
import dev.superseller.shardtools.item.Behavior;
import dev.superseller.shardtools.item.ShardCatalog;
import dev.superseller.shardtools.tools.AreaPlane;
import dev.superseller.shardtools.tools.TreeFeller;

/**
 * Core shard tool behaviour: 3x3 pickaxe/shovel mining, whole-tree axe.
 * The centre block is handled by vanilla (correct fortune/silk drops and
 * durability); the extra blocks are broken with the same tool so
 * enchantments apply to them as well.
 */
public final class BreakListener implements Listener {

    private final ShardToolsPlugin plugin;

    public BreakListener(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        String id = plugin.items().itemId(tool);
        if (id == null) {
            return;
        }
        ShardCatalog.Entry entry = plugin.catalog().byId(id);
        if (entry == null || entry.behavior() == Behavior.NONE || entry.behavior() == Behavior.HASTE_POTION) {
            return;
        }
        if (!player.hasPermission(Permissions.USE)) {
            return;
        }
        if (plugin.settings().isWorldDisabled(player.getWorld().getName())) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE && !plugin.settings().allowCreative()) {
            return;
        }
        if (plugin.settings().requireSneak() && !player.isSneaking()) {
            return;
        }
        Block origin = event.getBlock();
        if (entry.behavior() == Behavior.TREE_AXE) {
            fellTree(player, tool, origin);
        } else {
            breakArea(player, tool, origin, entry.behavior());
        }
    }

    private void breakArea(Player player, ItemStack tool, Block origin, Behavior behavior) {
        World world = origin.getWorld();
        var direction = player.getLocation().getDirection();
        List<int[]> offsets = AreaPlane.offsets(direction.getX(), direction.getY(), direction.getZ(),
                plugin.settings().areaRadius());
        for (int[] offset : offsets) {
            int x = origin.getX() + offset[0];
            int y = origin.getY() + offset[1];
            int z = origin.getZ() + offset[2];
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type == Material.AIR || plugin.settings().isProtected(type)) {
                continue;
            }
            if (plugin.settings().protectContainers() && plugin.settings().isContainer(type)) {
                continue;
            }
            boolean allowed = behavior == Behavior.AREA_PICKAXE
                    ? plugin.settings().isPickaxeAllowed(type)
                    : plugin.settings().isShovelAllowed(type);
            if (!allowed) {
                continue;
            }
            block.breakNaturally(tool, true);
            plugin.sweep().dropOreXp(block, type);
        }
    }

    private void fellTree(Player player, ItemStack tool, Block origin) {
        Material originType = origin.getType();
        if (!Tag.LOGS.isTagged(originType)) {
            return;
        }
        World world = origin.getWorld();
        TreeFeller.Grid grid = (x, y, z) -> {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return null;
            }
            Material material = world.getBlockAt(x, y, z).getType();
            return material == Material.AIR ? null : material.name();
        };
        List<int[]> logs = TreeFeller.collect(origin.getX(), origin.getY(), origin.getZ(),
                originType.name(), grid, name -> isLog(name),
                plugin.settings().treeSameMaterialOnly(), plugin.settings().treeMaxBlocks());
        for (int[] log : logs) {
            Block block = world.getBlockAt(log[0], log[1], log[2]);
            block.breakNaturally(tool, true);
        }
        if (plugin.settings().treeReplant() && !logs.isEmpty()) {
            replant(world, origin, originType);
        }
    }

    private boolean isLog(String materialName) {
        Material material = Material.matchMaterial(materialName);
        return material != null && Tag.LOGS.isTagged(material);
    }

    private void replant(World world, Block origin, Material logType) {
        String name = logType.name();
        String base = name.endsWith("_LOG") ? name.substring(0, name.length() - 4) : null;
        if (base == null) {
            return;
        }
        Material sapling = Material.matchMaterial(base + "_SAPLING");
        if (sapling == null) {
            return;
        }
        Block ground = world.getBlockAt(origin.getX(), origin.getY() - 1, origin.getZ());
        Material belowType = ground.getType();
        if (belowType == Material.DIRT || belowType == Material.GRASS_BLOCK
                || belowType.name().endsWith("_DIRT") || belowType.name().equals("PODZOL")) {
            origin.setType(sapling);
        }
    }
}
