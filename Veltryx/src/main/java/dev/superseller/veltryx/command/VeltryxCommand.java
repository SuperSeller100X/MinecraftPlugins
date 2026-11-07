package dev.superseller.veltryx.command;

import dev.superseller.veltryx.VeltryxPlugin;
import dev.superseller.veltryx.config.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * {@code /veltryx} (alias {@code /vx}) — plugin administration:
 * reload, version and info.
 */
public final class VeltryxCommand implements CommandExecutor, TabCompleter {

    private final VeltryxPlugin plugin;
    private final Messages messages;

    public VeltryxCommand(VeltryxPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("veltryx.admin.reload")) {
                    messages.send(sender, "general.no-permission");
                    return true;
                }
                plugin.reloadEverything();
                messages.send(sender, "veltryx.reloaded");
            }
            case "version" -> messages.send(sender, "veltryx.version",
                    Map.of("version", plugin.getPluginMeta().getVersion()));
            case "info" -> {
                messages.send(sender, "veltryx.info-header",
                        Map.of("version", plugin.getPluginMeta().getVersion()));
                messages.send(sender, "veltryx.info-body", Map.of(
                        "storage", plugin.storageTypeName(),
                        "platform", dev.superseller.veltryx.util.PlatformScheduler.isFolia()
                                ? "Folia" : "Paper/Purpur",
                        "players", String.valueOf(plugin.getServer().getOnlinePlayers().size())));
            }
            default -> messages.send(sender, "general.unknown-subcommand",
                    Map.of("input", args[0], "command", "/" + label));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("info", "version", "reload"));
        String lower = args[0].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(lower));
        return options;
    }
}
