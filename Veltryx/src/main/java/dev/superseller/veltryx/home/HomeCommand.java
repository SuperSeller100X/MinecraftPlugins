package dev.superseller.veltryx.home;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.model.HomeLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /home}, {@code /sethome} and {@code /homes}: teleport to homes, set
 * them and open the homes overview GUI.
 */
public final class HomeCommand implements CommandExecutor, TabCompleter {

    private final HomeService service;
    private final HomesGui gui;
    private final Messages messages;
    private final VeltryxConfig config;

    public HomeCommand(HomeService service, HomesGui gui, Messages messages, VeltryxConfig config) {
        this.service = service;
        this.gui = gui;
        this.messages = messages;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("shop.players-only"));
            return true;
        }
        if (!sender.hasPermission("veltryx.home")) {
            sender.sendMessage(messages.render("shop.no-permission"));
            return true;
        }
        String name = command.getName();
        if (name.equals("homes")) {
            gui.open(player);
            return true;
        }
        if (name.equals("sethome")) {
            if (args.length == 0) {
                sender.sendMessage(messages.render("home.usage-set"));
                return true;
            }
            finish(player, service.setHome(player, args[0]));
            return true;
        }
        // /home [name]
        List<HomeLocation> homes = service.list(player.getUniqueId());
        if (args.length == 0) {
            if (homes.isEmpty()) {
                sender.sendMessage(messages.render("home.no-homes"));
                return true;
            }
            if (homes.size() == 1) {
                finish(player, service.teleport(player, homes.get(0).name()));
            } else {
                gui.open(player);
            }
            return true;
        }
        finish(player, service.teleport(player, args[0]));
        return true;
    }

    /** Sends the template describing the home action (success or failure). */
    private void finish(Player player, HomeService.Result result) {
        messages.send(player, result.templateKey(), result.values());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)
                || !sender.hasPermission("veltryx.home")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (command.getName().equals("home")) {
            for (HomeLocation home : service.list(player.getUniqueId())) {
                if (home.name().startsWith(prefix)) {
                    out.add(home.name());
                }
            }
        } else if (command.getName().equals("sethome")) {
            if (prefix.isEmpty() || "home".startsWith(prefix)) {
                out.add("home");
            }
        }
        return out;
    }
}
