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
 * /shardtools (/shard, /st) - every subcommand also has a short alias:
 *
 *   help h ? | shop s | balance bal b | pay p | top t | info i |
 *   give g | items l | setprice sp | shards sh | interval iv |
 *   amount am | award aw | reload rl
 */
public final class ShardToolsCommand implements CommandExecutor, TabCompleter {

    private static final Map<String, String> SUBS = new HashMap<>();

    static {
        SUBS.put("help", "help");
        SUBS.put("h", "help");
        SUBS.put("?", "help");
        SUBS.put("shop", "shop");
        SUBS.put("s", "shop");
        SUBS.put("balance", "balance");
        SUBS.put("bal", "balance");
        SUBS.put("b", "balance");
        SUBS.put("pay", "pay");
        SUBS.put("p", "pay");
        SUBS.put("top", "top");
        SUBS.put("t", "top");
        SUBS.put("info", "info");
        SUBS.put("i", "info");
        SUBS.put("give", "give");
        SUBS.put("g", "give");
        SUBS.put("items", "items");
        SUBS.put("list", "items");
        SUBS.put("l", "items");
        SUBS.put("setprice", "setprice");
        SUBS.put("price", "setprice");
        SUBS.put("sp", "setprice");
        SUBS.put("shards", "shards");
        SUBS.put("sh", "shards");
        SUBS.put("interval", "interval");
        SUBS.put("iv", "interval");
        SUBS.put("amount", "amount");
        SUBS.put("am", "amount");
        SUBS.put("award", "award");
        SUBS.put("aw", "award");
        SUBS.put("reload", "reload");
        SUBS.put("rl", "reload");
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
            case "balance":
                return balance(sender, args);
            case "pay":
                return pay(sender, args);
            case "top":
                return top(sender, args);
            case "info":
                return info(sender);
            case "give":
                return give(sender, args);
            case "items":
                return items(sender);
            case "setprice":
                return setPrice(sender, args);
            case "shards":
                return shards(sender, args);
            case "interval":
                return interval(sender, args);
            case "amount":
                return amount(sender, args);
            case "award":
                return award(sender, args);
            case "reload":
                return reload(sender);
            default:
                return true;
        }
    }

    private boolean help(CommandSender sender) {
        String[] lines = {"help.header", "help.line-shop", "help.line-balance", "help.line-pay",
                "help.line-top", "help.line-info", "help.line-give", "help.line-items",
                "help.line-setprice", "help.line-shards", "help.line-interval", "help.line-amount",
                "help.line-award", "help.line-reload"};
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

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.GIVE)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            usage(sender, "/st give <player> <item> [amount]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "invalid-player", "%player%", args[1]);
            return true;
        }
        ShardCatalog.Entry entry = plugin.catalog().byId(args[2]);
        if (entry == null) {
            plugin.messages().send(sender, "invalid-item", "%item%", args[2]);
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            Long parsed = Numbers.parse(args[3]);
            if (parsed == null || parsed < 1) {
                plugin.messages().send(sender, "invalid-amount", "%amount%", args[3]);
                return true;
            }
            amount = (int) Math.min(64L * 27L, parsed);
        }
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = plugin.items().create(entry, remaining);
            HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
            for (ItemStack rest : leftover.values()) {
                target.getWorld().dropItem(target.getLocation(), rest);
            }
            remaining -= stack.getAmount();
        }
        plugin.messages().send(sender, "give.given", "%player%", target.getName(),
                "%item%", entry.displayName(), "%amount%", Numbers.format(amount));
        plugin.messages().send(target, "give.received",
                "%item%", entry.displayName(), "%amount%", Numbers.format(amount));
        return true;
    }

    private boolean items(CommandSender sender) {
        if (!sender.hasPermission(Permissions.ITEMS)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        sender.sendMessage(plugin.messages().bare("items.header",
                "%symbol%", plugin.settings().symbol()));
        for (ShardCatalog.Entry entry : plugin.catalog().ordered()) {
            Long price = plugin.priceBook().price(entry.id());
            String time = entry.expires()
                    ? TimeWords.format(entry.lifetimeMs()) : plugin.messages().raw("time.permanent");
            sender.sendMessage(plugin.messages().bare("items.entry",
                    "%item%", entry.id(),
                    "%price%", Numbers.format(price == null ? 0L : price),
                    "%time%", time,
                    "%symbol%", plugin.settings().symbol()));
        }
        return true;
    }

    private boolean setPrice(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.SETPRICE)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            usage(sender, "/st setprice <item> <price>");
            return true;
        }
        ShardCatalog.Entry entry = plugin.catalog().byId(args[1]);
        if (entry == null) {
            plugin.messages().send(sender, "invalid-item", "%item%", args[1]);
            return true;
        }
        Long price = Numbers.parse(args[2]);
        if (price == null) {
            plugin.messages().send(sender, "invalid-amount", "%amount%", args[2]);
            return true;
        }
        plugin.priceBook().setPrice(entry.id(), price);
        plugin.runtimeStore().setPriceOverride(entry.id(), price);
        plugin.messages().send(sender, "price.set", "%item%", entry.id(),
                "%price%", Numbers.format(price), "%symbol%", plugin.settings().symbol());
        return true;
    }

    private boolean shards(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ECONOMY)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 4) {
            usage(sender, "/st shards <player> <give|take|set> <amount>");
            return true;
        }
        Target target = resolve(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "invalid-player", "%player%", args[1]);
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        Long amount = Numbers.parse(args[3]);
        if (amount == null) {
            plugin.messages().send(sender, "invalid-amount", "%amount%", args[3]);
            return true;
        }
        String symbol = plugin.settings().symbol();
        switch (action) {
            case "give": {
                long balance = plugin.accounts().add(target.uuid(), target.name(), amount);
                plugin.messages().send(sender, "shards.given", "%player%", target.name(),
                        "%amount%", Numbers.format(amount), "%balance%", Numbers.format(balance),
                        "%symbol%", symbol);
                break;
            }
            case "take": {
                Long balance = plugin.accounts().take(target.uuid(), target.name(), amount);
                if (balance == null) {
                    long current = plugin.accounts().balance(target.uuid(), target.name());
                    plugin.messages().send(sender, "shards.insufficient", "%player%", target.name(),
                            "%balance%", Numbers.format(current), "%symbol%", symbol);
                    return true;
                }
                plugin.messages().send(sender, "shards.taken", "%player%", target.name(),
                        "%amount%", Numbers.format(amount), "%balance%", Numbers.format(balance),
                        "%symbol%", symbol);
                break;
            }
            case "set": {
                long balance = plugin.accounts().set(target.uuid(), target.name(), amount);
                plugin.messages().send(sender, "shards.set", "%player%", target.name(),
                        "%balance%", Numbers.format(balance), "%symbol%", symbol);
                break;
            }
            default:
                usage(sender, "/st shards <player> <give|take|set> <amount>");
                return true;
        }
        plugin.accounts().saveAsync();
        return true;
    }

    private boolean interval(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.SETTINGS)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            usage(sender, "/st interval <minutes>");
            return true;
        }
        Long minutes = Numbers.parse(args[1]);
        if (minutes == null || minutes < 1) {
            plugin.messages().send(sender, "invalid-amount", "%amount%", args[1]);
            return true;
        }
        plugin.runtimeStore().setAwardInterval(minutes);
        plugin.restartTasks();
        plugin.messages().send(sender, "award.interval-set", "%minutes%", Numbers.format(minutes));
        return true;
    }

    private boolean amount(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.SETTINGS)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            usage(sender, "/st amount <shards>");
            return true;
        }
        Long amount = Numbers.parse(args[1]);
        if (amount == null) {
            plugin.messages().send(sender, "invalid-amount", "%amount%", args[1]);
            return true;
        }
        plugin.runtimeStore().setAwardAmount(amount);
        plugin.restartTasks();
        plugin.messages().send(sender, "award.amount-set", "%amount%", Numbers.format(amount),
                "%symbol%", plugin.settings().symbol());
        return true;
    }

    private boolean award(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.SETTINGS)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            usage(sender, "/st award <on|off>");
            return true;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        plugin.runtimeStore().setAwardEnabled(enabled);
        plugin.restartTasks();
        plugin.messages().send(sender, "award.enabled",
                "%state%", enabled ? "enabled" : "disabled");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission(Permissions.RELOAD)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        plugin.reloadAll();
        plugin.messages().send(sender, "reload.done");
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
            out.addAll(filter(visibleSubcommands(sender), args[0]));
        } else if (args.length == 2) {
            String sub = SUBS.get(args[0].toLowerCase(Locale.ROOT));
            if (sub == null) {
                return out;
            }
            switch (sub) {
                case "balance":
                    if (sender.hasPermission(Permissions.BALANCE_OTHERS)) {
                        out.addAll(playerNames());
                    }
                    break;
                case "pay":
                case "give":
                    out.addAll(playerNames());
                    break;
                case "shards":
                    out.addAll(playerNames());
                    break;
                case "top":
                    out.addAll(List.of("5", "10", "25"));
                    break;
                case "setprice":
                    out.addAll(filter(itemIds(), args[1]));
                    break;
                case "interval":
                    out.addAll(List.of("5", "10", "15", "30", "60"));
                    break;
                case "amount":
                    out.addAll(List.of("1", "5", "10", "25", "50"));
                    break;
                case "award":
                    out.addAll(List.of("on", "off"));
                    break;
                default:
                    break;
            }
        } else if (args.length == 3) {
            String sub = SUBS.get(args[0].toLowerCase(Locale.ROOT));
            if (sub == null) {
                return out;
            }
            switch (sub) {
                case "give":
                    out.addAll(filter(itemIds(), args[2]));
                    break;
                case "pay":
                    out.addAll(List.of("100", "1000", "5000"));
                    break;
                case "shards":
                    out.addAll(filter(List.of("give", "take", "set"), args[2]));
                    break;
                default:
                    break;
            }
        } else if (args.length == 4) {
            String sub = SUBS.get(args[0].toLowerCase(Locale.ROOT));
            if ("give".equals(sub)) {
                out.addAll(List.of("1", "5", "64"));
            } else if ("shards".equals(sub)) {
                out.addAll(List.of("100", "1000", "3000"));
            }
        }
        return out;
    }

    private List<String> visibleSubcommands(CommandSender sender) {
        List<String> subs = new ArrayList<>(List.of("shop", "balance", "pay", "top", "info", "help"));
        if (sender.hasPermission(Permissions.GIVE)) {
            subs.add("give");
        }
        if (sender.hasPermission(Permissions.ITEMS)) {
            subs.add("items");
        }
        if (sender.hasPermission(Permissions.SETPRICE)) {
            subs.add("setprice");
        }
        if (sender.hasPermission(Permissions.ECONOMY)) {
            subs.add("shards");
        }
        if (sender.hasPermission(Permissions.SETTINGS)) {
            subs.add("interval");
            subs.add("amount");
            subs.add("award");
        }
        if (sender.hasPermission(Permissions.RELOAD)) {
            subs.add("reload");
        }
        return subs;
    }

    private List<String> itemIds() {
        List<String> ids = new ArrayList<>();
        for (ShardCatalog.Entry entry : plugin.catalog().ordered()) {
            ids.add(entry.id());
        }
        return ids;
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
