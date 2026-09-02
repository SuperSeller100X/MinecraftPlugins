package dev.superseller.chestlock.command;

import dev.superseller.chestlock.ChestLockPlugin;
import dev.superseller.chestlock.gui.DialogController;
import dev.superseller.chestlock.message.Feedback;
import dev.superseller.chestlock.security.SessionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /chestlock and /cl command router. Every subcommand has a short form. */
public final class ChestLockCommand implements CommandExecutor, TabCompleter {
    private final ChestLockPlugin plugin;
    private final DialogController dialogs;
    private final SessionManager sessions;
    private final Feedback feedback;

    public ChestLockCommand(
            ChestLockPlugin plugin,
            DialogController dialogs,
            SessionManager sessions,
            Feedback feedback
    ) {
        this.plugin = plugin;
        this.dialogs = dialogs;
        this.sessions = sessions;
        this.feedback = feedback;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("ChestLock player workflows require an in-game player.");
            return true;
        }
        if (args.length == 0) {
            dialogs.openMain(player);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "lock", "l" -> dialogs.beginLock(player);
            case "unlock", "u" -> dialogs.beginUnlock(player);
            case "info", "i" -> dialogs.showInfo(player);
            case "change", "c" -> dialogs.beginChange(player);
            case "remove", "r" -> dialogs.beginRemove(player);
            case "key", "k" -> dialogs.beginKey(player);
            case "access", "a" -> dialogs.beginAccess(player);
            case "settings", "s" -> dialogs.openSettings(player);
            case "help", "h", "?" -> dialogs.showHelp(player);
            case "bypass", "bp" -> toggleBypass(player);
            case "forceremove", "force-remove", "fr" -> dialogs.beginForceRemove(player);
            case "reload", "rl" -> reload(player);
            default -> dialogs.showHelp(player);
        }
        return true;
    }

    private void toggleBypass(Player player) {
        if (!player.hasPermission("chestlock.admin.bypass")) {
            feedback.failure(player, "permission-denied", java.util.Map.of());
            return;
        }
        boolean enabled = sessions.toggleBypass(player.getUniqueId());
        feedback.message(player, enabled ? "bypass-enabled" : "bypass-disabled");
        plugin.audit(player.getName() + (enabled ? " enabled" : " disabled") + " administrative bypass");
    }

    private void reload(Player player) {
        if (!player.hasPermission("chestlock.admin.reload")) {
            feedback.failure(player, "permission-denied", java.util.Map.of());
            return;
        }
        plugin.reloadChestLock();
        feedback.success(player, "reloaded", java.util.Map.of());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        add(sender, values, "chestlock.lock", "lock", "l");
        add(sender, values, "chestlock.unlock", "unlock", "u");
        add(sender, values, "chestlock.info", "info", "i");
        add(sender, values, "chestlock.manage", "change", "c", "remove", "r");
        add(sender, values, "chestlock.key", "key", "k");
        add(sender, values, "chestlock.manage", "access", "a");
        add(sender, values, "chestlock.settings", "settings", "s");
        add(sender, values, "chestlock.use", "help", "h");
        add(sender, values, "chestlock.admin.bypass", "bypass", "bp");
        add(sender, values, "chestlock.admin.forceremove", "forceremove", "fr");
        add(sender, values, "chestlock.admin.reload", "reload", "rl");
        return values.stream().filter(value -> value.startsWith(prefix)).sorted().toList();
    }

    private static void add(CommandSender sender, List<String> values, String permission, String... commands) {
        if (sender.hasPermission(permission)) {
            values.addAll(List.of(commands));
        }
    }
}
