package dev.superseller.veltryx.market;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.Money;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /ah} browses the auction house; {@code /ah sell <total-price>}
 * lists the stack held in the main hand.
 */
public final class AhCommand implements CommandExecutor, TabCompleter {

    private final AhGui gui;
    private final AhService service;
    private final Messages messages;

    public AhCommand(AhGui gui, AhService service, Messages messages) {
        this.gui = gui;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("shop.players-only"));
            return true;
        }
        if (!sender.hasPermission("veltryx.ah")) {
            sender.sendMessage(messages.render("shop.no-permission"));
            return true;
        }
        gui.cancelPendingPrice(player);
        if (args.length == 0) {
            gui.open(player, false, 0);
            return true;
        }
        if (args[0].equalsIgnoreCase("sell")) {
            if (args.length < 2) {
                sender.sendMessage(messages.render("ah.usage-sell"));
                return true;
            }
            java.math.BigInteger priceTotal = Money.parseExact(args[1]);
            if (priceTotal == null) {
                messages.send(player, "general.invalid-amount", Map.of("input", args[1]));
                return true;
            }
            AhService.Result result = service.listHeldItem(player, priceTotal);
            messages.send(player, result.templateKey(), result.values());
            return true;
        }
        if (args[0].equalsIgnoreCase("mine")) {
            gui.open(player, true, 0);
            return true;
        }
        gui.open(player, false, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("veltryx.ah")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new java.util.ArrayList<>();
        for (String option : List.of("sell", "mine")) {
            if (option.startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }
}
