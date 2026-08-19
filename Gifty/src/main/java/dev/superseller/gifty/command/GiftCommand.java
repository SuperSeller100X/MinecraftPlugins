package dev.superseller.gifty.command;

import dev.superseller.gifty.GuiSessionManager;
import dev.superseller.gifty.config.GiftyConfig;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /gift <player> — opens the compose GUI to send a gift.
 */
public final class GiftCommand implements CommandExecutor, TabCompleter {

    private final GiftyConfig config;
    private final GuiSessionManager sessions;

    public GiftCommand(GiftyConfig config, GuiSessionManager sessions) {
        this.config = config;
        this.sessions = sessions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.format("errors.players-only"));
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("gifty.use") || !player.hasPermission("gifty.send")) {
            player.sendMessage(config.format("errors.no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(config.format("send.usage"));
            return true;
        }
        String name = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        if (target == null || target.getName() == null || !target.hasPlayedBefore()) {
            player.sendMessage(config.format("send.not-found", "player", name));
            return true;
        }
        sessions.openCompose(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
