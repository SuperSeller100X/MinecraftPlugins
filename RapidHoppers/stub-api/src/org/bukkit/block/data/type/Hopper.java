package org.bukkit.block.data.type;
import org.bukkit.block.data.Directional;
public interface Hopper extends Directional {
    boolean isEnabled();
    void setEnabled(boolean enabled);
}
