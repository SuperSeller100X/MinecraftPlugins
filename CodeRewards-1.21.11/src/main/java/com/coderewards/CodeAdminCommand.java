package com.coderewards;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import java.util.Random;

public class CodeAdminCommand implements CommandExecutor {
    
    private CodeRewardsPlugin plugin;
    
    public CodeAdminCommand(CodeRewardsPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("coderewards.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }
        
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /codeadmin <create|delete|list|reload> [args]");
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "create":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /codeadmin create <rewardType> <amount> [code]");
                    return true;
                }
                
                String rewardType = args[1];
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid amount!");
                    return true;
                }
                
                String codeStr;
                if (args.length >= 4) {
                    codeStr = args[3].toUpperCase();
                } else {
                    codeStr = generateRandomCode();
                }
                
                Code newCode = new Code(codeStr, rewardType, amount, true);
                plugin.getActiveCodes().put(codeStr, newCode);
                
                sender.sendMessage(ChatColor.GREEN + "Created code: " + codeStr);
                sender.sendMessage(ChatColor.YELLOW + "Reward: " + rewardType + " x" + (int)amount);
                break;
                
            case "delete":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /codeadmin delete <code>");
                    return true;
                }
                
                String deleteCode = args[1].toUpperCase();
                if (plugin.getActiveCodes().remove(deleteCode) != null) {
                    sender.sendMessage(ChatColor.GREEN + "Deleted code: " + deleteCode);
                } else {
                    sender.sendMessage(ChatColor.RED + "Code not found!");
                }
                break;
                
            case "list":
                sender.sendMessage(ChatColor.GOLD + "=== Active Codes ===");
                for (String code : plugin.getActiveCodes().keySet()) {
                    Code c = plugin.getActiveCodes().get(code);
                    String status = c.isActive() ? ChatColor.GREEN + "Active" : ChatColor.RED + "Used";
                    sender.sendMessage(ChatColor.WHITE + code + " - " + c.getRewardType() + " x" + (int)c.getAmount() + " (" + status + ")");
                }
                break;
                
            case "reload":
                plugin.reloadConfig();
                plugin.getActiveCodes().clear();
                plugin.loadCodesFromConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
                break;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand! Use: create, delete, list, reload");
                break;
        }
        
        return true;
    }
    
    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
}
