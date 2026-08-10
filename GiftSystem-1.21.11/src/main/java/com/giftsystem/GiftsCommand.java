package com.giftsystem;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import java.util.UUID;
import java.util.List;

public class GiftsCommand implements CommandExecutor {
    
    private GiftSystemPlugin plugin;
    
    public GiftsCommand(GiftSystemPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("giftsystem.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }
        
        UUID playerUuid = player.getUniqueId();
        List<Gift> gifts = plugin.getPendingGifts().get(playerUuid);
        
        if (gifts == null || gifts.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no pending gifts!");
            return true;
        }
        
        player.sendMessage(ChatColor.GOLD + "=== Your Pending Gifts ===");
        for (int i = 0; i < gifts.size(); i++) {
            Gift gift = gifts.get(i);
            player.sendMessage(ChatColor.GREEN + "Gift #" + (i + 1) + ":");
            player.sendMessage(ChatColor.YELLOW + "From: " + gift.getSenderName());
            player.sendMessage(ChatColor.WHITE + "Message: " + gift.getMessage());
            player.sendMessage(ChatColor.WHITE + "Items: " + gift.getItems().size());
        }
        
        return true;
    }
}
