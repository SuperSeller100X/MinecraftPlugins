package dev.superseller.subscriptions.command;

import dev.superseller.subscriptions.config.PluginSettings;
import dev.superseller.subscriptions.gui.GuiManager;
import dev.superseller.subscriptions.util.Colors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class InboxCommand implements CommandExecutor {

    private final PluginSettings settings;
    private final GuiManager gui;

    public InboxCommand(PluginSettings settings, GuiManager gui) {
        this.settings = settings;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Colors.parse(settings.msg("generic.players-only")));
            return true;
        }
        if (!player.hasPermission("subscriptions.inbox")) {
            player.sendMessage(Colors.parse(settings.prefix() + settings.msg("generic.no-permission")));
            return true;
        }
        gui.openInbox(player, 0);
        return true;
    }
}
