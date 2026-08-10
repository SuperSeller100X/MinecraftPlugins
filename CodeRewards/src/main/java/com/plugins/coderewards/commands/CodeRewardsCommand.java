package com.plugins.coderewards.commands;

import com.plugins.coderewards.CodeRewards;
import com.plugins.coderewards.gui.CodeRewardsGUI;
import com.plugins.coderewards.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CodeRewardsCommand implements CommandExecutor {
    private final CodeRewards plugin;

    public CodeRewardsCommand(CodeRewards plugin) {
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

        // Open the GUI
        CodeRewardsGUI gui = new CodeRewardsGUI(plugin, player);
        gui.open();

        // Play GUI open sound
        String soundName = plugin.getConfig().getString("sounds.gui-open", "UI_BUTTON_CLICK");
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            // Sound not found, skip
        }

        return true;
    }
}
