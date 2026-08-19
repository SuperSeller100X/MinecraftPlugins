package org.bukkit.inventory.meta;

import org.bukkit.OfflinePlayer;

public interface SkullMeta extends ItemMeta {
    void setOwningPlayer(OfflinePlayer owner);

    OfflinePlayer getOwningPlayer();
}
