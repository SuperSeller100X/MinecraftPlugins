package dev.superseller.veltryx.teleport;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /tpa}, {@code /tpahere}, {@code /tp} and {@code /tphere}.
 *
 * <ul>
 *   <li>{@code /tpa <player>} — ask to teleport to them (per-counterpart
 *       send cooldown, default 10s)</li>
 *   <li>{@code /tpahere <player>} — ask them to teleport to you (same
 *       cooldown; several different players can be asked at once)</li>
 *   <li>{@code /tp} — accept the request aimed at you (also {@code /tphere}
 *       with no argument)</li>
 *   <li>{@code /tp <player>} / {@code /tphere <player>} — instant staff
 *       teleport to / pull a player ({@code veltryx.tp})</li>
 * </ul>
 */
public final class TpaCommand implements CommandExecutor, TabCompleter {

    private final TpaService service;
    private final Messages messages;
    private final VeltryxConfig config;

    public TpaCommand(TpaService service, Messages messages, VeltryxConfig config) {
        this.service = service;
        this.messages = messages;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.render("shop.players-only"));
            return true;
        }
        String name = command.getName();
        switch (name) {
            case "tpa" -> {
                return sendFlow(player, args, false);
            }
            case "tpahere" -> {
                return sendFlow(player, args, true);
            }
            case "tp" -> {
                if (args.length == 0) {
                    acceptFlow(player);
                    return true;
                }
                return staffFlow(player, args[0], false);
            }
            case "tphere" -> {
                if (args.length == 0) {
                    acceptFlow(player);
                    return true;
                }
                return staffFlow(player, args[0], true);
            }
            default -> {
                return true;
            }
        }
    }

    private boolean sendFlow(Player player, String[] args, boolean here) {
        if (!player.hasPermission("veltryx.tpa")) {
            messages.send(player, "shop.no-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(player, here ? "tpa.usage-here" : "tpa.usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(player, "tpa.not-found", Map.of("player", args[0]));
            return true;
        }
        if (target.equals(player)) {
            messages.send(player, "tpa.self");
            return true;
        }
        long now = System.currentTimeMillis();
        long cooldownMillis = config.tpaCooldownSeconds() * 1000L;
        long left = service.cooldownLeftMillis(player.getUniqueId(), target.getUniqueId(), now, cooldownMillis);
        if (left > 0) {
            messages.send(player, "tpa.cooldown", Map.of(
                    "seconds", String.valueOf((left + 999) / 1000),
                    "player", target.getName()));
            return true;
        }
        service.recordSend(player.getUniqueId(), target.getUniqueId(), now);
        service.send(player.getUniqueId(), target.getUniqueId(), here, now);
        messages.send(player, here ? "tpa.sent-here" : "tpa.sent", Map.of("player", target.getName()));
        messages.send(target, here ? "tpa.received-here" : "tpa.received", Map.of(
                "player", player.getName(),
                "seconds", String.valueOf(config.tpaTimeoutSeconds())));
        return true;
    }

    private void acceptFlow(Player player) {
        if (!player.hasPermission("veltryx.tpa")) {
            messages.send(player, "shop.no-permission");
            return;
        }
        TpaService.Request request = service.takePending(player.getUniqueId());
        if (request == null) {
            messages.send(player, "tpa.none");
            return;
        }
        long now = System.currentTimeMillis();
        if (request.expired(now, config.tpaTimeoutSeconds() * 1000L)) {
            messages.send(player, "tpa.expired");
            return;
        }
        Player requester = Bukkit.getPlayer(request.from());
        if (requester == null) {
            messages.send(player, "tpa.not-found", Map.of("player", "?"));
            return;
        }
        if (request.here()) {
            // /tpahere: the accepter teleports to the requester
            player.teleportAsync(requester.getLocation());
            messages.send(player, "tpa.you-teleported", Map.of("player", requester.getName()));
            messages.send(requester, "tpa.accepted", Map.of("player", player.getName()));
        } else {
            // /tpa: the requester teleports to the accepter
            requester.teleportAsync(player.getLocation());
            messages.send(requester, "tpa.you-teleported", Map.of("player", player.getName()));
            messages.send(player, "tpa.accepted", Map.of("player", requester.getName()));
        }
    }

    private boolean staffFlow(Player player, String arg, boolean pull) {
        if (!player.hasPermission("veltryx.tp")) {
            messages.send(player, "shop.no-permission");
            return true;
        }
        Player target = Bukkit.getPlayerExact(arg);
        if (target == null) {
            messages.send(player, "tpa.not-found", Map.of("player", arg));
            return true;
        }
        if (pull) {
            target.teleportAsync(player.getLocation());
            messages.send(player, "tpa.pulled", Map.of("player", target.getName()));
            messages.send(target, "tpa.pulled-you", Map.of("player", player.getName()));
        } else {
            player.teleportAsync(target.getLocation());
            messages.send(player, "tpa.warped", Map.of("player", target.getName()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
        List<String> out = new java.util.ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                out.add(online.getName());
            }
        }
        return out;
    }
}
