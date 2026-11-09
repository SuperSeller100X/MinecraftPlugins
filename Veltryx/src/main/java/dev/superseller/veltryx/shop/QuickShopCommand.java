package dev.superseller.veltryx.shop;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import dev.superseller.veltryx.config.Messages;

/** {@code /quickshop} (alias {@code /qs}) — the 5-row quick-buy panel. */
public final class QuickShopCommand implements CommandExecutor, TabCompleter {

    private final ShopExtrasGui extras;
    private final Messages messages;

    public QuickShopCommand(ShopExtrasGui extras, Messages messages) {
        this.extras = extras;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("shop.players-only"));
            return true;
        }
        if (!player.hasPermission("veltryx.shop")) {
            messages.send(player, "shop.no-permission", java.util.Map.of());
            return true;
        }
        extras.openQuickshop(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
