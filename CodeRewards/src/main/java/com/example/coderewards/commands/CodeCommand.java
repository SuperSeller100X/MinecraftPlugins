package com.example.coderewards.commands;

import com.example.coderewards.CodeRewardsPlugin;
import com.example.coderewards.data.RewardCode;
import com.example.coderewards.gui.CodeGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CodeCommand implements CommandExecutor, TabCompleter {
    private final CodeRewardsPlugin plugin;

    public CodeCommand(CodeRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Open GUI
            CodeGUI.openGUI(player);
            return true;
        }

        if (args.length == 1) {
            // Redeem code
            String code = args[0];
            redeemCode(player, code);
            return true;
        }

        player.sendMessage("§cUsage: /code [code] or just /code for GUI");
        return true;
    }

    private void redeemCode(Player player, String codeInput) {
        RewardCode rewardCode = plugin.getCodeManager().getCode(codeInput);
        
        if (rewardCode == null) {
            player.sendMessage("§cInvalid code! Please check and try again.");
            return;
        }

        if (!rewardCode.canRedeem()) {
            player.sendMessage("§cThis code has been fully redeemed!");
            return;
        }

        if (rewardCode.isOneTimePerPlayer() && rewardCode.hasRedeemed(player.getUniqueId().toString())) {
            player.sendMessage("§cYou have already redeemed this code!");
            return;
        }

        // Redeem the code
        rewardCode.redeem(player.getUniqueId().toString());

        // Execute commands
        for (String cmd : rewardCode.getCommands()) {
            String formattedCmd = cmd.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd);
        }

        // Give items
        for (ItemStack item : rewardCode.getItems()) {
            if (item != null) {
                player.getInventory().addItem(item.clone());
            }
        }

        player.sendMessage("§aSuccessfully redeemed code: " + rewardCode.getCode());
        player.sendMessage("§aYou received your rewards!");
        
        // Save after redemption
        plugin.getCodeManager().saveCodes();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (RewardCode code : plugin.getCodeManager().getAllCodes()) {
                if (code.canRedeem()) {
                    completions.add(code.getCode());
                }
            }
        }
        return completions;
    }
}
