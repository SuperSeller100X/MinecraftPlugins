package com.giftsystem;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import java.util.UUID;
import java.util.List;

public class GiftListener implements Listener {
    
    private GiftSystemPlugin plugin;
    
    public GiftListener(GiftSystemPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        
        List<Gift> gifts = plugin.getPendingGifts().get(playerUuid);
        
        if (gifts != null && !gifts.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "=== Welcome Back! ===");
            player.sendMessage(ChatColor.YELLOW + "You have " + gifts.size() + " pending gift(s)!");
            player.sendMessage(ChatColor.WHITE + "Use /gifts to view them!");
        }
    }
}
