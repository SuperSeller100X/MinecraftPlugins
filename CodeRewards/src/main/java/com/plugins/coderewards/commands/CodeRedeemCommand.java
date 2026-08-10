package com.plugins.coderewards.commands;

import com.plugins.coderewards.CodeRewards;
import com.plugins.coderewards.models.Code;
import com.plugins.coderewards.models.PlayerData;
import com.plugins.coderewards.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CodeRedeemCommand implements CommandExecutor, TabCompleter {
    private final CodeRewards plugin;

    public CodeRedeemCommand(CodeRewards plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.player-only", "&cThis command can only be used by players!")));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("coderewards.use")) {
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to do that!")));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(MessageUtils.colorize("&8[&6CodeRewards&8] &eUsage: /coderedeem <code>"));
            return true;
        }

        String codeInput = args[0];
        Code code = plugin.getCodeManager().getCode(codeInput);

        if (code == null || !code.isActive()) {
            String msg = plugin.getConfig().getString("messages.code-invalid", "&cInvalid or expired code!");
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.prefix", "&8[&6CodeRewards&8] ") + msg));
            playSound(player, "error");
            return true;
        }

        PlayerData data = plugin.getDataManager().getPlayerData(player.getUniqueId(), player.getName());

        // Check if already redeemed
        if (data.hasRedeemedCode(code.getCode())) {
            String msg = plugin.getConfig().getString("messages.code-already-used", "&cYou have already used this code!");
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.prefix", "&8[&6CodeRewards&8] ") + msg));
            playSound(player, "error");
            return true;
        }

        // Check cooldown
        if (!player.hasPermission("coderewards.bypass")) {
            long cooldown = plugin.getConfig().getLong("cooldown", 30) * 1000;
            long timeSinceLastRedemption = System.currentTimeMillis() - data.getLastRedemptionTime();
            
            if (timeSinceLastRedemption < cooldown) {
                long remaining = (cooldown - timeSinceLastRedemption) / 1000;
                String msg = plugin.getConfig().getString("messages.cooldown-active", "&ePlease wait %seconds% seconds before redeeming another code!");
                msg = msg.replace("%seconds%", String.valueOf(remaining));
                player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.prefix", "&8[&6CodeRewards&8] ") + msg));
                return true;
            }

            // Check daily limit
            int dailyLimit = plugin.getConfig().getInt("daily-limit", 10);
            if (data.getDailyRedemptions() >= dailyLimit) {
                String msg = plugin.getConfig().getString("messages.daily-limit-reached", "&cYou have reached your daily code redemption limit!");
                player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.prefix", "&8[&6CodeRewards&8] ") + msg));
                return true;
            }
        }

        // Redeem the code
        redeemCode(player, code, data);

        return true;
    }

    private void redeemCode(Player player, Code code, PlayerData data) {
        code.incrementUses();
        data.addRedeemedCode(code.getCode());
        data.setLastRedemptionTime(System.currentTimeMillis());
        data.incrementDailyRedemptions();

        // Save data
        plugin.getDataManager().savePlayerData(data);
        plugin.getCodeManager().saveCodes();

        // Process rewards based on type
        String rewardType = code.getRewardType();
        List<String> rewards = code.getRewards();
        StringBuilder rewardSummary = new StringBuilder();

        switch (rewardType.toLowerCase()) {
            case "money":
                for (String reward : rewards) {
                    try {
                        double amount = Double.parseDouble(reward);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + player.getName() + " " + amount);
                        rewardSummary.append("$").append(amount).append(" ");
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid money amount: " + reward);
                    }
                }
                break;

            case "xp":
                for (String reward : rewards) {
                    try {
                        int xpAmount = Integer.parseInt(reward);
                        player.giveExp(xpAmount);
                        rewardSummary.append(xpAmount).append(" XP ");
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid XP amount: " + reward);
                    }
                }
                break;

            case "commands":
                for (String rewardCmd : rewards) {
                    String cmd = rewardCmd.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    rewardSummary.append("command ");
                }
                break;

            case "items":
                // For items, rewards should be in format: MATERIAL:AMOUNT
                for (String reward : rewards) {
                    try {
                        String[] parts = reward.split(":");
                        Material material = Material.valueOf(parts[0]);
                        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, amount));
                        rewardSummary.append(amount).append("x ").append(material.name()).append(" ");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Invalid item reward: " + reward);
                    }
                }
                break;

            default:
                // Default to commands
                for (String rewardCmd : rewards) {
                    String cmd = rewardCmd.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
                rewardSummary.append("rewards");
        }

        // Send success message
        String msg = plugin.getConfig().getString("messages.code-redeemed", "&aSuccessfully redeemed code! You received: %reward%");
        msg = msg.replace("%reward%", rewardSummary.toString().trim());
        player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.prefix", "&8[&6CodeRewards&8] ") + msg));

        // Play success sound
        playSound(player, "success");
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
            String partial = args[0].toUpperCase();
            
            for (Code code : plugin.getCodeManager().getActiveCodes()) {
                if (code.getCode().startsWith(partial)) {
                    completions.add(code.getCode());
                }
            }
            
            return completions;
        }
        return Collections.emptyList();
    }
}
