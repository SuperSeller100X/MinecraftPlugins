package dev.superseller.connectedtools.util;

import dev.superseller.connectedtools.ConnectedToolsPlugin;
import org.bukkit.Block;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.Switch;
import org.bukkit.block.data.type.Lever;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.block.data.type.FenceGate;

public final class BlockUtil {

    private BlockUtil() {}

    public static boolean canToggle(Material type) {
        return type == Material.LEVER
                || type == Material.STONE_BUTTON || type == Material.OAK_BUTTON
                || type == Material.SPRUCE_BUTTON || type == Material.BIRCH_BUTTON
                || type == Material.JUNGLE_BUTTON || type == Material.ACACIA_BUTTON
                || type == Material.DARK_OAK_BUTTON || type == Material.MANGROVE_BUTTON
                || type == Material.BAMBOO_BUTTON || type == Material.CHERRY_BUTTON
                || type == Material.COMPARATOR || type == Material.REPEATER
                || type == Material.REDSTONE_TORCH || type == Material.REDSTONE_WALL_TORCH
                || type == Material.OBSERVER
                || type == Material.REDSTONE_WIRE
                || type == Material.OAK_DOOR || type == Material.IRON_DOOR
                || type == Material.SPRUCE_DOOR || type == Material.BIRCH_DOOR
                || type == Material.JUNGLE_DOOR || type == Material.ACACIA_DOOR
                || type == Material.DARK_OAK_DOOR || type == Material.MANGROVE_DOOR
                || type == Material.BAMBOO_DOOR || type == Material.CHERRY_DOOR
                || type == Material.COPPER_DOOR || type == Material.WAXED_COPPER_DOOR
                || type == Material.OAK_TRAPDOOR || type == Material.IRON_TRAPDOOR
                || type == Material.SPRUCE_TRAPDOOR || type == Material.BIRCH_TRAPDOOR
                || type == Material.JUNGLE_TRAPDOOR || type == Material.ACACIA_TRAPDOOR
                || type == Material.DARK_OAK_TRAPDOOR || type == Material.MANGROVE_TRAPDOOR
                || type == Material.BAMBOO_TRAPDOOR || type == Material.CHERRY_TRAPDOOR
                || type == Material.COPPER_TRAPDOOR || type == Material.WAXED_COPPER_TRAPDOOR
                || type == Material.OAK_FENCE_GATE || type == Material.SPRUCE_FENCE_GATE
                || type == Material.BIRCH_FENCE_GATE || type == Material.JUNGLE_FENCE_GATE
                || type == Material.ACACIA_FENCE_GATE || type == Material.DARK_OAK_FENCE_GATE
                || type == Material.MANGROVE_FENCE_GATE || type == Material.BAMBOO_FENCE_GATE
                || type == Material.CHERRY_FENCE_GATE || type == Material.COPPER_FENCE_GATE
                || type == Material.WAXED_COPPER_FENCE_GATE;
    }

    public static boolean toggleBlock(Block block, int ticks) {
        BlockData data = block.getBlockData();
        Material type = block.getType();

        if (type == Material.LEVER) {
            Lever lever = (Lever) data;
            lever.setPowered(!lever.isPowered());
            block.setBlockData(lever);
            return true;
        }

        if (type.name().endsWith("_BUTTON")) {
            Powerable btn = (Powerable) data;
            btn.setPowered(true);
            block.setBlockData(btn);
            // Revert after ticks
            block.getWorld().getScheduler().runTaskLater(
                    dev.superseller.connectedtools.ConnectedToolsPlugin.getInstance(),
                    () -> {
                        if (block.getType() == type) {
                            Powerable powered = (Powerable) block.getBlockData();
                            powered.setPowered(false);
                            block.setBlockData(powered);
                        }
                    }, ticks);
            return true;
        }

        if (type == Material.COMPARATOR || type == Material.REPEATER) {
            Powerable p = (Powerable) data;
            p.setPowered(!p.isPowered());
            block.setBlockData(p);
            return true;
        }

        if (type == Material.REDSTONE_TORCH || type == Material.REDSTONE_WALL_TORCH) {
            org.bukkit.block.data.type.RedstoneTorch torch = (org.bukkit.block.data.type.RedstoneTorch) data;
            torch.setLit(!torch.isLit());
            block.setBlockData(torch);
            return true;
        }

        if (type == Material.OBSERVER) {
            Directional dir = (Directional) data;
            // Observers emit a pulse when triggered by block updates; we'll simulate by temporarily powering
            // Since observers don't have a powered state, we'll temporarily replace with redstone block
            // and restore after ticks
            Material original = block.getType();
            block.setType(Material.REDSTONE_BLOCK);
            block.getWorld().getScheduler().runTaskLater(
                    dev.superseller.connectedtools.ConnectedToolsPlugin.getInstance(),
                    () -> {
                        if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                            block.setType(original);
                        }
                    }, ticks);
            return true;
        }

        if (type == Material.REDSTONE_WIRE) {
            org.bukkit.block.data.type.RedstoneWire wire = (org.bukkit.block.data.type.RedstoneWire) data;
            wire.setPower(15);
            block.setBlockData(wire);
            block.getWorld().getScheduler().runTaskLater(
                    dev.superseller.connectedtools.ConnectedToolsPlugin.getInstance(),
                    () -> {
                        if (block.getType() == Material.REDSTONE_WIRE) {
                            wire.setPower(0);
                            block.setBlockData(wire);
                        }
                    }, ticks);
            return true;
        }

        // Doors: toggle open/close
        if (type.name().endsWith("_DOOR")) {
            Door door = (Door) data;
            door.setOpen(!door.isOpen());
            block.setBlockData(door);
            return true;
        }

        // Trapdoors: toggle open/close
        if (type.name().endsWith("_TRAPDOOR")) {
            TrapDoor trap = (TrapDoor) data;
            trap.setOpen(!trap.isOpen());
            block.setBlockData(trap);
            return true;
        }

        // Fence gates: toggle open/close
        if (type.name().endsWith("_FENCE_GATE")) {
            FenceGate gate = (FenceGate) data;
            gate.setOpen(!gate.isOpen());
            block.setBlockData(gate);
            return true;
        }

        return false;
    }
}
