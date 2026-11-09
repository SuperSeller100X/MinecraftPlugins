package dev.superseller.veltryx.sus;

import dev.superseller.veltryx.config.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /sus [player]} — opens the suspicion GUI, or jumps straight to the
 * detail view of one player.
 */
public final class SusCommand implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final SusGui gui;

    public SusCommand(Messages messages, SusGui gui) {
        this.messages = messages;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veltryx.sus")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (!(sender instanceof Player staff)) {
            messages.send(sender, "general.players-only");
            return true;
        }
        if (args.length >= 1) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
                return true;
            }
            gui.openDetail(staff, target.getUniqueId());
            return true;
        }
        gui.openList(staff);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        String lower = args[0].toLowerCase(Locale.ROOT);
        names.removeIf(name -> !name.toLowerCase(Locale.ROOT).startsWith(lower));
        return names;
    }
}
