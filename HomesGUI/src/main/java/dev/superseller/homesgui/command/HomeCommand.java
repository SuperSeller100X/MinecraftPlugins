package dev.superseller.homesgui.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import dev.superseller.homesgui.HomesGUIPlugin;
import dev.superseller.homesgui.gui.HomeMenu;
import dev.superseller.homesgui.model.PlayerHomes;
import dev.superseller.homesgui.service.HomeService;

/** Player command tree and tab completion for all public HomesGUI commands. */
public final class HomeCommand implements CommandExecutor, TabCompleter {

    private final HomesGUIPlugin plugin;
    private final HomeService service;
    private final HomeMenu menu;

    public HomeCommand(HomesGUIPlugin plugin, HomeService service, HomeMenu menu) {
        this.plugin = plugin;
        this.service = service;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sethome" -> setCommand(sender, args);
            case "delhome" -> deleteCommand(sender, args);
            case "buyhome" -> buyCommand(sender, args);
            default -> homeCommand(sender, args);
        };
    }

    private boolean homeCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return openMenu(sender);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (is(subcommand, "gui", "g")) {
            if (args.length != 1) {
                return usage(sender);
            }
            return openMenu(sender);
        }
        if (is(subcommand, "set", "s")) {
            return setHome(sender, args, 1);
        }
        if (is(subcommand, "delete", "d")) {
            return deleteHome(sender, args, 1);
        }
        if (is(subcommand, "buy", "b")) {
            return buy(sender, args, 1);
        }
        if (is(subcommand, "price", "p")) {
            return price(sender, args, 1);
        }
        if (is(subcommand, "info", "i")) {
            if (args.length != 1) {
                return usage(sender);
            }
            if (!sender.hasPermission("homesgui.info")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "player-only");
                return true;
            }
            service.sendInfo(player);
            return true;
        }
        if (is(subcommand, "help", "h")) {
            if (args.length != 1) {
                return usage(sender);
            }
            sendHelp(sender);
            return true;
        }
        Integer slot = positiveInteger(subcommand);
        if (slot != null && args.length == 1) {
            if (!sender.hasPermission("homesgui.use")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "player-only");
                return true;
            }
            if (!service.canUseSlot(player, slot)) {
                plugin.messages().send(sender, "slot-locked", "%slot%", Integer.toString(slot));
                return true;
            }
            service.teleportHome(player, slot);
            return true;
        }
        plugin.messages().send(sender, "unknown-command");
        return true;
    }

    private boolean openMenu(CommandSender sender) {
        if (!sender.hasPermission("homesgui.gui")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        menu.open(player);
        return true;
    }

    private boolean setCommand(CommandSender sender, String[] args) {
        return setHome(sender, args, 0);
    }

    private boolean setHome(CommandSender sender, String[] args, int argumentOffset) {
        if (!sender.hasPermission("homesgui.set")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        int remaining = args.length - argumentOffset;
        if (remaining > 1) {
            return usage(sender);
        }
        int slot;
        if (remaining == 0) {
            slot = service.firstEmptySlot(player);
            if (slot < 0) {
                plugin.messages().send(sender, "no-empty-slot");
                return true;
            }
        } else {
            Integer parsed = positiveInteger(args[argumentOffset]);
            if (parsed == null) {
                plugin.messages().send(sender, "invalid-slot");
                return true;
            }
            slot = parsed;
        }
        service.setHome(player, slot);
        return true;
    }

    private boolean deleteCommand(CommandSender sender, String[] args) {
        return deleteHome(sender, args, 0);
    }

    private boolean deleteHome(CommandSender sender, String[] args, int argumentOffset) {
        if (!sender.hasPermission("homesgui.delete")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        int remaining = args.length - argumentOffset;
        if (remaining != 1) {
            return usage(sender);
        }
        Integer slot = positiveInteger(args[argumentOffset]);
        if (slot == null) {
            plugin.messages().send(sender, "invalid-slot");
            return true;
        }
        service.deleteHome(player, slot);
        return true;
    }

    private boolean buyCommand(CommandSender sender, String[] args) {
        return buy(sender, args, 0);
    }

    private boolean buy(CommandSender sender, String[] args, int argumentOffset) {
        if (!sender.hasPermission("homesgui.buy")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        int remaining = args.length - argumentOffset;
        if (remaining > 1) {
            return usage(sender);
        }
        int amount = 1;
        if (remaining == 1) {
            Integer parsed = positiveInteger(args[argumentOffset]);
            if (parsed == null) {
                plugin.messages().send(sender, "invalid-number");
                return true;
            }
            amount = parsed;
        }
        service.purchase(player, amount);
        return true;
    }

    private boolean price(CommandSender sender, String[] args, int argumentOffset) {
        if (!sender.hasPermission("homesgui.price")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        int remaining = args.length - argumentOffset;
        if (remaining > 1) {
            return usage(sender);
        }
        int count = 1;
        if (remaining == 1) {
            Integer parsed = positiveInteger(args[argumentOffset]);
            if (parsed == null) {
                plugin.messages().send(sender, "invalid-number");
                return true;
            }
            count = parsed;
        }
        service.sendPrice(player, count);
        return true;
    }

    private boolean usage(CommandSender sender) {
        plugin.messages().send(sender, "unknown-command");
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        for (String key : List.of("help-header", "help-home", "help-set", "help-delete", "help-buy",
                "help-home-subcommands", "help-admin")) {
            plugin.messages().send(sender, key);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("sethome")) {
            return args.length == 1 ? filter(homeSetSuggestions(player), args[0]) : List.of();
        }
        if (name.equals("delhome")) {
            return args.length == 1 ? filter(ownedSlots(player), args[0]) : List.of();
        }
        if (name.equals("buyhome")) {
            return args.length == 1 ? filter(List.of("1", "2", "3", "5", "10"), args[0]) : List.of();
        }
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(List.of("gui", "g", "set", "s", "delete", "d",
                    "buy", "b", "price", "p", "info", "i", "help", "h"));
            suggestions.addAll(ownedSlots(player));
            return filter(suggestions, args[0]);
        }
        if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if (is(subcommand, "set", "s")) {
                return filter(homeSetSuggestions(player), args[1]);
            }
            if (is(subcommand, "delete", "d")) {
                return filter(ownedSlots(player), args[1]);
            }
            if (is(subcommand, "buy", "b", "price", "p")) {
                return filter(List.of("1", "2", "3", "5", "10"), args[1]);
            }
        }
        return List.of();
    }

    private List<String> homeSetSuggestions(Player player) {
        PlayerHomes data = service.profile(player.getUniqueId());
        List<String> result = new ArrayList<>();
        data.homes().keySet().stream().sorted().map(String::valueOf).forEach(result::add);
        int empty = service.firstEmptySlot(player);
        if (empty > 0 && !result.contains(Integer.toString(empty))) {
            result.add(Integer.toString(empty));
        }
        return result;
    }

    private List<String> ownedSlots(Player player) {
        return service.profile(player.getUniqueId()).homes().keySet().stream()
                .sorted(Comparator.naturalOrder()).map(String::valueOf).toList();
    }

    private static List<String> filter(List<String> source, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return source.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).distinct().toList();
    }

    private static Integer positiveInteger(String value) {
        try {
            int result = Integer.parseInt(value);
            return result > 0 ? result : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean is(String value, String... choices) {
        for (String choice : choices) {
            if (choice.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
