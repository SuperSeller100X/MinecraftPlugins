package org.bukkit.block;
import org.bukkit.persistence.PersistentDataContainer;
public interface BlockState {
    Block getBlock();
    PersistentDataContainer getPersistentDataContainer();
    boolean update();
    boolean update(boolean force, boolean applyPhysics);
}
