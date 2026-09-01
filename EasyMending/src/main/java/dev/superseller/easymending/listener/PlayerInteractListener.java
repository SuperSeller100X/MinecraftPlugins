package dev.superseller.easymending.listener;

import dev.superseller.easymending.config.Messages;
import dev.superseller.easymending.config.PluginConfig;
import dev.superseller.easymending.model.RepairResult;
import dev.superseller.easymending.model.RepairScope;
import dev.superseller.easymending.service.RepairService;
import dev.superseller.easymending.util.ItemUtil;
import dev.superseller.easymending.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Optional convenience listener allowing sneak + right-click quick repairs.
 */
public final class PlayerInteractListener implements Listener {

    private final PluginConfig config;
    private final Messages messages;
    private final RepairService repairService;

    public PlayerInteractListener(PluginConfig config, Messages messages, RepairService repairService) {
        this.config = config;
        this.messages = messages;
        this.repairService = repairService;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!config.isSneakClickToRepair()) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!ItemUtil.isRepairable(item) || ItemUtil.getDamage(item) <= 0) {
            return;
        }

        if (!player.hasPermission("easymending.hand") || !player.hasPermission("easymending.use")) {
            return;
        }

        // Execute quick repair
        RepairResult result = repairService.repair(player, RepairScope.HAND, false, false);
        if (result.success()) {
            event.setCancelled(true);
            if (result.bypassCost()) {
                messages.send(player, "repair-free-single",
                        "item", result.primaryItemName(),
                        "repaired", String.valueOf(result.totalDurabilityRestored()));
            } else {
                messages.send(player, "repair-success-single",
                        "item", result.primaryItemName(),
                        "repaired", String.valueOf(result.totalDurabilityRestored()),
                        "cost", String.valueOf(result.xpSpent()));
            }
            if (result.partial()) {
                messages.send(player, "repair-partial-notice", "repaired", String.valueOf(result.totalDurabilityRestored()));
            }
            SoundUtil.playRepairSuccess(player, config);
        }
    }
}
