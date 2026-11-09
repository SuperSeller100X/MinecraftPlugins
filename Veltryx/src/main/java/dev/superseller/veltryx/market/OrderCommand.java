package dev.superseller.veltryx.market;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /order} and {@code /orders} browse buy orders. The direct command
 * {@code /order <item> <amount> <total-price>} escrows the total.
 */
public final class OrderCommand implements CommandExecutor, TabCompleter {

    private final OrderGui gui;
    private final OrderService service;
    private final Messages messages;

    public OrderCommand(OrderGui gui, OrderService service, Messages messages) {
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
        if (!sender.hasPermission("veltryx.order")) {
            sender.sendMessage(messages.render("shop.no-permission"));
            return true;
        }
        gui.cancelPendingPrice(player);
        if (args.length == 0 || args[0].equalsIgnoreCase("mine")) {
            gui.open(player, args.length > 0, 0);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("create")) {
            gui.openCreate(player);
            return true;
        }
        if (command.getName().equals("orders") || args.length < 3) {
            sender.sendMessage(messages.render("order.usage"));
            return true;
        }
        Material material = Material.matchMaterial(args[0]);
        if (material == null) {
            messages.send(player, "ah.unknown-item", Map.of("item", args[0]));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            messages.send(player, "general.invalid-amount", Map.of("input", args[1]));
            return true;
        }
        java.math.BigInteger priceTotal = Money.parseExact(args[2]);
        if (priceTotal == null) {
            messages.send(player, "general.invalid-amount", Map.of("input", args[2]));
            return true;
        }
        OrderService.Result result = service.create(player, material.name(), amount, priceTotal);
        messages.send(player, result.templateKey(), result.values());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("veltryx.order")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String option : List.of("mine", "create")) if (option.startsWith(prefix)) out.add(option);
            if (command.getName().equals("orders")) return out;
            for (Material material : Material.values()) {
                if (out.size() >= 20) {
                    break;
                }
                String name = material.name().toLowerCase(Locale.ROOT);
                if (name.startsWith(prefix) && material != Material.AIR) {
                    out.add(name);
                }
            }
            return out;
        }
        return List.of();
    }
}
