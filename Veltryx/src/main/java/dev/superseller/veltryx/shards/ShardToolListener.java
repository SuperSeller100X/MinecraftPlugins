package dev.superseller.veltryx.shards;

import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import dev.superseller.veltryx.config.VeltryxConfig;

/**
 * Core shard tool behaviour: 3x3 pickaxe/shovel mining, whole-tree axe.
 * The centre block is handled by vanilla (correct fortune/silk drops and
 * durability); the extra blocks are broken with the same tool so
 * enchantments apply to them as well.
 */
public final class ShardToolListener implements Listener {

    private final ShardItems items;
    private final ShardCatalog catalog;
    private final VeltryxConfig config;

    public ShardToolListener(ShardItems items, ShardCatalog catalog, VeltryxConfig config) {
        this.items = items;
        this.catalog = catalog;
        this.config = config;
    }

    // HIGHEST + ignoreCancelled: run after protection plugins have made their
    // cancel decision, so a cancelled break never triggers the area break.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        String id = items.itemId(tool);
        if (id == null) {
            return;
        }
        ShardCatalog.Entry entry = catalog.byId(id);
        if (entry == null || entry.behavior() == Behavior.NONE
                || entry.behavior() == Behavior.HASTE_POTION
                || entry.behavior() == Behavior.VOID_TOTEM) {
            return;
        }
        if (!items.abilityEnabled(tool)) {
            return;   // ability switched off with /shard toggle
        }
        if (!player.hasPermission("veltryx.shards")) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
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
        World world = origin.getWorld();
        // Vanilla rule: silk touch drops the ore block but NO experience -
        // without this check a silk shard pickaxe would farm infinite XP.
        boolean silkTouch = tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        var direction = player.getLocation().getDirection();
        List<int[]> offsets = AreaPlane.offsets(direction.getX(), direction.getY(), direction.getZ(),
                config.shardAreaRadius());
        for (int[] offset : offsets) {
            int x = origin.getX() + offset[0];
            int y = origin.getY() + offset[1];
            int z = origin.getZ() + offset[2];
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type == Material.AIR || isProtected(type)) {
                continue;
            }
            // breakNaturally(tool, false): the tool's fortune/silk applies to the
            // extra blocks too, and no vanilla break particles fire on them.
            block.breakNaturally(tool, false);
            if (!silkTouch) {
                dropOreXp(block, type);
            }
            amethystBurst(block);
        }
    }

    /** Amethyst-like purple glints on every block the shard tool mines. */
    private void amethystBurst(Block block) {
        block.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT,
                block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5, 6);
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
                true, config.shardTreeMaxBlocks());
        for (int[] log : logs) {
            Block block = world.getBlockAt(log[0], log[1], log[2]);
            block.breakNaturally(tool, false);
            amethystBurst(block);
        }
        if (config.shardTreeBreakLeaves()) {
            List<int[]> leaves = TreeFeller.collectLeaves(logs, grid,
                    name -> isLeaf(name), config.shardTreeMaxBlocks());
            for (int[] leaf : leaves) {
                Block leafBlock = world.getBlockAt(leaf[0], leaf[1], leaf[2]);
                // Empty-hand break: a silk touch axe must not drop leaf blocks.
                leafBlock.breakNaturally(null, false);
            }
        }
        if (config.shardTreeReplant() && !logs.isEmpty()) {
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
        String below = belowType.name();
        if (below.equals("DIRT") || below.equals("GRASS_BLOCK")
                || below.endsWith("_DIRT") || below.equals("PODZOL")) {
            origin.setType(sapling);
        }
    }

    /** Never hit containers or bedrock-like blocks. */
    private boolean isProtected(Material type) {
        String name = type.name();
        return type == Material.BARRIER || name.equals("BEDROCK")
                || name.equals("CHEST") || name.equals("TRAPPED_CHEST") || name.equals("BARREL")
                || name.equals("HOPPER") || name.equals("DISPENSER") || name.equals("DROPPER")
                || name.equals("FURNACE") || name.equals("BLAST_FURNACE") || name.equals("SMOKER")
                || name.equals("BREWING_STAND") || name.endsWith("SHULKER_BOX");
    }

    /** Vanilla-style ore XP for the extra broken blocks (worst case, like multi-drops). */
    private void dropOreXp(Block block, Material type) {
        String name = type.name();
        int xpValue = 0;
        if (name.contains("COAL_ORE")) {
            xpValue = 1;
        } else if (name.contains("DIAMOND_ORE") || name.contains("EMERALD_ORE")) {
            xpValue = 7;
        } else if (name.contains("LAPIS_ORE") || name.contains("REDSTONE_ORE")) {
            xpValue = 5;
        } else if (name.contains("NETHER_QUARTZ_ORE")) {
            xpValue = 3;
        } else if (name.contains("COPPER_ORE")) {
            xpValue = 2;
        } else if (name.equals("SCULK")) {
            xpValue = 1;
        }
        final int xp = xpValue;
        if (xp > 0) {
            block.getWorld().spawn(block.getLocation(), org.bukkit.entity.ExperienceOrb.class,
                    orb -> orb.setExperience(xp));
        }
    }
}
