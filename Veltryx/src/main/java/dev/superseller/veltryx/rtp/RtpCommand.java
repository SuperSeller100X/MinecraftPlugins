package dev.superseller.veltryx.rtp;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import dev.superseller.veltryx.config.Messages;

/**
 * {@code /rtp} — random teleport. Opens the world picker GUI, or accepts
 * {@code /rtp overworld|nether|end} to go directly.
 */
public final class RtpCommand implements CommandExecutor, TabCompleter {

    private final RtpGui gui;
    private final RtpService service;
    private final Messages messages;

    public RtpCommand(RtpGui gui, RtpService service, Messages messages) {
        this.gui = gui;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("shop.players-only"));
            return true;
        }
        if (!player.hasPermission("veltryx.rtp")) {
            messages.send(player, "shop.no-permission", java.util.Map.of());
            return true;
        }
        if (args.length == 0) {
            gui.open(player);
            return true;
        }
        String kind = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (kind) {
            case "overworld", "world", "ow" -> service.start(player, "overworld");
            case "nether", "hell" -> service.start(player, "nether");
            case "end" -> service.start(player, "end");
            default -> gui.open(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[] {"overworld", "nether", "end"}) {
                if (s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
