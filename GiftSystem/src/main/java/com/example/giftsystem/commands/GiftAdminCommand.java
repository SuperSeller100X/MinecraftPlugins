package com.example.giftsystem.commands;

import com.example.giftsystem.GiftSystemPlugin;
import com.example.giftsystem.data.Gift;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

public class GiftAdminCommand implements CommandExecutor, TabCompleter {
    private final GiftSystemPlugin plugin;

    public GiftAdminCommand(GiftSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("giftsystem.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "clear":
                handleClear(sender, args);
                break;
            case "list":
                handleList(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== GiftSystem Admin Commands ===");
        sender.sendMessage("§e/giftadmin clear <player> - Clear all gifts for a player");
        sender.sendMessage("§e/giftadmin list <player> - List all gifts for a player");
        sender.sendMessage("§e/giftadmin reload - Reload configuration");
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /giftadmin clear <player>");
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);
        String targetUUID = target != null ? target.getUniqueId().toString() : 
                           Bukkit.getOfflinePlayer(playerName).getUniqueId().toString();

        plugin.getGiftManager().clearPlayerGifts(targetUUID);
        sender.sendMessage("§aCleared all gifts for " + playerName);
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /giftadmin list <player>");
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);
        String targetUUID = target != null ? target.getUniqueId().toString() : 
                           Bukkit.getOfflinePlayer(playerName).getUniqueId().toString();

        List<Gift> gifts = plugin.getGiftManager().getGifts(targetUUID);
        if (gifts.isEmpty()) {
            sender.sendMessage("§cNo gifts found for " + playerName);
            return;
        }

        sender.sendMessage("§6=== Gifts for " + playerName + " ===");
        for (Gift gift : gifts) {
            String status = gift.isClaimed() ? "§aClaimed" : "§ePending";
            sender.sendMessage("§7From: " + gift.getSenderName() + 
                             " §7| Items: " + gift.getItems().size() +
                             " §7| Status: " + status);
            if (!gift.getMessage().isEmpty()) {
                sender.sendMessage("§7Message: " + gift.getMessage());
            }
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getGiftManager().loadGifts();
        sender.sendMessage("§aConfiguration reloaded!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("clear", "list", "reload"));
        }
        
        return completions;
    }
}
