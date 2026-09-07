package dev.superseller.teleportsigns.command;

import dev.superseller.teleportsigns.TeleportSignsPlugin;
import dev.superseller.teleportsigns.model.SignKey;
import dev.superseller.teleportsigns.model.TeleportSign;
import dev.superseller.teleportsigns.model.WarpDestination;
import dev.superseller.teleportsigns.service.TeleportService;
import dev.superseller.teleportsigns.util.LookTarget;
import dev.superseller.teleportsigns.util.Numbers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class TeleportSignsCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final List<String> ROOT = List.of(
            "help", "?", "set", "s", "here", ".", "remove", "r", "unset", "u",
            "info", "i", "list", "l", "cost", "c", "reload", "rl"
    );

    private final TeleportSignsPlugin plugin;

    public TeleportSignsCommand(TeleportSignsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || isHelp(args[0])) {
            plugin.messages().sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (isReload(sub)) {
            return reload(sender);
        }
        if (isList(sub)) {
            return list(sender, args);
        }

        if (!isKnownSub(args[0])) {
            return set(sender, args);
        }

        if (isSet(sub)) {
            return set(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        if (isHere(sub)) {
            return here(sender);
        }
        if (isRemove(sub)) {
            return remove(sender);
        }
        if (isInfo(sub)) {
            return info(sender);
        }
        if (isCost(sub)) {
            return cost(sender, args);
        }

        plugin.messages().sendHelp(sender);
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!Permissions.has(sender, Permissions.RELOAD)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        plugin.reloadAll();
        plugin.messages().send(sender, "reloaded");
        return true;
    }

    private boolean set(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, Permissions.CREATE);
        if (player == null) {
            return true;
        }
        DestinationParser.Result parsed = DestinationParser.parse(args);
        if (!parsed.ok) {
            Map<String, String> ph = new LinkedHashMap<String, String>();
            ph.put("input", parsed.errorInput == null ? "" : parsed.errorInput);
            plugin.messages().send(player, parsed.errorKey, ph);
            return true;
        }

        Sign sign = requireLookedSign(player);
        if (sign == null) {
            return true;
        }

        Location origin = player.getLocation();
        String worldName = parsed.hasWorld() ? parsed.world : origin.getWorld().getName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.messages().send(player, "invalid-world", Map.of("world", worldName));
            return true;
        }

        double x = parsed.x.resolve(origin.getX());
        double y = parsed.y.resolve(origin.getY());
        double z = parsed.z.resolve(origin.getZ());
        boolean hasRot = parsed.hasRotation();
        float yaw = hasRot ? parsed.yaw.floatValue() : origin.getYaw();
        float pitch = hasRot ? parsed.pitch.floatValue() : origin.getPitch();

        WarpDestination dest = new WarpDestination(world.getName(), x, y, z, yaw, pitch, hasRot);
        bind(player, sign, dest, false);
        return true;
    }

    private boolean here(CommandSender sender) {
        Player player = requirePlayer(sender, Permissions.CREATE);
        if (player == null) {
            return true;
        }
        Sign sign = requireLookedSign(player);
        if (sign == null) {
            return true;
        }
        Location loc = player.getLocation();
        WarpDestination dest = new WarpDestination(
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), true);
        bind(player, sign, dest, true);
        return true;
    }

    private void bind(Player player, Sign sign, WarpDestination dest, boolean here) {
        SignKey key = plugin.store().keyOf(sign.getBlock());
        TeleportSign existing = plugin.store().read(sign);
        double cost = existing != null ? existing.cost() : plugin.settings().defaultCost();
        UUID creator = existing != null && existing.creator() != null
                ? existing.creator() : player.getUniqueId();
        long created = existing != null ? existing.createdAt() : System.currentTimeMillis();
        TeleportSign data = new TeleportSign(key, dest, cost, creator, created);
        plugin.store().write(sign, data, plugin.settings().waxOnBind());
        plugin.messages().send(player, here ? "bound-here" : "bound", TeleportService.destPlaceholders(dest));
    }

    private boolean remove(CommandSender sender) {
        Player player = requirePlayer(sender, Permissions.REMOVE);
        if (player == null) {
            return true;
        }
        Sign sign = requireLookedSign(player);
        if (sign == null) {
            return true;
        }
        if (!plugin.store().remove(sign)) {
            plugin.messages().send(player, "not-teleport-sign");
            return true;
        }
        plugin.messages().send(player, "unbound");
        return true;
    }

    private boolean info(CommandSender sender) {
        Player player = requirePlayer(sender, Permissions.INFO);
        if (player == null) {
            return true;
        }
        Sign sign = requireLookedSign(player);
        if (sign == null) {
            return true;
        }
        TeleportSign data = plugin.store().read(sign);
        if (data == null) {
            plugin.messages().send(player, "not-teleport-sign");
            return true;
        }
        WarpDestination dest = data.destination();
        Map<String, String> ph = new LinkedHashMap<String, String>(TeleportService.destPlaceholders(dest));
        ph.put("cost", formatCost(data.cost()));
        ph.put("player", data.creator() == null ? "unknown" : nameOf(data.creator()));
        ph.put("time", TIME.format(Instant.ofEpochMilli(data.createdAt())));
        plugin.messages().send(player, "info-header");
        plugin.messages().send(player, "info-dest", ph);
        plugin.messages().send(player, "info-cost", ph);
        plugin.messages().send(player, "info-created", ph);
        return true;
    }

    private boolean list(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, Permissions.LIST);
        if (player == null) {
            return true;
        }
        int radius = plugin.settings().listRadius();
        if (args.length >= 2) {
            Integer parsed = Numbers.parseInt(args[1]);
            if (parsed == null || parsed.intValue() < 1) {
                plugin.messages().send(player, "invalid-number", Map.of("input", args[1]));
                return true;
            }
            radius = Math.min(256, parsed.intValue());
        }
        List<TeleportSign> found = plugin.store().nearby(player.getLocation(), radius, plugin.settings().listMax());
        if (found.isEmpty()) {
            plugin.messages().send(player, "list-empty", Map.of("radius", Integer.toString(radius)));
            return true;
        }
        plugin.messages().send(player, "list-header", Map.of(
                "count", Integer.toString(found.size()),
                "radius", Integer.toString(radius)));
        for (TeleportSign sign : found) {
            Map<String, String> ph = new LinkedHashMap<String, String>();
            ph.put("world", sign.key().worldName());
            ph.put("x", Integer.toString(sign.key().x()));
            ph.put("y", Integer.toString(sign.key().y()));
            ph.put("z", Integer.toString(sign.key().z()));
            ph.put("dest", sign.destination().compact());
            plugin.messages().send(player, "list-line", ph);
        }
        return true;
    }

    private boolean cost(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender, Permissions.COST);
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "usage-cost");
            return true;
        }
        Double amount = Numbers.parseMoney(args[1]);
        if (amount == null || amount.doubleValue() < 0.0d) {
            plugin.messages().send(player, "invalid-number", Map.of("input", args[1]));
            return true;
        }
        Sign sign = requireLookedSign(player);
        if (sign == null) {
            return true;
        }
        TeleportSign data = plugin.store().read(sign);
        if (data == null) {
            plugin.messages().send(player, "not-teleport-sign");
            return true;
        }
        TeleportSign updated = data.withCost(amount.doubleValue());
        plugin.store().write(sign, updated, false);
        plugin.messages().send(player, "cost-set", Map.of("cost", formatCost(amount.doubleValue())));
        return true;
    }

    private Player requirePlayer(CommandSender sender, String permission) {
        if (!Permissions.has(sender, permission)) {
            plugin.messages().send(sender, "no-permission");
            return null;
        }
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "players-only");
            return null;
        }
        return (Player) sender;
    }

    private Sign requireLookedSign(Player player) {
        org.bukkit.block.Block block = LookTarget.block(player, plugin.settings().maxLookDistance());
        if (block == null) {
            plugin.messages().send(player, "no-target");
            return null;
        }
        if (!LookTarget.isSign(block)) {
            plugin.messages().send(player, "not-a-sign",
                    Map.of("radius", Integer.toString(plugin.settings().maxLookDistance())));
            return null;
        }
        return (Sign) block.getState();
    }

    private String formatCost(double amount) {
        if (plugin.economy().isEnabled()) {
            return plugin.economy().format(amount);
        }
        return Numbers.pretty(amount);
    }

    private static String nameOf(UUID uuid) {
        if (uuid == null) {
            return "unknown";
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name;
    }

    private static boolean isKnownSub(String token) {
        String s = token.toLowerCase(Locale.ROOT);
        return isHelp(s) || isSet(s) || isHere(s) || isRemove(s) || isInfo(s)
                || isList(s) || isCost(s) || isReload(s);
    }

    private static boolean isHelp(String s) {
        return "help".equalsIgnoreCase(s) || "?".equals(s);
    }

    private static boolean isSet(String s) {
        return "set".equalsIgnoreCase(s) || "s".equalsIgnoreCase(s);
    }

    private static boolean isHere(String s) {
        return "here".equalsIgnoreCase(s) || ".".equals(s);
    }

    private static boolean isRemove(String s) {
        return "remove".equalsIgnoreCase(s) || "r".equalsIgnoreCase(s)
                || "unset".equalsIgnoreCase(s) || "u".equalsIgnoreCase(s);
    }

    private static boolean isInfo(String s) {
        return "info".equalsIgnoreCase(s) || "i".equalsIgnoreCase(s);
    }

    private static boolean isList(String s) {
        return "list".equalsIgnoreCase(s) || "l".equalsIgnoreCase(s);
    }

    private static boolean isCost(String s) {
        return "cost".equalsIgnoreCase(s) || "c".equalsIgnoreCase(s);
    }

    private static boolean isReload(String s) {
        return "reload".equalsIgnoreCase(s) || "rl".equalsIgnoreCase(s);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            Set<String> suggestions = new LinkedHashSet<String>();
            for (String root : ROOT) {
                if (visible(sender, root) && root.startsWith(prefix)) {
                    suggestions.add(root);
                }
            }
            if (Permissions.has(sender, Permissions.CREATE)) {
                for (World world : Bukkit.getWorlds()) {
                    if (world.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        suggestions.add(world.getName());
                    }
                }
                if (sender instanceof Player) {
                    addCoordHints(suggestions, (Player) sender, prefix, 0);
                }
            }
            return new ArrayList<String>(suggestions);
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (isReload(first) || isRemove(first) || isInfo(first) || isHere(first) || isHelp(first)) {
            return List.of();
        }
        if (isList(first) && args.length == 2 && Permissions.has(sender, Permissions.LIST)) {
            return filter(List.of("16", "32", "64", "128"), args[1]);
        }
        if (isCost(first) && args.length == 2 && Permissions.has(sender, Permissions.COST)) {
            return filter(List.of("0", "10", "25", "50", "100"), args[1]);
        }
        if (!Permissions.has(sender, Permissions.CREATE)) {
            return List.of();
        }

        int offset = isSet(first) ? 1 : 0;
        int pos = args.length - 1 - offset;
        String prefix = args[args.length - 1];
        Set<String> suggestions = new LinkedHashSet<String>();

        if (pos == 0) {
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                    suggestions.add(world.getName());
                }
            }
        }
        if (sender instanceof Player) {
            addCoordHints(suggestions, (Player) sender, prefix, pos);
        }
        return new ArrayList<String>(suggestions);
    }

    private boolean visible(CommandSender sender, String root) {
        if (isHelp(root)) {
            return true;
        }
        if (isReload(root)) {
            return Permissions.has(sender, Permissions.RELOAD);
        }
        if (isList(root)) {
            return Permissions.has(sender, Permissions.LIST);
        }
        if (isInfo(root)) {
            return Permissions.has(sender, Permissions.INFO);
        }
        if (isRemove(root)) {
            return Permissions.has(sender, Permissions.REMOVE);
        }
        if (isCost(root)) {
            return Permissions.has(sender, Permissions.COST);
        }
        if (isSet(root) || isHere(root)) {
            return Permissions.has(sender, Permissions.CREATE);
        }
        return true;
    }

    private static void addCoordHints(Set<String> out, Player player, String prefix, int pos) {
        Location loc = player.getLocation();
        List<String> hints = new ArrayList<String>();
        if (pos <= 0) {
            hints.add("~");
            hints.add(Numbers.prettyCoord(loc.getX()));
        } else if (pos == 1) {
            hints.add("~");
            hints.add(Numbers.prettyCoord(loc.getY()));
        } else if (pos == 2) {
            hints.add("~");
            hints.add(Numbers.prettyCoord(loc.getZ()));
        } else if (pos == 3) {
            hints.add(Numbers.pretty(loc.getYaw()));
            hints.add("0");
            hints.add("90");
            hints.add("180");
        } else if (pos == 4) {
            hints.add(Numbers.pretty(loc.getPitch()));
            hints.add("0");
        } else if (pos == 5) {
            hints.add(Numbers.pretty(loc.getPitch()));
            hints.add("0");
        }
        for (String hint : hints) {
            if (hint.startsWith(prefix)) {
                out.add(hint);
            }
        }
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<String>();
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(option);
            }
        }
        return out;
    }
}
