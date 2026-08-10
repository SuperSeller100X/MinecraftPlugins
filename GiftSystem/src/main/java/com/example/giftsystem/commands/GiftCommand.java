package com.example.giftsystem.commands;

import com.example.giftsystem.GiftSystemPlugin;
import com.example.giftsystem.data.Gift;
import com.example.giftsystem.gui.GiftGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GiftCommand implements CommandExecutor {
    private final GiftSystemPlugin plugin;

    public GiftCommand(GiftSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("giftgui") || cmdName.equals("gifts")) {
            GiftGUI.openGUI(player);
            return true;
        }

        if (cmdName.equals("gift")) {
            if (args.length == 0) {
                player.sendMessage("§cUsage: /gift <player> [message]");
                player.sendMessage("§7Hold items in your hand to gift them!");
                return true;
            }

            String recipientName = args[0];
            Player recipient = Bukkit.getPlayer(recipientName);
            
            if (recipient == null) {
                // Check if player exists (offline gifting allowed)
                if (Bukkit.getOfflinePlayer(recipientName).hasPlayedBefore() || 
                    Bukkit.getOfflinePlayer(recipientName).isOnline()) {
                    // Allow offline gifting
                } else {
                    player.sendMessage("§cPlayer not found!");
                    return true;
                }
            }

            StringBuilder message = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                message.append(args[i]).append(" ");
            }

            // Get items from player's hand
            List<ItemStack> items = new ArrayList<>();
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (!handItem.isEmpty()) {
                items.add(handItem.clone());
                handItem.setAmount(0);
            }

            if (items.isEmpty()) {
                player.sendMessage("§cHold an item in your main hand to gift!");
                return true;
            }

            String recipientUUID = recipient != null ? recipient.getUniqueId().toString() : 
                                   Bukkit.getOfflinePlayer(recipientName).getUniqueId().toString();
            String recipientDisplayName = recipient != null ? recipient.getName() : recipientName;

            Gift gift = new Gift(
                player.getUniqueId().toString(),
                player.getName(),
                recipientUUID,
                recipientDisplayName,
                items,
                message.toString().trim()
            );

            plugin.getGiftManager().addGift(gift);

            player.sendMessage("§aGift sent to " + recipientDisplayName + "!");
            if (recipient != null && recipient.isOnline()) {
                recipient.sendMessage("§eYou received a gift from " + player.getName() + "!");
                recipient.sendMessage("§7Use /gifts to claim it!");
            }

            return true;
        }

        return true;
    }
}
