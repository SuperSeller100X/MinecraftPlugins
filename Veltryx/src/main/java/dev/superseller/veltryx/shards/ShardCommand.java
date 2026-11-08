package dev.superseller.veltryx.shards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.superseller.veltryx.config.Messages;

/**
 * {@code /shard} (aliases {@code /shardtools}, {@code /st}):
 * balance, pay, top, shop, toggle, info - plus staff give/set.
 * There is deliberately no "buy shards for money" subcommand: shards are
 * earned by playing (1 per minute), paid between players or given by staff.
 */
public final class ShardCommand implements CommandExecutor, TabCompleter {

    private final ShardService service;
    private final ShardCatalog catalog;
    private final ShardItems items;
    private final ShardShopGui shopGui;
    private final Messages messages;

    public ShardCommand(ShardService service, ShardCatalog catalog, ShardItems items,
                        ShardShopGui shopGui, Messages messages) {
        this.service = service;
        this.catalog = catalog;
        this.items = items;
        this.shopGui = shopGui;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "balance", "bal" -> {
                return balance(sender, args);
            }
            case "pay" -> {
                return pay(sender, args);
            }
            case "top" -> {
                return top(sender);
            }
            case "shop" -> {
                return shop(sender);
            }
            case "spawners" -> {
                if (sender instanceof Player player) shopGui.openSpawnerShop(player);
                else sender.sendMessage(messages.render("shop.players-only"));
                return true;
            }
            case "toggle" -> {
                return toggle(sender);
            }
            case "info", "help" -> {
                return info(sender);
            }
            case "give" -> {
                return give(sender, args, false);
            }
            case "set" -> {
                return give(sender, args, true);
            }
            default -> {
                info(sender);
                return true;
            }
        }
    }

    private boolean balance(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!(sender instanceof Player player) || !player.hasPermission("veltryx.shards.admin")) {
                messages.send(sender, "shop.no-permission", Map.of());
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages.send(sender, "shards.pay-unknown", Map.of("player", args[1]));
                return true;
            }
            messages.send(sender, "shards.balance-other",
                    Map.of("player", target.getName(), "balance", String.valueOf(service.balance(target.getUniqueId()))));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("shop.players-only"));
            return true;
        }
        messages.send(player, "shards.balance",
                Map.of("balance", String.valueOf(service.balance(player.getUniqueId()))));
        return true;
    }

    private boolean pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("shop.players-only"));
            return true;
        }
        if (args.length < 3) {
            messages.send(player, "shards.usage-pay", Map.of());
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "shards.pay-unknown", Map.of("player", args[1]));
            return true;
        }
        long amount = parseAmount(args[2]);
        if (amount <= 0) {
            messages.send(player, "shards.pay-bad-amount", Map.of());
            return true;
        }
        ShardService.Result result = service.pay(player.getUniqueId(), target.getUniqueId(), amount);
        if (!result.ok()) {
            messages.send(player, result.key(), Map.of());
            return true;
        }
        messages.send(player, "shards.pay-sent",
                Map.of("amount", String.valueOf(amount), "player", target.getName()));
        messages.send(target, "shards.pay-received",
                Map.of("amount", String.valueOf(amount), "player", player.getName()));
        return true;
    }

    private boolean top(CommandSender sender) {
        List<Map.Entry<java.util.UUID, Long>> rows = service.top(10);
        if (rows.isEmpty()) {
            messages.send(sender, "shards.top-empty", Map.of());
            return true;
        }
        messages.send(sender, "shards.top-header", Map.of());
        for (int i = 0; i < rows.size(); i++) {
            Map.Entry<java.util.UUID, Long> row = rows.get(i);
            String name = nameOf(row.getKey());
            messages.send(sender, "shards.top-row",
                    Map.of("rank", String.valueOf(i + 1), "player", name,
                            "balance", String.valueOf(row.getValue())));
        }
        return true;
    }

    private boolean shop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("shop.players-only"));
            return true;
        }
        shopGui.open(player);
        return true;
    }

    private boolean toggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("shop.players-only"));
            return true;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        String id = items.itemId(tool);
        ShardCatalog.Entry entry = catalog.byId(id);
        if (entry == null || !items.hasAbility(entry)) {
            messages.send(player, "shards.toggle-no-tool", Map.of());
            return true;
        }
        boolean enabled = items.toggleAbility(tool);
        messages.send(player, enabled ? "shards.toggle-on" : "shards.toggle-off",
                Map.of("item", entry.displayName()));
        return true;
    }

    private boolean info(CommandSender sender) {
        messages.sendMultiline(sender, "shards.info", Map.of());
        return true;
    }

    private boolean give(CommandSender sender, String[] args, boolean set) {
        if (!(sender instanceof Player player) || !player.hasPermission("veltryx.shards.admin")) {
            messages.send(sender, "shop.no-permission", Map.of());
            return true;
        }
        if (args.length < 3) {
            messages.send(player, "shards.usage-give", Map.of());
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "shards.pay-unknown", Map.of("player", args[1]));
            return true;
        }
        long amount = parseAmount(args[2]);
        if (amount < 0) {
            messages.send(player, "shards.pay-bad-amount", Map.of());
            return true;
        }
        if (set) {
            service.take(target.getUniqueId(), service.balance(target.getUniqueId()));
            service.give(target.getUniqueId(), amount);
        } else {
            service.give(target.getUniqueId(), amount);
        }
        messages.send(player, set ? "shards.set" : "shards.gave",
                Map.of("amount", String.valueOf(amount), "player", target.getName()));
        return true;
    }

    private String nameOf(java.util.UUID id) {
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        return offline != null && offline.getName() != null ? offline.getName() : id.toString();
    }

    private static long parseAmount(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[] {"balance", "pay", "top", "shop", "spawners", "toggle", "info", "give", "set"}) {
                if (s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("pay")
                || args[0].equalsIgnoreCase("balance")
                || args[0].equalsIgnoreCase("give")
                || args[0].equalsIgnoreCase("set"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(java.util.Locale.ROOT)
                        .startsWith(args[1].toLowerCase(java.util.Locale.ROOT))) {
                    out.add(player.getName());
                }
            }
        }
        return out;
    }
}
