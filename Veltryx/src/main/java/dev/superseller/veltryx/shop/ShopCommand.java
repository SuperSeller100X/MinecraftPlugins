package dev.superseller.veltryx.shop;

import dev.superseller.veltryx.config.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /shop} — opens the shop GUI, optionally straight into a category.
 */
public final class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopGui gui;
    private final ShopService shop;
    private final Messages messages;

    public ShopCommand(ShopGui gui, ShopService shop, Messages messages) {
        this.gui = gui;
        this.shop = shop;
        this.messages = messages;
    }

    /** Wired with the shop extras for /shop search. */
    private ShopExtrasGui extras;

    public void attachExtras(ShopExtrasGui extras) {
        this.extras = extras;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("shop.players-only"));
            return true;
        }
        if (!sender.hasPermission("veltryx.shop")) {
            sender.sendMessage(messages.render("shop.no-permission"));
            return true;
        }
        if (args.length == 0) {
            gui.openMain(player);
            return true;
        }
        String wanted = args[0].toLowerCase(Locale.ROOT);
        if (wanted.equals("search") && extras != null) {
            if (args.length < 2) {
                extras.openSearchPrompt(player);
            } else {
                StringBuilder query = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i++) {
                    query.append(' ').append(args[i]);
                }
                extras.openSearch(player, query.toString(), 0);
            }
            return true;
        }
        for (PriceTable.Category category : shop.prices().categories()) {
            if (category.id().equals(wanted)) {
                gui.openCategory(player, category.id(), 0);
                return true;
            }
        }
        sender.sendMessage(messages.render("shop.unknown-category", java.util.Map.of("category", args[0])));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("veltryx.shop")) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        if ("search".startsWith(prefix)) out.add("search");
        for (PriceTable.Category category : shop.prices().categories()) {
            if (category.id().startsWith(prefix)) {
                out.add(category.id());
            }
        }
        return out;
    }
}
