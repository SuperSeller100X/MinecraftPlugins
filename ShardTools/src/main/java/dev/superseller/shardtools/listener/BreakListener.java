package dev.superseller.shardtools.listener;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
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

    // HIGHEST + ignoreCancelled: run after protection plugins have made their
    // cancel decision, so a cancelled break never triggers the area break.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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
        if (entry.behavior() == Behavior.TREE_AXE && Tag.LOGS.isTagged(origin.getType())) {
            // Breaking a log with the axe fells the whole tree.
            fellTree(player, tool, origin);
        } else {
            // Pickaxe, shovel AND axe all 3x3 the same shared block list.
            breakArea(player, tool, origin);
        }
    }

    private void breakArea(Player player, ItemStack tool, Block origin) {
        plugin.effects().mineUse(player);   // ONE sound per use, not per block
        World world = origin.getWorld();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        // Vanilla rule: silk touch drops the ore block but NO experience -
        // without this check a silk shard pickaxe would farm infinite XP.
        boolean silkTouch = tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        var direction = player.getLocation().getDirection();
        List<int[]> offsets = AreaPlane.offsets(direction.getX(), direction.getY(), direction.getZ(),
                plugin.settings().areaRadius());
        boolean any = false;
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
            if (!plugin.settings().isAreaAllowed(type)) {
                continue;
            }
            if (creative) {
                // Vanilla creative parity: no drops, no XP for area blocks.
                block.setType(Material.AIR);
            } else {
                // triggerEffect=false: no vanilla break particles/sound on the
                // extra blocks - portal particles + amethyst sound stay clean.
                block.breakNaturally(tool, false);
                if (!silkTouch) {
                    plugin.sweep().dropOreXp(block, type);
                }
            }
            plugin.effects().mineBlockParticles(block.getLocation());
            any = true;
        }
        if (!any) {
            plugin.effects().mineBlockParticles(origin.getLocation());
        }
    }

    private void fellTree(Player player, ItemStack tool, Block origin) {
        Material originType = origin.getType();
        if (!Tag.LOGS.isTagged(originType)) {
            return;
        }
        plugin.effects().mineUse(player);   // ONE sound per use, not per block
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
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        for (int[] log : logs) {
            Block block = world.getBlockAt(log[0], log[1], log[2]);
            if (creative) {
                block.setType(Material.AIR);   // vanilla creative: no drops
            } else {
                block.breakNaturally(tool, false);
            }
            plugin.effects().mineBlockParticles(block.getLocation());
        }
        if (plugin.settings().treeBreakLeaves()) {
            // DonutSMP: the axe mines all logs AND leaves connected to the tree.
            List<int[]> leaves = TreeFeller.collectLeaves(logs, grid,
                    name -> isLeaf(name), plugin.settings().treeMaxBlocks());
            // Empty-hand break: a silk touch axe must not drop leaf blocks.
            for (int[] leaf : leaves) {
                Block leafBlock = world.getBlockAt(leaf[0], leaf[1], leaf[2]);
                if (creative) {
                    leafBlock.setType(Material.AIR);
                } else {
                    leafBlock.breakNaturally(null, false);
                }
            }
        }
        if (plugin.settings().treeReplant() && !logs.isEmpty()) {
            replant(world, origin, originType);
        }
    }

    private boolean isLeaf(String materialName) {
        Material material = Material.matchMaterial(materialName);
        return material != null && Tag.LEAVES.isTagged(material);
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
