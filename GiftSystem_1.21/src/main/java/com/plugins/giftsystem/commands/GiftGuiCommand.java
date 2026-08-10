package com.plugins.giftsystem.commands;

import com.plugins.giftsystem.GiftSystem;
import com.plugins.giftsystem.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GiftGuiCommand implements CommandExecutor {
    private final GiftSystem plugin;

    public GiftGuiCommand(GiftSystem plugin) {
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

        // For now, just show help - gift creation is done via /gift command
        player.sendMessage(MessageUtils.colorize("&8[&aGiftSystem&8] &6Gift Creation"));
        player.sendMessage(MessageUtils.colorize(""));
        player.sendMessage(MessageUtils.colorize("&7To send a gift:"));
        player.sendMessage(MessageUtils.colorize("  &e1.&7 Hold items in your main hand"));
        player.sendMessage(MessageUtils.colorize("  &e2.&7 Type &e/gift <player> [message]"));
        player.sendMessage(MessageUtils.colorize(""));
        player.sendMessage(MessageUtils.colorize("&7Examples:"));
        player.sendMessage(MessageUtils.colorize("  &e/gift Steve Happy Birthday!"));
        player.sendMessage(MessageUtils.colorize("  &e/gift Alex Here are some diamonds"));
        player.sendMessage(MessageUtils.colorize(""));
        player.sendMessage(MessageUtils.colorize("&7View your received gifts with &e/gifts"));

        return true;
    }
}
