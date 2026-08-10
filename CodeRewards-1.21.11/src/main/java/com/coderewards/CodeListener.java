package com.coderewards;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class CodeListener implements Listener {
    
    private CodeRewardsPlugin plugin;
    
    public CodeListener(CodeRewardsPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        if (player.hasPermission("coderewards.use")) {
            player.sendMessage(ChatColor.GOLD + "=== Code Rewards ===");
            player.sendMessage(ChatColor.YELLOW + "Use /redeem <code> to redeem codes!");
            player.sendMessage(ChatColor.WHITE + "Use /codes to view available codes GUI!");
        }
    }
}
