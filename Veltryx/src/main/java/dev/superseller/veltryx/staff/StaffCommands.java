package dev.superseller.veltryx.staff;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * The staff utility toolbox: {@code /ping}, {@code /tps}, {@code /broadcast},
 * {@code /clearchat}, {@code /slowchat}, {@code /vanish} and {@code /freeze}.
 */
public final class StaffCommands implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final VeltryxConfig config;
    private final VanishService vanish;
    private final FreezeService freeze;
    private final ChatListener chatListener;

    public StaffCommands(Messages messages, VeltryxConfig config, VanishService vanish,
                         FreezeService freeze, ChatListener chatListener) {
        this.messages = messages;
        this.config = config;
        this.vanish = vanish;
        this.freeze = freeze;
        this.chatListener = chatListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "ping" -> ping(sender, args);
            case "tps" -> tps(sender);
            case "broadcast" -> broadcast(sender, label, args);
            case "clearchat" -> clearchat(sender);
            case "slowchat" -> slowchat(sender, label, args);
            case "vanish" -> vanish(sender);
            case "freeze" -> freeze(sender, args);
            default -> messages.send(sender, "general.unknown-subcommand",
                    Map.of("input", command.getName(), "command", "/" + label));
        }
        return true;
    }

    private void ping(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            if (!sender.hasPermission("veltryx.ping.others")) {
                messages.send(sender, "general.no-permission");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
                return;
            }
            messages.send(sender, "ping.other", Map.of(
                    "player", target.getName(), "ping", String.valueOf(target.getPing())));
            return;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only");
            return;
        }
        if (!sender.hasPermission("veltryx.ping")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        messages.send(player, "ping.self", Map.of("ping", String.valueOf(player.getPing())));
    }

    private void tps(CommandSender sender) {
        if (!sender.hasPermission("veltryx.tps")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        double[] tps = Bukkit.getTPS();
        double one = tps == null || tps.length == 0 ? 20.0 : tps[0];
        double five = tps == null || tps.length < 2 ? one : tps[1];
        double fifteen = tps == null || tps.length < 3 ? five : tps[2];
        messages.send(sender, "tps.info", Map.of(
                "tps1", String.format(Locale.ROOT, "%.2f", one),
                "tps5", String.format(Locale.ROOT, "%.2f", five),
                "tps15", String.format(Locale.ROOT, "%.2f", fifteen)));
    }

    private void broadcast(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("veltryx.admin.broadcast")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length == 0) {
            messages.send(sender, "broadcast.usage", Map.of("command", "/" + label));
            return;
        }
        String message = String.join(" ", args);
        Component announcement = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(config.broadcastPrefix())
                .append(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize(message));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(announcement);
        }
        Bukkit.getConsoleSender().sendMessage(announcement);
    }

    private void clearchat(CommandSender sender) {
        if (!sender.hasPermission("veltryx.admin.chat")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        Component blank = Component.text(" ");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("veltryx.admin.chat")) {
                continue;
            }
            for (int i = 0; i < 20; i++) {
                player.sendMessage(blank);
            }
            messages.send(player, "clearchat.cleared", Map.of("staff", sender.getName()));
        }
        messages.send(sender, "clearchat.done");
    }

    private void slowchat(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("veltryx.admin.chat")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length == 0) {
            long current = chatListener.slowchatSeconds();
            messages.send(sender, current > 0
                    ? "slowchat.status-on"
                    : "slowchat.status-off",
                    Map.of("seconds", String.valueOf(current)));
            return;
        }
        if (args[0].equalsIgnoreCase("off")) {
            chatListener.setSlowchatSeconds(0);
            messages.send(sender, "slowchat.disabled");
            return;
        }
        long seconds;
        try {
            seconds = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            messages.send(sender, "slowchat.usage", Map.of("command", "/" + label));
            return;
        }
        chatListener.setSlowchatSeconds(Math.max(1L, seconds));
        messages.send(sender, "slowchat.enabled",
                Map.of("seconds", String.valueOf(Math.max(1L, seconds))));
    }

    private void vanish(CommandSender sender) {
        if (!sender.hasPermission("veltryx.staff.vanish")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only");
            return;
        }
        boolean now = vanish.toggle(player);
        messages.send(player, now ? "vanish.enabled" : "vanish.disabled");
    }

    private void freeze(CommandSender sender, String[] args) {
        if (!sender.hasPermission("veltryx.moderation.freeze")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 1) {
            messages.send(sender, "freeze.usage", Map.of("command", "/freeze"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
            return;
        }
        boolean now = freeze.toggle(target);
        messages.send(sender, now ? "freeze.frozen" : "freeze.unfrozen",
                Map.of("player", target.getName()));
        if (now) {
            messages.send(target, "freeze.notify", Map.of("staff", sender.getName()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1 && (name.equals("ping") || name.equals("freeze"))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            String lower = args[0].toLowerCase(Locale.ROOT);
            names.removeIf(n -> !n.toLowerCase(Locale.ROOT).startsWith(lower));
            return names;
        }
        if (args.length == 1 && name.equals("slowchat")) {
            List<String> options = new ArrayList<>(List.of("5", "10", "30", "off"));
            String lower = args[0].toLowerCase(Locale.ROOT);
            options.removeIf(option -> !option.startsWith(lower));
            return options;
        }
        return List.of();
    }
}
