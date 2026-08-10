package com.plugins.giftsystem.commands;

import com.plugins.giftsystem.GiftSystem;
import com.plugins.giftsystem.gui.GiftInboxGUI;
import com.plugins.giftsystem.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GiftsCommand implements CommandExecutor {
    private final GiftSystem plugin;

    public GiftsCommand(GiftSystem plugin) {
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

        GiftInboxGUI gui = new GiftInboxGUI(plugin, player);
        gui.open();

        playSound(player);
        return true;
    }

    private void playSound(Player player) {
        String soundName = plugin.getConfig().getString("sounds.gui-open", "UI_BUTTON_CLICK");
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            // Sound not found, skip
        }
    }
}
