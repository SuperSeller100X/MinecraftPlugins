package dev.superseller.connectedtools.command;

import dev.superseller.connectedtools.config.PluginSettings;
import dev.superseller.connectedtools.gui.GuiManager;
import dev.superseller.connectedtools.model.ConnectionStore;
import dev.superseller.connectedtools.service.ConnectionService;
import dev.superseller.connectedtools.util.Colors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ConnectedToolsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "connect", "disconnect", "list", "info", "gui", "reload",
            "c", "d", "l", "i"
    );

    private final PluginSettings settings;
    private final ConnectionStore store;
    private final ConnectionService service;

    public ConnectedToolsCommand(PluginSettings settings, ConnectionStore store, ConnectionService service) {
        this.settings = settings;
        this.store = store;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("connectedtools")
                && !label.equalsIgnoreCase("ct")
                && !label.equalsIgnoreCase("conn")
                && !label.equalsIgnoreCase("conntools")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Colors.parse(settings.msg("permission.denied")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("connect") || sub.equals("c")) {
            if (!service.hasPermission(player, "connectedtools.connect")) {
                player.sendMessage(Colors.parse(settings.msg("permission.denied")));
                return true;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            service.enterBindingMode(player, item);
        } else if (sub.equals("disconnect") || sub.equals("d")) {
            if (!service.hasPermission(player, "connectedtools.disconnect")) {
                player.sendMessage(Colors.parse(settings.msg("permission.denied")));
                return true;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            service.disconnect(player, item);
        } else if (sub.equals("list") || sub.equals("l")) {
            if (!service.hasPermission(player, "connectedtools.list")) {
                player.sendMessage(Colors.parse(settings.msg("permission.denied")));
                return true;
            }
            List<dev.superseller.connectedtools.model.Connection> connections = store.getConnections(player.getUniqueId());
            if (connections.isEmpty()) {
                player.sendMessage(Colors.parse(settings.msg("gui.empty")));
            } else {
                player.sendMessage(Colors.parse("&6=== Connected Items ==="));
                for (dev.superseller.connectedtools.model.Connection c : connections) {
                    player.sendMessage(Colors.parse("&a" + c.getItemName()
                            + " &7-> &f" + c.getLocationString()
                            + " &7(" + c.getTargetType() + ")"));
                }
            }
        } else if (sub.equals("info") || sub.equals("i")) {
            if (!service.hasPermission(player, "connectedtools.info")) {
                player.sendMessage(Colors.parse(settings.msg("permission.denied")));
                return true;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            dev.superseller.connectedtools.model.Connection conn = store.getConnection(player.getUniqueId(), item);
            if (conn == null) {
                player.sendMessage(Colors.parse(settings.msg("disconnect.none")));
            } else {
                player.sendMessage(Colors.parse("&6=== Connection Info ==="));
                player.sendMessage("&aItem: &f" + conn.getItemName());
                player.sendMessage("&aTarget: &f" + conn.getTargetType());
                player.sendMessage("&aLocation: &f" + conn.getLocationString());
                player.sendMessage("&aWorld: &f" + conn.getWorldName());
            }
        } else if (sub.equals("gui")) {
            if (!service.hasPermission(player, "connectedtools.gui")) {
                player.sendMessage(Colors.parse(settings.msg("permission.denied")));
                return true;
            }
            new GuiManager(settings, store).openMain(player);
        } else if (sub.equals("reload")) {
            if (!service.hasPermission(player, "connectedtools.reload")) {
                player.sendMessage(Colors.parse(settings.msg("permission.denied")));
                return true;
            }
            settings.load();
            player.sendMessage(Colors.parse(settings.msg("command.reload-ok")));
        } else {
            sendHelp(player, label);
        }
        return true;
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(Colors.parse(settings.msg("command.help-title")));
        player.sendMessage(Colors.parse(settings.msg("command.connect-desc")));
        player.sendMessage(Colors.parse(settings.msg("command.disconnect-desc")));
        player.sendMessage(Colors.parse(settings.msg("command.list-desc")));
        player.sendMessage(Colors.parse(settings.msg("command.info-desc")));
        player.sendMessage(Colors.parse(settings.msg("command.gui-desc")));
        player.sendMessage(Colors.parse(settings.msg("command.reload-desc")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        if (args.length <= 1) {
            for (String s : SUBS) {
                if (s.toLowerCase(Locale.ROOT).startsWith(current)) {
                    results.add(s);
                }
            }
        }
        return results;
    }
}
