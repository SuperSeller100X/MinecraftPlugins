package dev.superseller.connectedtools.listener;

import dev.superseller.connectedtools.ConnectedToolsPlugin;
import dev.superseller.connectedtools.service.ConnectionService;
import dev.superseller.connectedtools.util.Colors;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerInteractListener implements Listener {

    private final ConnectionService service;

    public PlayerInteractListener(ConnectionService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Binding mode: left-click block
        if (service != null && ConnectedToolsPlugin.getInstance().getStore().isBinding(player.getUniqueId()) && action == Action.LEFT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked == null) return;
            double distance = player.getLocation().distance(clicked.getLocation());
            if (distance > ConnectedToolsPlugin.getInstance().settings().maxBindDistance()) {
                player.sendMessage(Colors.parse(ConnectedToolsPlugin.getInstance().settings().msg("binding.too-far").replace("{distance}", String.valueOf(ConnectedToolsPlugin.getInstance().settings().maxBindDistance()))));
                return;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                player.sendMessage(Colors.parse(ConnectedToolsPlugin.getInstance().settings().msg("binding.not-holding")));
                return;
            }
            event.setCancelled(true);
            service.bind(player, item, clicked);
            return;
        }

        // Activation: right-click with connected item -> emit pulse
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) return;
            if (service != null) {
                event.setCancelled(true);
                service.emit(player, item);
            }
        }
    }
}
