package dev.superseller.gifty.command;

import dev.superseller.gifty.GuiSessionManager;
import dev.superseller.gifty.config.GiftyConfig;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /inbox [page] — opens the player's own delivery inbox.
 */
public final class InboxCommand implements CommandExecutor, TabCompleter {

    private final GiftyConfig config;
    private final GuiSessionManager sessions;

    public InboxCommand(GiftyConfig config, GuiSessionManager sessions) {
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
        if (!player.hasPermission("gifty.use") || !player.hasPermission("gifty.inbox")) {
            player.sendMessage(config.format("errors.no-permission"));
            return true;
        }
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        sessions.openInbox(player, page, false);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (int i = 1; i <= config.inboxPages(); i++) {
                if (String.valueOf(i).startsWith(args[0])) {
                    out.add(String.valueOf(i));
                }
            }
        }
        return out;
    }
}
