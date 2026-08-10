package com.giftsystem.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class GiftAdminCommand implements CommandExecutor {

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

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "send":
                return handleSend(sender, args);
            case "clear":
                return handleClear(sender, args);
            case "reload":
                return handleReload(sender);
            case "list":
                return handleList(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleSend(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /giftadmin send <player> [message]");
            sender.sendMessage("§7You must be holding an item to send.");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or offline!");
            return true;
        }

        // This would need implementation similar to GiftCommand but without cooldown
        sender.sendMessage("§aUse the regular /gift command for sending items. This command is for admin-specific operations.");
        return true;
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /giftadmin clear <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        UUID targetUuid;
        
        if (target == null) {
            // Try to find player by name (offline)
            try {
                targetUuid = UUID.fromString(args[1]);
            } catch (Exception e) {
                sender.sendMessage("§cPlayer not found! Use exact username or UUID.");
                return true;
            }
        } else {
            targetUuid = target.getUniqueId();
        }

        List<Gift> gifts = plugin.getPlayerGifts(targetUuid);
        int count = gifts.size();
        
        plugin.clearAllGifts(targetUuid);
        
        sender.sendMessage("§aCleared §e" + count + " §agift(s) from " + args[1]);
        
        if (target != null && target.isOnline()) {
            target.sendMessage("§cAn admin has cleared your pending gifts.");
        }
        
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.loadGifts();
        sender.sendMessage("§aGiftSystem configuration reloaded!");
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /giftadmin list <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        UUID targetUuid;
        
        if (target == null) {
            try {
                targetUuid = UUID.fromString(args[1]);
            } catch (Exception e) {
                sender.sendMessage("§cPlayer not found! Use exact username or UUID.");
                return true;
            }
        } else {
            targetUuid = target.getUniqueId();
        }

        List<Gift> gifts = plugin.getPlayerGifts(targetUuid);
        
        if (gifts.isEmpty()) {
            sender.sendMessage("§e" + args[1] + " §ahas no pending gifts.");
            return true;
        }

        sender.sendMessage("§6=== Gifts for " + args[1] + " ===");
        for (int i = 0; i < gifts.size(); i++) {
            Gift gift = gifts.get(i);
            sender.sendMessage("§e#" + (i + 1) + ": From §f" + gift.getFromPlayer() + 
                             " §e- Item: §f" + gift.getItem().getType().name() +
                             " §e(x" + gift.getItem().getAmount() + ")");
        }
        sender.sendMessage("§6========================");
        
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== GiftSystem Admin Commands ===");
        sender.sendMessage("§e/giftadmin clear <player> §7- Clear all gifts for a player");
        sender.sendMessage("§e/giftadmin list <player> §7- List all pending gifts for a player");
        sender.sendMessage("§e/giftadmin reload §7- Reload configuration");
        sender.sendMessage("§6================================");
    }
}
