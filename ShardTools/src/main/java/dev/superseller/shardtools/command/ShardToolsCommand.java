package dev.superseller.shardtools.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import dev.superseller.shardtools.ShardToolsPlugin;
import dev.superseller.shardtools.item.ShardCatalog;
import dev.superseller.shardtools.util.Numbers;
import dev.superseller.shardtools.util.TimeWords;

/**
 * /shardtools (/shard, /st) - the player-facing command. Every subcommand
 * also has a short alias:
 *
 *   help h ? | shop s | toggle tg ability ab | balance bal b | pay p |
 *   buy | top t | info i
 *
 * All administrative subcommands live in /shardtoolsadmin (/sta) - typing an
 * old admin subcommand here points the sender there.
 */
public final class ShardToolsCommand implements CommandExecutor, TabCompleter {

    private static final Map<String, String> SUBS = new HashMap<>();

    static {
        SUBS.put("help", "help");
        SUBS.put("h", "help");
        SUBS.put("?", "help");
        SUBS.put("shop", "shop");
        SUBS.put("s", "shop");
        SUBS.put("toggle", "toggle");
        SUBS.put("tg", "toggle");
        SUBS.put("ability", "toggle");
        SUBS.put("ab", "toggle");
        SUBS.put("balance", "balance");
        SUBS.put("bal", "balance");
        SUBS.put("b", "balance");
        SUBS.put("pay", "pay");
        SUBS.put("p", "pay");
        SUBS.put("buy", "buy");
        SUBS.put("top", "top");
        SUBS.put("t", "top");
        SUBS.put("info", "info");
        SUBS.put("i", "info");
        // Former /st admin subcommands - redirect the sender to /sta.
        String[] moved = {"give", "g", "items", "list", "l", "setprice", "price", "sp",
                "shards", "sh", "eco", "interval", "iv", "amount", "am", "award", "aw",
                "reload", "rl", "add", "remove", "edit"};
        for (String name : moved) {
            SUBS.put(name, "admin-moved");
        }
    }

    private final ShardToolsPlugin plugin;

    public ShardToolsCommand(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : SUBS.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            plugin.messages().send(sender, "unknown-command");
            return true;
        }
        switch (sub) {
            case "help":
                return help(sender);
            case "shop":
                return shop(sender);
            case "toggle":
                return toggle(sender);
            case "balance":
                return balance(sender, args);
            case "pay":
                return pay(sender, args);
            case "buy":
                return buy(sender, args);
            case "top":
                return top(sender, args);
            case "info":
                return info(sender);
            case "admin-moved":
                plugin.messages().send(sender, "admin-moved");
                return true;
            default:
                return true;
        }
    }

    private boolean help(CommandSender sender) {
        String[] lines = {"help.header", "help.line-shop", "help.line-toggle", "help.line-balance",
                "help.line-pay", "help.line-buy", "help.line-top", "help.line-info", "help.line-admin"};
        for (String line : lines) {
            sender.sendMessage(plugin.messages().bare(line));
        }
        return true;
    }

    private boolean shop(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission(Permissions.SHOP)) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        plugin.gui().open(player, 0);
        return true;
    }

    /**
     * /st toggle - switches the ability (3x3 mining / tree felling) of the
     * shard tool in the main hand on or off. Only works while holding a
     * shard tool that actually has an ability.
     */
    private boolean toggle(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission(Permissions.TOGGLE)) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = plugin.items().itemId(held);
        ShardCatalog.Entry entry = id == null ? null : plugin.catalog().byId(id);
        if (entry == null || !entry.behavior().toggleable()) {
            plugin.messages().send(player, "toggle.not-tool");
            return true;
        }
        boolean enabled = plugin.items().toggleAbility(held);
        player.getInventory().setItemInMainHand(held);
        plugin.messages().send(player, enabled ? "toggle.enabled" : "toggle.disabled",
                "%item%", entry.displayName());
        return true;
    }

    private boolean balance(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!sender.hasPermission(Permissions.BALANCE_OTHERS)) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            Target target = resolve(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "invalid-player", "%player%", args[1]);
                return true;
            }
            long balance = plugin.accounts().balance(target.uuid(), target.name());
            plugin.messages().send(sender, "balance.other", "%player%", target.name(),
                    "%balance%", Numbers.format(balance), "%symbol%", plugin.settings().symbol());
            return true;
        }
        if (!sender.hasPermission(Permissions.BALANCE)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        Player player = (Player) sender;
        long balance = plugin.accounts().balance(player.getUniqueId(), player.getName());
        plugin.messages().send(player, "balance.self",
                "%balance%", Numbers.format(balance), "%symbol%", plugin.settings().symbol());
        return true;
    }

    private boolean pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission(Permissions.PAY)) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        if (args.length < 3) {
            usage(sender, "/st pay <player> <amount>");
            return true;
        }
        Long amount = Numbers.parse(args[2]);
        if (amount == null || amount <= 0L) {
            plugin.messages().send(sender, "invalid-amount", "%amount%", args[2]);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "invalid-player", "%player%", args[1]);
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "pay.self");
            return true;
        }
        Long newBalance = plugin.accounts().take(player.getUniqueId(), player.getName(), amount);
        if (newBalance == null) {
            long balance = plugin.accounts().balance(player.getUniqueId(), player.getName());
            plugin.messages().send(player, "pay.insufficient",
                    "%balance%", Numbers.format(balance), "%symbol%", plugin.settings().symbol());
            return true;
        }
        long targetBalance = plugin.accounts().add(target.getUniqueId(), target.getName(), amount);
        plugin.messages().send(player, "pay.sent", "%player%", target.getName(),
                "%amount%", Numbers.format(amount), "%balance%", Numbers.format(newBalance),
                "%symbol%", plugin.settings().symbol());
        plugin.messages().send(target, "pay.received", "%player%", player.getName(),
                "%amount%", Numbers.format(amount), "%balance%", Numbers.format(targetBalance),
                "%symbol%", plugin.settings().symbol());
        plugin.accounts().saveAsync();
        return true;
    }

    /**
     * /st buy <shards> - convert in-game money (Vault economy such as
     * EssentialsX) into shards at the configured rate.
     */
    private boolean buy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission(Permissions.BUY)) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        if (!plugin.settings().moneyShopEnabled()) {
            plugin.messages().send(player, "buy.disabled");
            return true;
        }
        if (!plugin.vault().available()) {
            plugin.messages().send(player, "buy.no-vault");
            return true;
        }
        if (args.length < 2) {
            usage(sender, "/st buy <shards>");
            return true;
        }
        Long shards = Numbers.parse(args[1]);
        if (shards == null || shards < 1L) {
            plugin.messages().send(player, "invalid-amount", "%amount%", args[1]);
            return true;
        }
        if (shards < plugin.settings().moneyMinPurchase()) {
            plugin.messages().send(player, "buy.below-min",
                    "%min%", Numbers.format(plugin.settings().moneyMinPurchase()));
            return true;
        }
        if (shards > plugin.settings().moneyMaxPurchase()) {
            plugin.messages().send(player, "buy.above-max",
                    "%max%", Numbers.format(plugin.settings().moneyMaxPurchase()));
            return true;
        }
        double cost = shards * plugin.settings().moneyCostPerShard();
        double money = plugin.vault().balance(player);
        if (money < cost) {
            plugin.messages().send(player, "buy.insufficient-money",
                    "%cost%", plugin.vault().format(cost),
                    "%balance%", plugin.vault().format(Math.max(0.0D, money)));
            return true;
        }
        if (!plugin.vault().withdraw(player, cost)) {
            plugin.messages().send(player, "buy.failed");
            return true;
        }
        long newBalance = plugin.accounts().add(player.getUniqueId(), player.getName(), shards);
        plugin.accounts().saveAsync();
        plugin.messages().send(player, "buy.success",
                "%amount%", Numbers.format(shards),
                "%cost%", plugin.vault().format(cost),
                "%balance%", Numbers.format(newBalance),
                "%symbol%", plugin.settings().symbol());
        org.bukkit.Sound sound = plugin.soundResolver().resolve(plugin.settings().soundPurchase());
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.8f);
        }
        return true;
    }

    private boolean top(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.TOP)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        int limit = 10;
        if (args.length >= 2) {
            Long parsed = Numbers.parse(args[1]);
            if (parsed == null || parsed < 1) {
                plugin.messages().send(sender, "invalid-amount", "%amount%", args[1]);
                return true;
            }
            limit = (int) Math.min(25L, parsed);
        }
        List<dev.superseller.shardtools.economy.ShardAccounts.Account> top = plugin.accounts().top(limit);
        if (top.isEmpty()) {
            plugin.messages().send(sender, "top.empty");
            return true;
        }
        sender.sendMessage(plugin.messages().bare("top.header"));
        int rank = 1;
        for (dev.superseller.shardtools.economy.ShardAccounts.Account account : top) {
            sender.sendMessage(plugin.messages().bare("top.entry",
                    "%rank%", Integer.toString(rank++),
                    "%player%", account.name,
                    "%balance%", Numbers.format(account.balance),
                    "%symbol%", plugin.settings().symbol()));
        }
        return true;
    }

    private boolean info(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission(Permissions.INFO)) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = plugin.items().itemId(held);
        ShardCatalog.Entry entry = id == null ? null : plugin.catalog().byId(id);
        if (entry == null) {
            plugin.messages().send(player, "info.not-holding");
            return true;
        }
        String time;
        if (held == null || !entry.expires()) {
            time = plugin.messages().raw("info.permanent");
        } else {
            Long created = plugin.items().created(held);
            Long lifetime = plugin.items().lifetime(held);
            long remaining = created == null || lifetime == null ? 0L
                    : Math.max(0L, created + lifetime - System.currentTimeMillis());
            time = TimeWords.format(remaining);
        }
        Long price = plugin.priceBook().price(entry.id());
        plugin.messages().send(player, "info.holding", "%item%", entry.displayName(),
                "%time%", time,
                "%price%", Numbers.format(price == null ? 0L : price),
                "%symbol%", plugin.settings().symbol());
        return true;
    }

    private void usage(CommandSender sender, String usage) {
        sender.sendMessage(net.kyori.adventure.text.Component.text(usage));
    }

    // ---------------------------------------------------------------
    // Tab completion
    // ---------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(filter(List.of("shop", "toggle", "balance", "pay", "buy", "top", "info", "help"),
                    args[0]));
        } else if (args.length == 2) {
            String sub = SUBS.get(args[0].toLowerCase(Locale.ROOT));
            if (sub == null) {
                return out;
            }
            switch (sub) {
                case "balance":
                    if (sender.hasPermission(Permissions.BALANCE_OTHERS)) {
                        out.addAll(filter(playerNames(), args[1]));
                    }
                    break;
                case "pay":
                    out.addAll(filter(playerNames(), args[1]));
                    break;
                case "top":
                    out.addAll(filter(List.of("5", "10", "25"), args[1]));
                    break;
                case "buy":
                    out.addAll(filter(List.of("10", "100", "1000"), args[1]));
                    break;
                default:
                    break;
            }
        } else if (args.length == 3) {
            String sub = SUBS.get(args[0].toLowerCase(Locale.ROOT));
            if ("pay".equals(sub)) {
                out.addAll(filter(List.of("100", "1000", "5000"), args[2]));
            }
        }
        return out;
    }

    private List<String> playerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    /** Resolves online or cached-offline players. */
    private Target resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Target(online.getUniqueId(), online.getName());
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return new Target(cached.getUniqueId(), cached.getName() == null ? name : cached.getName());
        }
        return null;
    }

    private static final class Target {
        private final UUID uuid;
        private final String name;

        Target(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        UUID uuid() {
            return uuid;
        }

        String name() {
            return name;
        }
    }
}
