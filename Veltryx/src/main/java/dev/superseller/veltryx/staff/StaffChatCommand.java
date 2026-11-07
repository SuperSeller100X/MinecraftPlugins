package dev.superseller.veltryx.staff;

import dev.superseller.veltryx.config.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /staffchat <message>} (alias {@code /sc}) — private staff chat.
 * {@code /staffchat toggle} opts out of receiving it (e.g. while recording).
 */
public final class StaffChatCommand implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();

    public StaffChatCommand(Messages messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veltryx.staff.chat")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("toggle")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.players-only");
                return true;
            }
            if (muted.remove(player.getUniqueId())) {
                messages.send(player, "staffchat.enabled");
            } else {
                muted.add(player.getUniqueId());
                messages.send(player, "staffchat.disabled");
            }
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "staffchat.usage", Map.of("command", "/" + label));
            return true;
        }
        String message = String.join(" ", args);
        Component chat = messages.prefixed("staffchat.format", Map.of(
                "staff", sender.getName(),
                "message", message));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("veltryx.staff.chat") && !muted.contains(staff.getUniqueId())) {
                staff.sendMessage(chat);
            }
        }
        Bukkit.getConsoleSender().sendMessage(chat);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("toggle"));
            String lower = args[0].toLowerCase(Locale.ROOT);
            options.removeIf(option -> !option.startsWith(lower));
            return options;
        }
        return List.of();
    }
}
