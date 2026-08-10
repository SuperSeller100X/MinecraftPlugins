package com.giftsystem;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GiftCommand implements CommandExecutor {
    
    private GiftSystemPlugin plugin;
    
    public GiftCommand(GiftSystemPlugin plugin) {
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
        
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /gift <player> [message]");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found or offline!");
            return true;
        }
        
        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "You can't send a gift to yourself!");
            return true;
        }
        
        List<ItemStack> items = new ArrayList<>(player.getInventory().getContents());
        items.removeIf(item -> item == null);
        
        if (items.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You need items in your inventory to send a gift!");
            return true;
        }
        
        String message = args.length > 1 ? String.join(" ", args).substring(args[0].length() + 1) : "No message";
        
        Gift gift = new Gift(player.getUniqueId(), player.getName(), items, message);
        
        UUID targetUuid = target.getUniqueId();
        plugin.getPendingGifts().computeIfAbsent(targetUuid, k -> new ArrayList<>()).add(gift);
        
        player.getInventory().clear();
        
        player.sendMessage(ChatColor.GREEN + "Gift sent to " + target.getName() + "!");
        target.sendMessage(ChatColor.GOLD + "You received a gift from " + player.getName() + "!");
        target.sendMessage(ChatColor.YELLOW + "Use /gifts to view your gifts!");
        
        return true;
    }
}
