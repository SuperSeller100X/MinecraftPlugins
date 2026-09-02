package dev.superseller.shardtools.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.superseller.shardtools.ShardToolsPlugin;
import dev.superseller.shardtools.item.Behavior;
import dev.superseller.shardtools.item.EnchantSpec;
import dev.superseller.shardtools.item.ShardCatalog;
import dev.superseller.shardtools.util.Numbers;
import dev.superseller.shardtools.util.TimeWords;

/**
 * /shardtoolsadmin (/sta) - every administrative subcommand, each with a
 * short alias:
 *
 *   help h ? | give g | items list l | add a create | remove rm delete del |
 *   edit e | setprice price sp | shards sh eco | interval iv | amount am |
 *   award aw | reload rl
 *
 * add/remove/edit write straight into config.yml (items: section) and
 * reload the catalog, so the shop updates immediately.
 */
public final class ShardToolsAdminCommand implements CommandExecutor, TabCompleter {

    private static final Map<String, String> SUBS = new HashMap<>();

    static {
        SUBS.put("help", "help");
        SUBS.put("h", "help");
        SUBS.put("?", "help");
        SUBS.put("give", "give");
        SUBS.put("g", "give");
        SUBS.put("items", "items");
        SUBS.put("list", "items");
        SUBS.put("l", "items");
        SUBS.put("add", "add");
        SUBS.put("a", "add");
        SUBS.put("create", "add");
        SUBS.put("remove", "remove");
        SUBS.put("rm", "remove");
        SUBS.put("delete", "remove");
        SUBS.put("del", "remove");
        SUBS.put("edit", "edit");
        SUBS.put("e", "edit");
        SUBS.put("setprice", "setprice");
        SUBS.put("price", "setprice");
        SUBS.put("sp", "setprice");
        SUBS.put("shards", "shards");
        SUBS.put("sh", "shards");
        SUBS.put("eco", "shards");
        SUBS.put("interval", "interval");
        SUBS.put("iv", "interval");
        SUBS.put("amount", "amount");
        SUBS.put("am", "amount");
        SUBS.put("award", "award");
        SUBS.put("aw", "award");
        SUBS.put("reload", "reload");
        SUBS.put("rl", "reload");
    }

    private static final List<String> EDIT_KEYS =
            List.of("name", "material", "price", "lifetime", "behavior", "enchants", "lore");

    private final ShardToolsPlugin plugin;

    public ShardToolsAdminCommand(ShardToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : SUBS.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            plugin.messages().send(sender, "unknown-admin-command");
            return true;
        }
        switch (sub) {
            case "help":
                return help(sender);
            case "give":
                return give(sender, args);
            case "items":
                return items(sender);
            case "add":
                return add(sender, args);
            case "remove":
                return remove(sender, args);
            case "edit":
                return edit(sender, args);
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
        String[] lines = {"admin-help.header", "admin-help.line-give", "admin-help.line-items",
                "admin-help.line-add", "admin-help.line-remove", "admin-help.line-edit",
                "admin-help.line-setprice", "admin-help.line-shards", "admin-help.line-interval",
                "admin-help.line-amount", "admin-help.line-award", "admin-help.line-reload"};
        for (String line : lines) {
            sender.sendMessage(plugin.messages().bare(line));
        }
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.GIVE)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            usage(sender, "/sta give <player> <item> [amount]");
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
        if (entry.isCommandItem()) {
            // Command items (spawners, crate keys...) run their console command
            // instead of handing out a physical icon.
            for (int i = 0; i < amount; i++) {
                String command = entry.command().replace("%player%", target.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        } else {
            int remaining = amount;
            while (remaining > 0) {
                ItemStack stack = plugin.items().create(entry, remaining);
                HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
                for (ItemStack rest : leftover.values()) {
                    target.getWorld().dropItem(target.getLocation(), rest);
                }
                remaining -= stack.getAmount();
            }
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

    // ---------------------------------------------------------------
    // Catalog management: /sta add, /sta remove, /sta edit
    // ---------------------------------------------------------------

    /** /sta add <id> <material> <price> [lifetimeHours] [behavior] */
    private boolean add(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.MANAGE)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 4) {
            usage(sender, "/sta add <id> <material> <price> [lifetimeHours] [behavior]");
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (plugin.catalog().byId(id) != null) {
            plugin.messages().send(sender, "catalog.exists", "%item%", id);
            return true;
        }
        Material material = Material.matchMaterial(args[2].toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.messages().send(sender, "catalog.invalid-material", "%material%", args[2]);
            return true;
        }
        Long price = Numbers.parse(args[3]);
        if (price == null || price < 0L) {
            plugin.messages().send(sender, "invalid-amount", "%amount%", args[3]);
            return true;
        }
        long lifetimeHours = 0L;
        if (args.length >= 5) {
            Long parsed = Numbers.parse(args[4]);
            if (parsed == null || parsed < 0L) {
                plugin.messages().send(sender, "invalid-amount", "%amount%", args[4]);
                return true;
            }
            lifetimeHours = parsed;
        }
        Behavior behavior = Behavior.NONE;
        if (args.length >= 6) {
            behavior = strictBehavior(args[5]);
            if (behavior == null) {
                plugin.messages().send(sender, "catalog.invalid-behavior", "%behavior%", args[5]);
                return true;
            }
        }
        String path = "items." + id;
        plugin.getConfig().set(path + ".material", material.name());
        plugin.getConfig().set(path + ".name",
                "<white>" + prettyName(material.name()) + "</white>");
        plugin.getConfig().set(path + ".price", price);
        plugin.getConfig().set(path + ".lifetime-hours", lifetimeHours);
        plugin.getConfig().set(path + ".behavior", behavior.name());
        plugin.getConfig().set(path + ".enchants", new ArrayList<String>());
        plugin.getConfig().set(path + ".lore", new ArrayList<String>());
        plugin.saveConfig();
        plugin.rebuildCatalog();
        plugin.messages().send(sender, "catalog.added", "%item%", id,
                "%price%", Numbers.format(price), "%symbol%", plugin.settings().symbol());
        return true;
    }

    /** /sta remove <id> */
    private boolean remove(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.MANAGE)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            usage(sender, "/sta remove <id>");
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (plugin.catalog().byId(id) == null) {
            plugin.messages().send(sender, "invalid-item", "%item%", args[1]);
            return true;
        }
        plugin.getConfig().set("items." + id, null);
        plugin.saveConfig();
        plugin.rebuildCatalog();
        plugin.messages().send(sender, "catalog.removed", "%item%", id);
        return true;
    }

    /** /sta edit <id> <name|material|price|lifetime|behavior|enchants|lore> <value...> */
    private boolean edit(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.MANAGE)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 4) {
            usage(sender, "/sta edit <id> <name|material|price|lifetime|behavior|enchants|lore> <value>");
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (plugin.catalog().byId(id) == null) {
            plugin.messages().send(sender, "invalid-item", "%item%", args[1]);
            return true;
        }
        String key = args[2].toLowerCase(Locale.ROOT);
        String value = join(args, 3);
        String path = "items." + id;
        switch (key) {
            case "name": {
                plugin.getConfig().set(path + ".name", value);
                break;
            }
            case "material": {
                Material material = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
                if (material == null) {
                    plugin.messages().send(sender, "catalog.invalid-material", "%material%", value);
                    return true;
                }
                plugin.getConfig().set(path + ".material", material.name());
                value = material.name();
                break;
            }
            case "price": {
                Long price = Numbers.parse(value);
                if (price == null || price < 0L) {
                    plugin.messages().send(sender, "invalid-amount", "%amount%", value);
                    return true;
                }
                plugin.getConfig().set(path + ".price", price);
                // Also refresh the runtime override so the new price is
                // effective even if /sta setprice was used before.
                plugin.runtimeStore().setPriceOverride(id, price);
                value = Numbers.format(price);
                break;
            }
            case "lifetime": {
                Long hours = Numbers.parse(value);
                if (hours == null || hours < 0L) {
                    plugin.messages().send(sender, "invalid-amount", "%amount%", value);
                    return true;
                }
                plugin.getConfig().set(path + ".lifetime-hours", hours);
                value = Numbers.format(hours) + "h";
                break;
            }
            case "behavior": {
                Behavior behavior = strictBehavior(value);
                if (behavior == null) {
                    plugin.messages().send(sender, "catalog.invalid-behavior", "%behavior%", value);
                    return true;
                }
                plugin.getConfig().set(path + ".behavior", behavior.name());
                value = behavior.name();
                break;
            }
            case "enchants": {
                // Comma-separated "enchant:level" list; "none" clears them.
                List<String> specs = new ArrayList<>();
                if (!value.equalsIgnoreCase("none")) {
                    for (String part : value.split(",")) {
                        String spec = part.trim();
                        if (spec.isEmpty()) {
                            continue;
                        }
                        if (EnchantSpec.parse(spec) == null
                                || plugin.enchantResolver().resolve(EnchantSpec.parse(spec).enchant()) == null) {
                            plugin.messages().send(sender, "catalog.invalid-enchant", "%enchant%", spec);
                            return true;
                        }
                        specs.add(spec.toLowerCase(Locale.ROOT));
                    }
                }
                plugin.getConfig().set(path + ".enchants", specs);
                value = specs.isEmpty() ? "none" : String.join(", ", specs);
                break;
            }
            case "lore": {
                // MiniMessage lines separated with "|"; "none" clears the lore.
                List<String> lines = new ArrayList<>();
                if (!value.equalsIgnoreCase("none")) {
                    for (String part : value.split("\\|")) {
                        String line = part.trim();
                        if (!line.isEmpty()) {
                            lines.add(line);
                        }
                    }
                }
                plugin.getConfig().set(path + ".lore", lines);
                value = lines.isEmpty() ? "none" : Integer.toString(lines.size()) + " line(s)";
                break;
            }
            default:
                plugin.messages().send(sender, "catalog.invalid-key", "%key%", args[2]);
                return true;
        }
        plugin.saveConfig();
        plugin.rebuildCatalog();
        plugin.messages().send(sender, "catalog.edited", "%item%", id, "%key%", key, "%value%", value);
        return true;
    }

    private boolean setPrice(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.SETPRICE)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            usage(sender, "/sta setprice <item> <price>");
            return true;
        }
        ShardCatalog.Entry entry = plugin.catalog().byId(args[1]);
        if (entry == null) {
            plugin.messages().send(sender, "invalid-item", "%item%", args[1]);
            return true;
        }
        Long price = Numbers.parse(args[2]);
        if (price == null || price < 0L) {
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
            usage(sender, "/sta shards <player> <give|take|set> <amount>");
            return true;
        }
        Target target = resolve(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "invalid-player", "%player%", args[1]);
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        Long amount = Numbers.parse(args[3]);
        if (amount == null || amount < 0L) {
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
                usage(sender, "/sta shards <player> <give|take|set> <amount>");
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
            usage(sender, "/sta interval <minutes>");
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
            usage(sender, "/sta amount <shards>");
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
            usage(sender, "/sta award <on|off>");
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

    private static String join(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    /** Like Behavior.parse but returns null instead of silently using NONE. */
    private static Behavior strictBehavior(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (Behavior behavior : Behavior.values()) {
            if (behavior.name().equals(normalized)) {
                return behavior;
            }
        }
        return null;
    }

    /** NETHERITE_PICKAXE -> "Netherite Pickaxe". */
    private static String prettyName(String materialName) {
        String[] parts = materialName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
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
                case "give":
                case "shards":
                    out.addAll(filter(playerNames(), args[1]));
                    break;
                case "remove":
                case "edit":
                case "setprice":
                    out.addAll(filter(itemIds(), args[1]));
                    break;
                case "interval":
                    out.addAll(filter(List.of("5", "10", "15", "30", "60"), args[1]));
                    break;
                case "amount":
                    out.addAll(filter(List.of("1", "5", "10", "25", "50"), args[1]));
                    break;
                case "award":
                    out.addAll(filter(List.of("on", "off"), args[1]));
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
                case "add":
                    out.addAll(filter(List.of("NETHERITE_PICKAXE", "NETHERITE_AXE", "NETHERITE_SHOVEL",
                            "NETHERITE_SWORD", "NETHERITE_HOE", "BOW", "CROSSBOW", "MACE", "POTION"),
                            args[2]));
                    break;
                case "edit":
                    out.addAll(filter(EDIT_KEYS, args[2]));
                    break;
                case "shards":
                    out.addAll(filter(List.of("give", "take", "set"), args[2]));
                    break;
                case "setprice":
                    out.addAll(filter(List.of("500", "1000", "3000"), args[2]));
                    break;
                default:
                    break;
            }
        } else if (args.length == 4) {
            String sub = SUBS.get(args[0].toLowerCase(Locale.ROOT));
            if ("give".equals(sub)) {
                out.addAll(filter(List.of("1", "5", "64"), args[3]));
            } else if ("shards".equals(sub)) {
                out.addAll(filter(List.of("100", "1000", "3000"), args[3]));
            } else if ("add".equals(sub)) {
                out.addAll(filter(List.of("100", "500", "1000", "3000"), args[3]));
            } else if ("edit".equals(sub) && "behavior".equalsIgnoreCase(args[2])) {
                List<String> behaviors = new ArrayList<>();
                for (Behavior behavior : Behavior.values()) {
                    behaviors.add(behavior.name());
                }
                out.addAll(filter(behaviors, args[3]));
            }
        } else if (args.length == 5 && "add".equals(SUBS.get(args[0].toLowerCase(Locale.ROOT)))) {
            out.addAll(filter(List.of("0", "24", "48"), args[4]));
        } else if (args.length == 6 && "add".equals(SUBS.get(args[0].toLowerCase(Locale.ROOT)))) {
            List<String> behaviors = new ArrayList<>();
            for (Behavior behavior : Behavior.values()) {
                behaviors.add(behavior.name());
            }
            out.addAll(filter(behaviors, args[5]));
        }
        return out;
    }

    private List<String> visibleSubcommands(CommandSender sender) {
        List<String> subs = new ArrayList<>();
        subs.add("help");
        if (sender.hasPermission(Permissions.GIVE)) {
            subs.add("give");
        }
        if (sender.hasPermission(Permissions.ITEMS)) {
            subs.add("items");
        }
        if (sender.hasPermission(Permissions.MANAGE)) {
            subs.add("add");
            subs.add("remove");
            subs.add("edit");
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
