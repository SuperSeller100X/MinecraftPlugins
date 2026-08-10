package com.giftsystem.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReceiveGiftCommand implements CommandExecutor {

    private final GiftSystemPlugin plugin;

    public ReceiveGiftCommand(GiftSystemPlugin plugin) {
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

        // Open the gift GUI
        player.openInventory(plugin.createGiftGUI(player));
        return true;
    }
}
