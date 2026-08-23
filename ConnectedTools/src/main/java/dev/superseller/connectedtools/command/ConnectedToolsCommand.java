package dev.superseller.connectedtools.command;

import dev.superseller.connectedtools.ConnectedToolsPlugin;
import dev.superseller.connectedtools.gui.ConnectionGUI;
import dev.superseller.connectedtools.model.Connection;
import dev.superseller.connectedtools.model.ConnectionStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ConnectedToolsCommand implements CommandExecutor {

    private final ConnectionStore store = ConnectedToolsPlugin.getInstance().getStore();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("connectedtools") && !label.equalsIgnoreCase("ct")
                && !label.equalsIgnoreCase("conn") && !label.equalsIgnoreCase("conntools")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "connect":
            case "c":
                return handleConnect(player, args);
            case "disconnect":
            case "d":
                return handleDisconnect(player, args);
            case "list":
            case "l":
                return handleList(player);
            case "info":
            case "i":
                return handleInfo(player);
            case "gui":
                return handleGUI(player);
            case "reload":
                return handleReload(player);
            default:
                sendHelp(player);
                return true;
        }
    }

    private boolean handleConnect(Player player, String[] args) {
        if (!player.hasPermission("connectedtools.connect") && !player.hasPermission("connectedtools.all")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to connect items.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ChatColor.YELLOW + "Hold the item you want to connect in your main hand.");
            return true;
        }

        // Enter binding mode: instruct player to left-click a block
        store.setBindingPlayer(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Binding mode active. Left-click the block you want to connect this item to (within 10 blocks).");
        player.sendMessage(ChatColor.GRAY + "Type /ct c again or /ct disconnect to cancel.");
        return true;
    }

    private boolean handleDisconnect(Player player, String[] args) {
        if (!player.hasPermission("connectedtools.disconnect") && !player.hasPermission("connectedtools.all")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to disconnect items.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ChatColor.YELLOW + "Hold the connected item in your main hand to disconnect it.");
            return true;
        }

        Connection conn = store.getConnection(player.getUniqueId(), item);
        if (conn == null) {
            player.sendMessage(ChatColor.YELLOW + "This item is not connected to any block.");
            return true;
        }

        store.removeConnection(player.getUniqueId(), item);
        player.sendMessage(ChatColor.GREEN + "Disconnected the item from " + conn.getTargetType() + " at "
                + conn.getLocationString() + ".");
        return true;
    }

    private boolean handleList(Player player) {
        if (!player.hasPermission("connectedtools.list") && !player.hasPermission("connectedtools.all")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to list connections.");
            return true;
        }

        List<Connection> connections = store.getConnections(player.getUniqueId());
        if (connections.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no connected items.");
        } else {
            player.sendMessage(ChatColor.GOLD + "=== Connected Items ===");
            for (Connection c : connections) {
                player.sendMessage(ChatColor.AQUA + c.getItemName()
                        + ChatColor.GRAY + " -> "
                        + ChatColor.GREEN + c.getLocationString()
                        + ChatColor.DARK_GRAY + " (" + c.getTargetType() + ")");
            }
        }
        return true;
    }

    private boolean handleInfo(Player player) {
        if (!player.hasPermission("connectedtools.info") && !player.hasPermission("connectedtools.all")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to view info.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ChatColor.YELLOW + "Hold an item to view its connection info.");
            return true;
        }

        Connection conn = store.getConnection(player.getUniqueId(), item);
        if (conn == null) {
            player.sendMessage(ChatColor.YELLOW + "This item is not connected to any block.");
        } else {
            player.sendMessage(ChatColor.GOLD + "=== Connection Info ===");
            player.sendMessage(ChatColor.AQUA + "Item: " + ChatColor.WHITE + conn.getItemName());
            player.sendMessage(ChatColor.AQUA + "Target: " + ChatColor.WHITE + conn.getTargetType());
            player.sendMessage(ChatColor.AQUA + "Location: " + ChatColor.WHITE + conn.getLocationString());
            player.sendMessage(ChatColor.AQUA + "World: " + ChatColor.WHITE + conn.getWorldName());
        }
        return true;
    }

    private boolean handleGUI(Player player) {
        if (!player.hasPermission("connectedtools.gui") && !player.hasPermission("connectedtools.all")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to open the GUI.");
            return true;
        }

        new ConnectionGUI(player, store).open();
        return true;
    }

    private boolean handleReload(Player player) {
        if (!player.hasPermission("connectedtools.reload") && !player.hasPermission("connectedtools.all")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to reload.");
            return true;
        }

        ConnectedToolsPlugin.getInstance().reloadConfig();
        player.sendMessage(ChatColor.GREEN + "ConnectedTools configuration reloaded.");
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== ConnectedTools Help ===");
        player.sendMessage(ChatColor.YELLOW + "/ct connect (c)" + ChatColor.GRAY + " - Bind held item to a block by clicking it.");
        player.sendMessage(ChatColor.YELLOW + "/ct disconnect (d)" + ChatColor.GRAY + " - Unbind held item.");
        player.sendMessage(ChatColor.YELLOW + "/ct list (l)" + ChatColor.GRAY + " - List all connected items.");
        player.sendMessage(ChatColor.YELLOW + "/ct info (i)" + ChatColor.GRAY + " - Show info for held item.");
        player.sendMessage(ChatColor.YELLOW + "/ct gui" + ChatColor.GRAY + " - Open connection management GUI.");
        player.sendMessage(ChatColor.YELLOW + "/ct reload" + ChatColor.GRAY + " - Reload plugin config (admin).");
    }

    public ConnectionStore getStore() {
        return store;
    }
}
