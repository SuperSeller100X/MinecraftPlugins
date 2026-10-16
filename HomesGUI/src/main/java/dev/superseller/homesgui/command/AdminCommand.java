package dev.superseller.homesgui.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import dev.superseller.homesgui.HomesGUIPlugin;
import dev.superseller.homesgui.config.HomesSettings;
import dev.superseller.homesgui.model.HomeLocation;
import dev.superseller.homesgui.model.PlayerHomes;
import dev.superseller.homesgui.service.HomeService;
import dev.superseller.homesgui.storage.HomesStorage;

/** Staff commands for inspecting and managing player home data. */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final HomesGUIPlugin plugin;
    private final HomeService service;
    private final HomesStorage storage;

    public AdminCommand(HomesGUIPlugin plugin, HomeService service, HomesStorage storage) {
        this.plugin = plugin;
        this.service = service;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || is(args[0], "help", "h")) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (is(sub, "reload", "rl")) {
            if (!allowed(sender, "homesgui.admin.reload")) return true;
            if (args.length != 1) return usage(sender);
            plugin.reloadPluginSettings();
            plugin.messages().send(sender, "admin-reloaded");
            return true;
        }
        if (is(sub, "info", "i")) {
            if (!allowed(sender, "homesgui.admin.info")) return true;
            Target target = target(sender, args, 1);
            if (target == null) return true;
            PlayerHomes data = storage.profile(target.uuid());
            Player online = plugin.getServer().getPlayer(target.uuid());
            int available = online == null ? service.availableSlots(target.uuid()) : service.availableSlots(online);
            plugin.messages().send(sender, "admin-info", "%player%", target.name(),
                    "%saved%", Integer.toString(data.savedCount()), "%available%", Integer.toString(available),
                    "%purchased%", Integer.toString(data.purchasedSlots()), "%bonus%", Integer.toString(data.bonusSlots()));
            return true;
        }
        if (is(sub, "list", "l")) {
            if (!allowed(sender, "homesgui.admin.info")) return true;
            Target target = target(sender, args, 1);
            if (target == null) return true;
            var homes = storage.profile(target.uuid()).homes();
            if (homes.isEmpty()) {
                plugin.messages().send(sender, "admin-no-homes", "%player%", target.name());
                return true;
            }
            plugin.messages().send(sender, "admin-list-header", "%player%", target.name());
            homes.forEach((slot, home) -> plugin.messages().send(sender, "admin-list-home",
                    "%slot%", Integer.toString(slot), "%world%", home.world(),
                    "%x%", coordinate(home.x()), "%y%", coordinate(home.y()), "%z%", coordinate(home.z())));
            return true;
        }
        if (is(sub, "grant", "g")) {
            if (!allowed(sender, "homesgui.admin.slots")) return true;
            Target target = target(sender, args, 1);
            if (target == null) return true;
            if (args.length != 3) return usage(sender);
            Integer amount = nonNegativeInteger(args[2]);
            if (amount == null || amount == 0) {
                plugin.messages().send(sender, "invalid-number");
                return true;
            }
            PlayerHomes data = storage.profile(target.uuid());
            synchronized (data) {
                int current = persistentCapacity(data);
                if (!withinLimit(sender, (long) current + amount)
                        || (long) current + amount > HomesSettings.ABSOLUTE_MAX_HOMES) {
                    plugin.messages().send(sender, "admin-action-failed");
                    return true;
                }
                data.addBonusSlots(amount);
            }
            storage.markDirty();
            plugin.messages().send(sender, "admin-granted", "%count%", amount.toString(), "%player%", target.name());
            return true;
        }
        if (is(sub, "setlimit", "sl")) {
            if (!allowed(sender, "homesgui.admin.slots")) return true;
            Target target = target(sender, args, 1);
            if (target == null) return true;
            if (args.length != 3) return usage(sender);
            Integer total = nonNegativeInteger(args[2]);
            if (total == null || total > HomesSettings.ABSOLUTE_MAX_HOMES) {
                plugin.messages().send(sender, "invalid-number");
                return true;
            }
            PlayerHomes data = storage.profile(target.uuid());
            synchronized (data) {
                int minimum = plugin.settings().homes().freeSlots() + data.purchasedSlots();
                if (total < minimum || total < data.highestSavedSlot()) {
                    plugin.messages().send(sender, "admin-limit-too-low", "%player%", target.name());
                    return true;
                }
                if (!withinLimit(sender, total)) {
                    plugin.messages().send(sender, "admin-action-failed");
                    return true;
                }
                int bonus = total - minimum;
                data.bonusSlots(bonus);
            }
            storage.markDirty();
            plugin.messages().send(sender, "admin-limit-set", "%player%", target.name(), "%limit%", total.toString());
            return true;
        }
        if (is(sub, "remove", "rm")) {
            if (!allowed(sender, "homesgui.admin.slots")) return true;
            Target target = target(sender, args, 1);
            if (target == null) return true;
            if (args.length != 3) return usage(sender);
            Integer amount = nonNegativeInteger(args[2]);
            if (amount == null || amount == 0) {
                plugin.messages().send(sender, "invalid-number");
                return true;
            }
            PlayerHomes data = storage.profile(target.uuid());
            synchronized (data) {
                int bonus = data.bonusSlots();
                int newBonus = bonus - amount;
                int newCapacity = plugin.settings().homes().freeSlots() + data.purchasedSlots() + newBonus;
                if (newBonus < 0 || data.highestSavedSlot() > newCapacity) {
                    plugin.messages().send(sender, "admin-limit-too-low", "%player%", target.name());
                    return true;
                }
                data.bonusSlots(newBonus);
            }
            storage.markDirty();
            plugin.messages().send(sender, "admin-removed", "%count%", amount.toString(), "%player%", target.name());
            return true;
        }
        if (is(sub, "delete", "d")) {
            if (!allowed(sender, "homesgui.admin.data")) return true;
            Target target = target(sender, args, 1);
            if (target == null) return true;
            if (args.length != 3) return usage(sender);
            Integer slot = positiveInteger(args[2]);
            if (slot == null) {
                plugin.messages().send(sender, "invalid-slot");
                return true;
            }
            if (!service.removeHome(target.uuid(), slot)) {
                plugin.messages().send(sender, "slot-empty", "%slot%", slot.toString());
                return true;
            }
            plugin.messages().send(sender, "admin-delete-success", "%slot%", slot.toString(), "%player%", target.name());
            return true;
        }
        if (is(sub, "clear", "c")) {
            if (!allowed(sender, "homesgui.admin.data")) return true;
            Target target = target(sender, args, 1);
            if (target == null) return true;
            if (args.length != 2) return usage(sender);
            PlayerHomes data = storage.profile(target.uuid());
            int removed;
            synchronized (data) {
                removed = data.savedCount();
                data.clearHomes();
            }
            if (removed > 0) storage.markDirty();
            plugin.messages().send(sender, "admin-clear-success", "%count%", Integer.toString(removed),
                    "%player%", target.name());
            return true;
        }
        if (is(sub, "reset", "x")) {
            if (!allowed(sender, "homesgui.admin.data")) return true;
            Target target = target(sender, args, 1);
            if (target == null) return true;
            if (args.length != 2) return usage(sender);
            PlayerHomes data = storage.profile(target.uuid());
            synchronized (data) {
                data.clearHomes();
                data.purchasedSlots(0);
                data.bonusSlots(0);
            }
            storage.markDirty();
            plugin.messages().send(sender, "admin-reset-success", "%player%", target.name());
            return true;
        }
        if (is(sub, "tp", "t")) {
            if (!allowed(sender, "homesgui.admin.teleport")) return true;
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "admin-teleport-player-only");
                return true;
            }
            Target target = target(sender, args, 1);
            if (target == null) return true;
            if (args.length != 3) {
                plugin.messages().send(sender, "admin-teleport-usage");
                return true;
            }
            Integer slot = positiveInteger(args[2]);
            if (slot == null) {
                plugin.messages().send(sender, "invalid-slot");
                return true;
            }
            service.teleportHome(player, target.uuid(), slot);
            return true;
        }
        plugin.messages().send(sender, "admin-usage");
        return true;
    }

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    private Target target(CommandSender sender, String[] args, int nameIndex) {
        if (args.length <= nameIndex) {
            plugin.messages().send(sender, "admin-usage");
            return null;
        }
        String input = args[nameIndex];
        try {
            UUID uuid = UUID.fromString(input);
            return new Target(uuid, input);
        } catch (IllegalArgumentException ignored) {
            // Resolve online or cached names only; never block a region thread on a profile lookup.
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(input)) {
                return new Target(online.getUniqueId(), online.getName());
            }
        }
        OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(input);
        if (cached != null) {
            return new Target(cached.getUniqueId(), input);
        }
        plugin.messages().send(sender, "admin-player-not-found");
        return null;
    }

    private int persistentCapacity(PlayerHomes data) {
        return plugin.settings().homes().freeSlots() + data.purchasedSlots() + data.bonusSlots();
    }

    private boolean withinLimit(CommandSender sender, long total) {
        int maximum = plugin.settings().homes().maxTotal();
        return total <= HomesSettings.ABSOLUTE_MAX_HOMES
                && (maximum < 0 || total <= maximum);
    }

    private void sendHelp(CommandSender sender) {
        if (!sender.hasPermission("homesgui.admin")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        for (String key : List.of("admin-help-header", "admin-help-reload", "admin-help-info", "admin-help-list",
                "admin-help-grant", "admin-help-setlimit", "admin-help-remove", "admin-help-delete",
                "admin-help-clear", "admin-help-reset", "admin-help-teleport")) {
            plugin.messages().send(sender, key);
        }
    }

    private boolean usage(CommandSender sender) {
        plugin.messages().send(sender, "admin-usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("homesgui.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("reload", "rl", "info", "i", "list", "l", "grant", "g", "setlimit", "sl",
                    "remove", "rm", "delete", "d", "clear", "c", "reset", "x", "tp", "t", "help", "h"), args[0]);
        }
        if (args.length == 2 && !is(args[0].toLowerCase(Locale.ROOT), "reload", "rl", "help", "h")) {
            return filter(targetSuggestions(), args[1]);
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (is(sub, "grant", "g", "remove", "rm")) return filter(List.of("1", "2", "4", "5"), args[2]);
            if (is(sub, "setlimit", "sl")) return filter(List.of("4", "5", "8", "10"), args[2]);
            if (is(sub, "delete", "d", "tp", "t")) {
                Target target = resolveTarget(args[1]);
                if (target != null) {
                    return filter(storage.profile(target.uuid()).homes().keySet().stream()
                            .map(String::valueOf).toList(), args[2]);
                }
            }
        }
        return List.of();
    }

    private List<String> targetSuggestions() {
        List<String> result = new ArrayList<>();
        plugin.getServer().getOnlinePlayers().stream().map(Player::getName).forEach(result::add);
        return result.stream().distinct().toList();
    }

    private Target resolveTarget(String input) {
        try {
            return new Target(UUID.fromString(input), input);
        } catch (IllegalArgumentException ignored) {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.getName().equalsIgnoreCase(input)) {
                    return new Target(online.getUniqueId(), online.getName());
                }
            }
            OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(input);
            return cached == null ? null : new Target(cached.getUniqueId(), input);
        }
    }

    private static List<String> filter(List<String> source, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return source.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private static Integer positiveInteger(String value) {
        Integer parsed = nonNegativeInteger(value);
        return parsed != null && parsed > 0 ? parsed : null;
    }

    private static Integer nonNegativeInteger(String value) {
        try {
            int result = Integer.parseInt(value);
            return result >= 0 ? result : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String coordinate(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static boolean is(String value, String... choices) {
        for (String choice : choices) {
            if (choice.equals(value)) return true;
        }
        return false;
    }

    private record Target(UUID uuid, String name) { }
}
