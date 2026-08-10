package com.plugins.giftsystem.commands;

import com.plugins.giftsystem.GiftSystem;
import com.plugins.giftsystem.models.Gift;
import com.plugins.giftsystem.utils.DataManager;
import com.plugins.giftsystem.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class GiftCommand implements CommandExecutor, TabCompleter {
    private final GiftSystem plugin;

    public GiftCommand(GiftSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.player-only", "&cThis command can only be used by players!")));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("giftsystem.use")) {
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to do that!")));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(MessageUtils.colorize("&8[&aGiftSystem&8] &eUsage: /gift <player> [message]"));
            player.sendMessage(MessageUtils.colorize("&7Hold items in your hand to send as a gift!"));
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null) {
            // Check if offline player exists (could add offline support here)
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found!")));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.self-gift", "&cYou cannot send a gift to yourself!")));
            return true;
        }

        // Get items from player's hand
        List<ItemStack> items = new ArrayList<>();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        
        if (handItem.getType().isAir()) {
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.no-items", "&cYou must hold items to send a gift!")));
            return true;
        }

        int maxItems = plugin.getConfig().getInt("max-items-per-gift", 9);
        if (handItem.getAmount() > maxItems) {
            // Split into multiple gifts or limit
            ItemStack cloned = handItem.clone();
            cloned.setAmount(maxItems);
            items.add(cloned);
            handItem.setAmount(handItem.getAmount() - maxItems);
        } else {
            items.add(handItem.clone());
            handItem.setAmount(0);
        }

        // Build message from remaining args
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) messageBuilder.append(" ");
            messageBuilder.append(args[i]);
        }
        String message = messageBuilder.toString();

        // Check cooldown
        DataManager.PlayerGiftData data = plugin.getDataManager().getPlayerData(player.getName());
        
        if (!player.hasPermission("giftsystem.bypass")) {
            long cooldown = plugin.getConfig().getLong("cooldown", 60) * 1000;
            long timeSinceLastSend = System.currentTimeMillis() - data.getLastSendTime();
            
            if (timeSinceLastSend < cooldown) {
                long remaining = (cooldown - timeSinceLastSend) / 1000;
                String msg = plugin.getConfig().getString("messages.cooldown-active", "&ePlease wait %seconds% seconds before sending another gift!");
                msg = msg.replace("%seconds%", String.valueOf(remaining));
                player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.prefix", "&8[&aGiftSystem&8] ") + msg));
                return true;
            }

            // Check daily limit
            int dailyLimit = plugin.getConfig().getInt("daily-send-limit", 10);
            if (data.getDailySentCount() >= dailyLimit) {
                player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.daily-limit-reached", "&cYou have reached your daily gift sending limit!")));
                return true;
            }
        }

        // Create and send gift
        long expirationHours = plugin.getConfig().getLong("gift-expiration-hours", 168);
        Gift gift = new Gift(
            player.getName(),
            player.getUniqueId(),
            target.getName(),
            target.getUniqueId(),
            items,
            message.isEmpty() ? null : message,
            expirationHours
        );

        plugin.getGiftManager().addGift(gift);
        
        // Update sender data
        data.setLastSendTime(System.currentTimeMillis());
        data.incrementDailySentCount();
        plugin.getDataManager().savePlayerData(data);

        // Send messages
        String sentMsg = plugin.getConfig().getString("messages.gift-sent", "&aYou sent a gift to &e%player%&a!");
        sentMsg = sentMsg.replace("%player%", target.getName());
        player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.prefix", "&8[&aGiftSystem&8] ") + sentMsg));

        String receivedMsg = plugin.getConfig().getString("messages.gift-received", "&aYou received a gift from &e%player%&a!");
        receivedMsg = receivedMsg.replace("%player%", player.getName());
        target.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.prefix", "&8[&aGiftSystem&8] ") + receivedMsg));

        // Play sounds
        playSound(player, "gift-sent");
        playSound(target, "gift-received");

        return true;
    }

    private void playSound(Player player, String type) {
        String soundName = plugin.getConfig().getString("sounds." + type, "");
        if (!soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                // Sound not found, skip
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
            
            return completions;
        }
        return Collections.emptyList();
    }
}
