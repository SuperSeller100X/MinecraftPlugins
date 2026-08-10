package com.giftsystem;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import java.util.UUID;
import java.util.List;

public class GiftAdminCommand implements CommandExecutor {
    
    private GiftSystemPlugin plugin;
    
    public GiftAdminCommand(GiftSystemPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("giftsystem.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }
        
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /giftadmin <clear|list|reload> [player]");
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "clear":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /giftadmin clear <player>");
                    return true;
                }
                
                Player target = Bukkit.getPlayer(args[1]);
                UUID targetUuid;
                
                if (target != null) {
                    targetUuid = target.getUniqueId();
                } else {
                    sender.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
                
                List<Gift> gifts = plugin.getPendingGifts().remove(targetUuid);
                if (gifts != null) {
                    sender.sendMessage(ChatColor.GREEN + "Cleared " + gifts.size() + " gifts for " + args[1]);
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "No gifts found for " + args[1]);
                }
                break;
                
            case "list":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /giftadmin list <player>");
                    return true;
                }
                
                Player listTarget = Bukkit.getPlayer(args[1]);
                UUID listUuid;
                
                if (listTarget != null) {
                    listUuid = listTarget.getUniqueId();
                } else {
                    sender.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
                
                List<Gift> listGifts = plugin.getPendingGifts().get(listUuid);
                if (listGifts == null || listGifts.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No pending gifts for " + args[1]);
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== Pending Gifts for " + args[1] + " ===");
                    for (int i = 0; i < listGifts.size(); i++) {
                        Gift gift = listGifts.get(i);
                        sender.sendMessage(ChatColor.GREEN + "Gift #" + (i + 1) + ": From " + gift.getSenderName());
                    }
                }
                break;
                
            case "reload":
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
                break;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand! Use: clear, list, reload");
                break;
        }
        
        return true;
    }
}
