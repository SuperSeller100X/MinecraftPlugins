package com.coderewards;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class RewardManager {
    
    public static void giveReward(Player player, Code code) {
        String rewardType = code.getRewardType().toLowerCase();
        double amount = code.getAmount();
        
        switch (rewardType) {
            case "money":
                // Economy integration would go here
                player.sendMessage(ChatColor.GREEN + "Received $" + (int)amount + " virtual currency!");
                break;
                
            case "exp":
                player.giveExp((int)amount);
                player.sendMessage(ChatColor.GREEN + "Received " + (int)amount + " XP!");
                break;
                
            case "health":
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount));
                player.sendMessage(ChatColor.GREEN + "Health restored!");
                break;
                
            case "hunger":
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + (int)amount));
                player.sendMessage(ChatColor.GREEN + "Hunger restored!");
                break;
                
            case "diamond":
                ItemStack diamonds = new ItemStack(Material.DIAMOND, (int)amount);
                player.getInventory().addItem(diamonds);
                player.sendMessage(ChatColor.GREEN + "Received " + (int)amount + " Diamonds!");
                break;
                
            case "emerald":
                ItemStack emeralds = new ItemStack(Material.EMERALD, (int)amount);
                player.getInventory().addItem(emeralds);
                player.sendMessage(ChatColor.GREEN + "Received " + (int)amount + " Emeralds!");
                break;
                
            case "gold":
                ItemStack gold = new ItemStack(Material.GOLD_INGOT, (int)amount);
                player.getInventory().addItem(gold);
                player.sendMessage(ChatColor.GREEN + "Received " + (int)amount + " Gold Ingots!");
                break;
                
            case "iron":
                ItemStack iron = new ItemStack(Material.IRON_INGOT, (int)amount);
                player.getInventory().addItem(iron);
                player.sendMessage(ChatColor.GREEN + "Received " + (int)amount + " Iron Ingots!");
                break;
                
            case "speed":
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int)(amount * 60 * 20), 1));
                player.sendMessage(ChatColor.GREEN + "Received Speed boost for " + (int)amount + " minutes!");
                break;
                
            case "strength":
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, (int)(amount * 60 * 20), 1));
                player.sendMessage(ChatColor.GREEN + "Received Strength boost for " + (int)amount + " minutes!");
                break;
                
            case "enchanted_book":
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, (int)amount);
                book.addEnchantment(Enchantment.SHARPNESS, 5);
                player.getInventory().addItem(book);
                player.sendMessage(ChatColor.GREEN + "Received " + (int)amount + " Enchanted Books!");
                break;
                
            default:
                player.sendMessage(ChatColor.RED + "Unknown reward type: " + rewardType);
                break;
        }
        
        // Play level up sound
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }
}
