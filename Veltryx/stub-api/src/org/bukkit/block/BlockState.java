package org.bukkit.block;
public interface BlockState {
    Block getBlock();
    boolean update(boolean force, boolean applyPhysics);
}
