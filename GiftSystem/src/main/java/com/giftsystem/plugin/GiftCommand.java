package com.giftsystem.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class GiftCommand implements CommandExecutor {

    private final GiftSystemPlugin plugin;

    public GiftCommand(GiftSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("giftsystem.use")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (!plugin.isGiftingEnabled()) {
            player.sendMessage("§cGifting is currently disabled!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§6Usage: §e/gift <player> [message]");
            player.sendMessage("§7Open your inventory and select items to gift, then use this command.");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("§cPlayer not found or offline!");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§cYou cannot send a gift to yourself!");
            return true;
        }

        if (plugin.isInCooldown(player.getName()) && !player.hasPermission("giftsystem.bypass")) {
            player.sendMessage("§cYou are on cooldown! Please wait before sending another gift.");
            return true;
        }

        // Get items from player's cursor or selected slot
        List<ItemStack> itemsToGift = new ArrayList<>();
        ItemStack cursor = player.getItemOnCursor();
        
        if (cursor != null && cursor.getType() != Material.AIR) {
            itemsToGift.add(cursor.clone());
            player.setItemOnCursor(new ItemStack(Material.AIR));
        } else {
            // Get item from main hand
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem != null && handItem.getType() != Material.AIR) {
                itemsToGift.add(handItem.clone());
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        }

        if (itemsToGift.isEmpty()) {
            player.sendMessage("§cHold an item in your hand or cursor to gift!");
            player.sendMessage("§7For multiple items, open the gifting GUI with /giftgui");
            return true;
        }

        // Build message
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            messageBuilder.append(args[i]).append(" ");
        }
        String message = messageBuilder.toString().trim();

        // Send gifts
        int maxGifts = plugin.getMaxGiftsPerSend();
        int sentCount = 0;

        for (ItemStack item : itemsToGift) {
            if (sentCount >= maxGifts && !player.hasPermission("giftsystem.bypass")) {
                player.sendMessage("§cMaximum gift limit reached (" + maxGifts + " items)!");
                break;
            }

            Gift gift = new Gift(player.getName(), item, System.currentTimeMillis(), message);
            plugin.addGift(target.getUniqueId(), gift);
            sentCount++;
        }

        plugin.setCooldown(player.getName());

        player.sendMessage("§aYou sent §e" + sentCount + " §agift(s) to §e" + target.getName() + "!");
        
        if (target.isOnline()) {
            target.sendMessage("§e" + player.getName() + " §asent you a gift! Use §e/gifts §ato view it!");
        }

        return true;
    }
}
