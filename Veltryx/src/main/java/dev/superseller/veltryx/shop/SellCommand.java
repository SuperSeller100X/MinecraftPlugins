package dev.superseller.veltryx.shop;

import dev.superseller.veltryx.config.Messages;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /sell} — opens the sell GUI for the player's inventory.
 */
public final class SellCommand implements CommandExecutor, TabCompleter {

    private final SellGui gui;
    private final Messages messages;

    public SellCommand(SellGui gui, Messages messages) {
        this.gui = gui;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("shop.players-only"));
            return true;
        }
        if (!sender.hasPermission("veltryx.sell")) {
            sender.sendMessage(messages.render("shop.no-permission"));
            return true;
        }
        gui.open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
