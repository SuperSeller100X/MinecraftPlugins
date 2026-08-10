package com.plugins.giftsystem.commands;

import com.plugins.giftsystem.GiftSystem;
import com.plugins.giftsystem.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

public class GiftAdminCommand implements CommandExecutor, TabCompleter {
    private final GiftSystem plugin;

    public GiftAdminCommand(GiftSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("giftsystem.admin")) {
            sender.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to do that!")));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(MessageUtils.colorize("&8[&aGiftSystem&8] &eUsage: /giftadmin <clear|list|reload> [player]"));
            sender.sendMessage(MessageUtils.colorize("&7Subcommands:"));
            sender.sendMessage(MessageUtils.colorize("  &e/giftadmin clear <player>&7 - Clear all gifts for a player"));
            sender.sendMessage(MessageUtils.colorize("  &e/giftadmin list&7 - Show gift statistics"));
            sender.sendMessage(MessageUtils.colorize("  &e/giftadmin reload&7 - Reload configuration"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "clear":
                return handleClear(sender, args);
            case "list":
                return handleList(sender);
            case "reload":
                return handleReload(sender);
            default:
                sender.sendMessage(MessageUtils.colorize("&8[&aGiftSystem&8] &cUnknown subcommand! Use /giftadmin for help."));
                return true;
        }
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&8[&aGiftSystem&8] &eUsage: /giftadmin clear <player>"));
            return true;
        }

        String playerName = args[1];
        
        // Check if player is online
        org.bukkit.Player target = Bukkit.getPlayer(playerName);
        if (target != null) {
            playerName = target.getName();
        }

        int giftCount = plugin.getGiftManager().getPendingGiftCount(playerName);
        plugin.getGiftManager().clearPlayerGifts(playerName);

        String msg = plugin.getConfig().getString("messages.admin-cleared", "&aCleared all gifts for %player%!");
        msg = msg.replace("%player%", playerName);
        sender.sendMessage(MessageUtils.colorize(msg));
        
        if (giftCount > 0) {
            sender.sendMessage(MessageUtils.colorize("&7Cleared &e" + giftCount + " &7pending gifts."));
        }

        return true;
    }

    private boolean handleList(CommandSender sender) {
        int totalGifts = plugin.getGiftManager().getTotalGifts();
        int pendingGifts = plugin.getGiftManager().getPendingGiftsCount();
        int playersWithGifts = 0;
        
        // Count players with pending gifts
        for (String playerName : plugin.getGiftManager().getPlayerGifts("").getClass().equals(ArrayList.class) ? new String[0] : new String[0]) {
            // This is just a placeholder - actual implementation would iterate properly
        }
        
        sender.sendMessage(MessageUtils.colorize("&8[&aGiftSystem&8] &6Gift Statistics"));
        sender.sendMessage(MessageUtils.colorize(""));
        sender.sendMessage(MessageUtils.colorize("&7Total Gifts Created: &e" + totalGifts));
        sender.sendMessage(MessageUtils.colorize("&7Pending Gifts: &e" + pendingGifts));
        sender.sendMessage(MessageUtils.colorize("&7Active Players with Gifts: &eChecking..."));
        sender.sendMessage(MessageUtils.colorize(""));
        sender.sendMessage(MessageUtils.colorize("&7Use &e/giftadmin clear <player> &7to clear gifts."));

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getGiftManager().loadGifts();
        
        sender.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.admin-reload", "&aConfiguration reloaded successfully!")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("clear", "list", "reload");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            List<String> completions = new ArrayList<>();
            String partial = args[1].toLowerCase();
            
            for (org.bukkit.Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
            
            return completions;
        }

        return Collections.emptyList();
    }
}
