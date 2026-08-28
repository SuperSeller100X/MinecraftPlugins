package dev.superseller.easymending.listener;

import dev.superseller.easymending.config.Messages;
import dev.superseller.easymending.config.PluginConfig;
import dev.superseller.easymending.gui.EasyMendingGui;
import dev.superseller.easymending.gui.EasyMendingHolder;
import dev.superseller.easymending.model.RepairEstimate;
import dev.superseller.easymending.model.RepairResult;
import dev.superseller.easymending.model.RepairScope;
import dev.superseller.easymending.service.RepairService;
import dev.superseller.easymending.util.ExperienceUtil;
import dev.superseller.easymending.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles inventory interaction events inside the EasyMending GUI.
 */
public final class GuiListener implements Listener {

    private final PluginConfig config;
    private final Messages messages;
    private final RepairService repairService;
    private final EasyMendingGui gui;

    public GuiListener(PluginConfig config, Messages messages, RepairService repairService, EasyMendingGui gui) {
        this.config = config;
        this.messages = messages;
        this.repairService = repairService;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EasyMendingHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        switch (slot) {
            case EasyMendingGui.SLOT_CLOSE -> {
                SoundUtil.playGuiClick(player, config);
                player.closeInventory();
            }
            case EasyMendingGui.SLOT_INFO, EasyMendingGui.SLOT_PROFILE -> {
                SoundUtil.playGuiClick(player, config);
            }
            case EasyMendingGui.SLOT_HAND -> handleRepairClick(player, RepairScope.HAND, "easymending.hand", event);
            case EasyMendingGui.SLOT_OFFHAND -> handleRepairClick(player, RepairScope.OFFHAND, "easymending.offhand", event);
            case EasyMendingGui.SLOT_ARMOR -> handleRepairClick(player, RepairScope.ARMOR, "easymending.armor", event);
            case EasyMendingGui.SLOT_ALL -> handleRepairClick(player, RepairScope.ALL, "easymending.all", event);
            default -> SoundUtil.playGuiClick(player, config);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof EasyMendingHolder) {
            event.setCancelled(true);
        }
    }

    private void handleRepairClick(Player player, RepairScope scope, String permission, InventoryClickEvent event) {
        if (!player.hasPermission(permission) && !player.hasPermission("easymending.use")) {
            messages.send(player, "no-permission");
            SoundUtil.playNoDamage(player, config);
            return;
        }

        RepairResult result = repairService.repair(player, scope, false, false);
        if (result.success()) {
            if (result.bypassCost()) {
                if (result.itemsRepaired() > 1) {
                    messages.send(player, "repair-free-multiple", "count", String.valueOf(result.itemsRepaired()));
                } else {
                    messages.send(player, "repair-free-single",
                            "item", result.primaryItemName(),
                            "repaired", String.valueOf(result.totalDurabilityRestored()));
                }
            } else {
                if (result.itemsRepaired() > 1) {
                    messages.send(player, "repair-success-multiple",
                            "count", String.valueOf(result.itemsRepaired()),
                            "repaired", String.valueOf(result.totalDurabilityRestored()),
                            "cost", String.valueOf(result.xpSpent()));
                } else {
                    messages.send(player, "repair-success-single",
                            "item", result.primaryItemName(),
                            "repaired", String.valueOf(result.totalDurabilityRestored()),
                            "cost", String.valueOf(result.xpSpent()));
                }
            }

            if (result.partial()) {
                messages.send(player, "repair-partial-notice", "repaired", String.valueOf(result.totalDurabilityRestored()));
            }

            if (result.itemsRepaired() > 1) {
                SoundUtil.playRepairAll(player, config);
            } else {
                SoundUtil.playRepairSuccess(player, config);
            }

            gui.refresh(player, event.getInventory());
        } else {
            handleFailure(player, scope, result.failureReasonKey());
        }
    }

    private void handleFailure(Player player, RepairScope scope, String reasonKey) {
        if (reasonKey == null) reasonKey = "no-damage-target";

        switch (reasonKey) {
            case "cooldown-active" -> {
                long rem = repairService.getRemainingCooldownSeconds(player.getUniqueId());
                messages.send(player, "cooldown-active", "seconds", String.valueOf(rem));
                SoundUtil.playNoDamage(player, config);
            }
            case "insufficient-xp" -> {
                RepairEstimate est = repairService.estimate(player, scope);
                int currentXp = ExperienceUtil.getPlayerTotalExperience(player);
                messages.send(player, "insufficient-xp",
                        "cost", String.valueOf(est.totalXpCost()),
                        "current_xp", String.valueOf(currentXp));
                SoundUtil.playInsufficientXp(player, config);
            }
            case "not-repairable" -> {
                messages.send(player, "not-repairable");
                SoundUtil.playNoDamage(player, config);
            }
            case "no-mending" -> {
                messages.send(player, "no-mending");
                SoundUtil.playNoMending(player, config);
            }
            case "no-damage-held" -> {
                messages.send(player, "no-damage-held");
                SoundUtil.playNoDamage(player, config);
            }
            default -> {
                messages.send(player, "no-damage-target");
                SoundUtil.playNoDamage(player, config);
            }
        }
    }
}
