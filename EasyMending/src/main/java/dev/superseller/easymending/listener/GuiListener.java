package dev.superseller.easymending.listener;

import dev.superseller.easymending.config.Messages;
import dev.superseller.easymending.config.PluginConfig;
import dev.superseller.easymending.gui.EasyMendingGui;
import dev.superseller.easymending.gui.EasyMendingHolder;
import dev.superseller.easymending.model.RepairEstimate;
import dev.superseller.easymending.model.RepairResult;
import dev.superseller.easymending.model.RepairScope;
import dev.superseller.easymending.scheduler.PlatformScheduler;
import dev.superseller.easymending.service.RepairService;
import dev.superseller.easymending.util.ExperienceUtil;
import dev.superseller.easymending.util.ItemUtil;
import dev.superseller.easymending.util.SoundUtil;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles inventory interaction events inside the EasyMending repair station GUI.
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

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        Inventory topInv = event.getView().getTopInventory();

        // 1. Handle shift-clicking from player's inventory into the repair slot
        if (rawSlot >= topInv.getSize()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && ItemUtil.isRepairable(clicked)) {
                    ItemStack currentInSlot = topInv.getItem(EasyMendingGui.SLOT_ITEM_INPUT);
                    if (currentInSlot == null || currentInSlot.getType().isAir()) {
                        topInv.setItem(EasyMendingGui.SLOT_ITEM_INPUT, clicked.clone());
                        event.setCurrentItem(null);
                        SoundUtil.playGuiClick(player, config);
                        PlatformScheduler.runEntitySync(player, () -> gui.updateAnvilButton(player, topInv));
                    }
                }
            }
            return;
        }

        // 2. Handle interaction inside top inventory
        if (rawSlot == EasyMendingGui.SLOT_ITEM_INPUT) {
            // Interactive drop-in slot: allow placing/taking items freely!
            PlatformScheduler.runEntitySync(player, () -> gui.updateAnvilButton(player, topInv));
            return;
        }

        // All other top inventory slots are protected buttons
        event.setCancelled(true);

        switch (rawSlot) {
            case EasyMendingGui.SLOT_ANVIL_BUTTON -> handleAnvilSlotRepair(player, topInv);
            case EasyMendingGui.SLOT_HAND -> handleRepairClick(player, RepairScope.HAND, "easymending.hand", topInv);
            case EasyMendingGui.SLOT_OFFHAND -> handleRepairClick(player, RepairScope.OFFHAND, "easymending.offhand", topInv);
            case EasyMendingGui.SLOT_ARMOR -> handleRepairClick(player, RepairScope.ARMOR, "easymending.armor", topInv);
            case EasyMendingGui.SLOT_HOTBAR -> handleRepairClick(player, RepairScope.HOTBAR, "easymending.hotbar", topInv);
            case EasyMendingGui.SLOT_ALL -> handleRepairClick(player, RepairScope.ALL, "easymending.all", topInv);
            case EasyMendingGui.SLOT_CLOSE -> {
                SoundUtil.playGuiClick(player, config);
                PlatformScheduler.runEntitySync(player, player::closeInventory);
            }
            case EasyMendingGui.SLOT_REFRESH -> {
                SoundUtil.playGuiClick(player, config);
                gui.refresh(player, topInv);
                messages.send(player, "gui-refreshed");
            }
            default -> SoundUtil.playGuiClick(player, config);
        }
    }

    private void handleAnvilSlotRepair(Player player, Inventory inv) {
        ItemStack item = inv.getItem(EasyMendingGui.SLOT_ITEM_INPUT);

        if (item == null || item.getType().isAir()) {
            messages.send(player, "gui-no-item-in-slot");
            SoundUtil.playNoDamage(player, config);
            return;
        }

        if (!ItemUtil.isRepairable(item)) {
            messages.send(player, "not-repairable");
            SoundUtil.playNoDamage(player, config);
            return;
        }

        if (ItemUtil.getDamage(item) <= 0) {
            messages.send(player, "no-damage-held");
            SoundUtil.playNoDamage(player, config);
            return;
        }

        boolean mendingBypass = repairService.hasMendingBypass(player);
        if (config.isRequireMending() && !ItemUtil.hasMending(item) && !mendingBypass) {
            messages.send(player, "no-mending");
            SoundUtil.playNoMending(player, config);
            return;
        }

        RepairResult result = repairService.repairSingleItem(player, item, false);
        if (result.success()) {
            inv.setItem(EasyMendingGui.SLOT_ITEM_INPUT, item);

            if (result.bypassCost()) {
                messages.send(player, "repair-free-single",
                        "item", result.primaryItemName(),
                        "repaired", String.valueOf(result.totalDurabilityRestored()));
            } else {
                messages.send(player, "gui-item-repaired",
                        "item", result.primaryItemName(),
                        "repaired", String.valueOf(result.totalDurabilityRestored()),
                        "cost", String.valueOf(result.xpSpent()));
            }

            if (result.partial()) {
                messages.send(player, "repair-partial-notice", "repaired", String.valueOf(result.totalDurabilityRestored()));
            }

            SoundUtil.playRepairSuccess(player, config);
            gui.refresh(player, inv);
        } else {
            handleFailure(player, RepairScope.HAND, result.failureReasonKey());
        }
    }

    private void handleRepairClick(Player player, RepairScope scope, String permission, Inventory inv) {
        if (!player.hasPermission(permission)) {
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

            gui.refresh(player, inv);
        } else {
            handleFailure(player, scope, result.failureReasonKey());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof EasyMendingHolder)) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize && slot != EasyMendingGui.SLOT_ITEM_INPUT) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getRawSlots().contains(EasyMendingGui.SLOT_ITEM_INPUT)) {
            if (event.getWhoClicked() instanceof Player player) {
                PlatformScheduler.runEntitySync(player, () -> gui.updateAnvilButton(player, event.getView().getTopInventory()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof EasyMendingHolder)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory inv = event.getInventory();
        ItemStack leftover = inv.getItem(EasyMendingGui.SLOT_ITEM_INPUT);

        if (leftover != null && !leftover.getType().isAir()) {
            inv.setItem(EasyMendingGui.SLOT_ITEM_INPUT, null);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(leftover);
            for (ItemStack drop : overflow.values()) {
                if (player.getWorld() != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
            if (player.isOnline()) {
                messages.send(player, "gui-item-returned", "item", ItemUtil.getFriendlyName(leftover));
            }
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
