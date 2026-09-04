package dev.superseller.voidtotem.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.superseller.voidtotem.VoidTotemPlugin;
import net.kyori.adventure.text.MiniMessage;

/**
 * /vt give <player> [amount]  - hand out Void Totems (also called by
 *                                ShardTools when the shop item is bought)
 * /vt info                     - what the totem does
 * /vt reload                   - reload config.yml
 */
public final class VoidTotemCommand implements org.bukkit.command.CommandExecutor, TabCompleter {

    private final VoidTotemPlugin plugin;

    public VoidTotemCommand(VoidTotemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, plugin.config().message("usage"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give":
                return give(sender, args);
            case "info":
                return info(sender);
            case "reload":
                return reload(sender);
            default:
                send(sender, plugin.config().message("usage"));
                return true;
        }
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("voidtotem.give")) {
            send(sender, "<red>You do not have permission to do that.</red>");
            return true;
        }
        if (args.length < 2) {
            send(sender, "<red>Usage: /vt give <player> [amount]</red>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "<red>Player not found: " + args[1] + "</red>");
            return true;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        amount = Math.max(1, amount);

        // Totems stack to 1, so hand them out one at a time (dropping overflow).
        int given = 0;
        for (int i = 0; i < amount; i++) {
            var leftover = target.getInventory().addItem(plugin.item().create(1));
            if (!leftover.isEmpty()) {
                for (ItemStack rest : leftover.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), rest);
                }
                break; // inventory full - stop handing out
            }
            given++;
        }

        send(target, plugin.config().message("given"));
        if (sender != target) {
            send(sender, "<green>Gave " + given + " Void Totem(s) to " + target.getName() + ".</green>");
        }
        return true;
    }

    private boolean info(CommandSender sender) {
        send(sender, "<gradient:#7a00ff:#00e5ff>Void Totem</gradient>");
        send(sender, "<gray>Falls into the void carrying a Void Totem and it consumes itself to pull you back to safety.</gray>");
        send(sender, "<gray>Sold for " + plugin.config().shopPrice() + " shards in /st shop (ShardTools).</gray>");
        send(sender, "<gray>Consume from: " + plugin.config().consumeFrom()
                + "   |   Rescue: " + plugin.config().rescueMode() + "</gray>");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("voidtotem.reload")) {
            send(sender, "<red>You do not have permission to do that.</red>");
            return true;
        }
        plugin.reloadConfiguration();
        send(sender, plugin.config().message("reloaded"));
        return true;
    }

    private void send(CommandSender sender, String mini) {
        if (mini == null || mini.isEmpty()) {
            return;
        }
        sender.sendMessage(MiniMessage.miniMessage().deserialize(mini));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : new String[]{"give", "info", "reload"}) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("voidtotem.give")) {
            String prefix = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(prefix)) {
                    out.add(player.getName());
                }
            }
            return out;
        }
        return out;
    }
}
