package dev.superseller.teleportsigns.listener;

import dev.superseller.teleportsigns.TeleportSignsPlugin;
import dev.superseller.teleportsigns.model.TeleportSign;
import dev.superseller.teleportsigns.util.LookTarget;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-click a bound sign to teleport. */
public final class InteractListener implements Listener {

    private final TeleportSignsPlugin plugin;

    public InteractListener(TeleportSignsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!LookTarget.isSign(block)) {
            return;
        }
        Sign state = (Sign) block.getState();
        TeleportSign bound = plugin.store().read(state);
        if (bound == null) {
            return;
        }
        if (plugin.settings().sneakToEdit() && event.getPlayer().isSneaking()) {
            return;
        }
        event.setCancelled(true);
        plugin.teleports().request(event.getPlayer(), bound);
    }
}
