package com.example.coderewards.commands;

import com.example.coderewards.CodeRewardsPlugin;
import com.example.coderewards.data.RewardCode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class CodeAdminCommand implements CommandExecutor, TabCompleter {
    private final CodeRewardsPlugin plugin;

    public CodeAdminCommand(CodeRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("coderewards.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add":
                handleAdd(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== CodeRewards Admin Commands ===");
        sender.sendMessage("§e/codeadmin add <code> [maxUses] [oneTimePerPlayer] - Create a new code");
        sender.sendMessage("§e/codeadmin remove <code> - Remove a code");
        sender.sendMessage("§e/codeadmin list - List all codes");
        sender.sendMessage("§e/codeadmin reload - Reload configuration");
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /codeadmin add <code> [maxUses] [oneTimePerPlayer]");
            return;
        }

        String code = args[1];
        int maxUses = args.length > 2 ? Integer.parseInt(args[2]) : -1;
        boolean oneTimePerPlayer = args.length <= 3 || Boolean.parseBoolean(args[3]);

        // Create sample rewards (can be customized via config later)
        List<String> commands = Arrays.asList("say {player} redeemed code " + code + "!");
        List<ItemStack> items = Arrays.asList(new ItemStack(Material.DIAMOND, 5));

        RewardCode rewardCode = new RewardCode(code, commands, items, maxUses, oneTimePerPlayer);

        if (plugin.getCodeManager().addCode(rewardCode)) {
            sender.sendMessage("§aSuccessfully created code: " + code);
        } else {
            sender.sendMessage("§cCode already exists!");
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /codeadmin remove <code>");
            return;
        }

        String code = args[1];
        if (plugin.getCodeManager().removeCode(code)) {
            sender.sendMessage("§aSuccessfully removed code: " + code);
        } else {
            sender.sendMessage("§cCode not found!");
        }
    }

    private void handleList(CommandSender sender) {
        Collection<RewardCode> codes = plugin.getCodeManager().getAllCodes();
        if (codes.isEmpty()) {
            sender.sendMessage("§cNo codes found!");
            return;
        }

        sender.sendMessage("§6=== Reward Codes ===");
        for (RewardCode code : codes) {
            sender.sendMessage("§e" + code.getCode() + 
                    " §7- Uses: " + code.getUsesLeft() + "/" + code.getMaxUses() +
                    " §7- OneTime: " + code.isOneTimePerPlayer());
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getCodeManager().loadCodes();
        sender.sendMessage("§aConfiguration reloaded!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("add", "remove", "list", "reload"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("remove")) {
                for (RewardCode code : plugin.getCodeManager().getAllCodes()) {
                    completions.add(code.getCode());
                }
            }
        }
        
        return completions;
    }
}
