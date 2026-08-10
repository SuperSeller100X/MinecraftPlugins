package com.example.coderewards.listeners;

import com.example.coderewards.CodeRewardsPlugin;
import com.example.coderewards.data.RewardCode;
import com.example.coderewards.gui.CodeGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {
    private final CodeRewardsPlugin plugin;

    public GUIListener(CodeRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        if (event.getInventory().getHolder() instanceof CodeGUI) {
            event.setCancelled(true);
            
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;
            
            String displayName = clickedItem.getItemMeta().getDisplayName();
            if (displayName == null) return;
            
            // Remove color codes for comparison
            String cleanName = displayName.replaceAll("§[0-9a-fk-or]", "");
            
            // Find matching code
            for (RewardCode code : plugin.getCodeManager().getAllCodes()) {
                String codeName = code.canRedeem() ? code.getCode() : code.getCode() + " (Expired)";
                if (cleanName.equals(codeName)) {
                    redeemCode(player, code);
                    return;
                }
            }
        }
    }

    private void redeemCode(Player player, RewardCode rewardCode) {
        if (!rewardCode.canRedeem()) {
            player.sendMessage("§cThis code has been fully redeemed!");
            return;
        }

        if (rewardCode.isOneTimePerPlayer() && rewardCode.hasRedeemed(player.getUniqueId().toString())) {
            player.sendMessage("§cYou have already redeemed this code!");
            return;
        }

        // Redeem the code
        rewardCode.redeem(player.getUniqueId().toString());

        // Execute commands
        for (String cmd : rewardCode.getCommands()) {
            String formattedCmd = cmd.replace("{player}", player.getName());
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), formattedCmd);
        }

        // Give items
        for (org.bukkit.inventory.ItemStack item : rewardCode.getItems()) {
            if (item != null) {
                player.getInventory().addItem(item.clone());
            }
        }

        player.sendMessage("§aSuccessfully redeemed code: " + rewardCode.getCode());
        player.sendMessage("§aYou received your rewards!");
        
        // Refresh GUI
        plugin.getCodeManager().saveCodes();
        CodeGUI.openGUI(player);
    }
}
