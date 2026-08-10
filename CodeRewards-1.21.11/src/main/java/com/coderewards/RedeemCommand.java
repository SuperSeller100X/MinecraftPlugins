package com.coderewards;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class RedeemCommand implements CommandExecutor {
    
    private CodeRewardsPlugin plugin;
    
    public RedeemCommand(CodeRewardsPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("coderewards.redeem")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }
        
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /redeem <code>");
            return true;
        }
        
        String codeStr = args[0].toUpperCase();
        Code code = plugin.getActiveCodes().get(codeStr);
        
        if (code == null) {
            player.sendMessage(ChatColor.RED + "Invalid code!");
            return true;
        }
        
        if (!code.isActive()) {
            player.sendMessage(ChatColor.RED + "This code has already been used!");
            return true;
        }
        
        if (plugin.getRedeemedCodes().contains(player.getUniqueId() + "_" + codeStr)) {
            player.sendMessage(ChatColor.RED + "You have already redeemed this code!");
            return true;
        }
        
        RewardManager.giveReward(player, code);
        code.setActive(false);
        plugin.getRedeemedCodes().add(player.getUniqueId() + "_" + codeStr);
        
        player.sendMessage(ChatColor.GREEN + "Successfully redeemed code: " + codeStr);
        player.sendMessage(ChatColor.YELLOW + "Reward: " + code.getRewardType() + " x" + (int)code.getAmount());
        
        return true;
    }
}
