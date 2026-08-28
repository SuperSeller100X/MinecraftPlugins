package dev.superseller.easymending.command;

import dev.superseller.easymending.config.Messages;
import dev.superseller.easymending.config.PluginConfig;
import dev.superseller.easymending.model.RepairEstimate;
import dev.superseller.easymending.model.RepairResult;
import dev.superseller.easymending.model.RepairScope;
import dev.superseller.easymending.service.RepairService;
import dev.superseller.easymending.util.ExperienceUtil;
import dev.superseller.easymending.util.SoundUtil;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles all administrator commands for EasyMending.
 */
public final class EasyMendingAdminCommand implements CommandExecutor {

    private final PluginConfig config;
    private final Messages messages;
    private final RepairService repairService;

    public EasyMendingAdminCommand(PluginConfig config, Messages messages, RepairService repairService) {
        this.config = config;
        this.messages = messages;
        this.repairService = repairService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("easymending.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload", "rl" -> handleReload(sender);
            case "repair", "r" -> handleRepair(sender, args);
            case "inspect", "i" -> handleInspect(sender, args);
            case "setratio", "sr" -> handleSetRatio(sender, args);
            case "bypass", "bp" -> handleBypass(sender, args);
            case "stats", "s" -> handleStats(sender);
            case "help", "?" -> sendAdminHelp(sender, label);
            default -> sendAdminHelp(sender, label);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("easymending.admin.reload") && !sender.hasPermission("easymending.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        config.load();
        messages.load();
        messages.send(sender, "admin-reload-success");
    }

    private void handleRepair(CommandSender sender, String[] args) {
        if (!sender.hasPermission("easymending.admin.repair") && !sender.hasPermission("easymending.admin")) {
            messages.send(sender, "no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ema repair <player> [hand|offhand|armor|hotbar|all] [--free/-f]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            messages.send(sender, "player-not-found", "player", args[1]);
            return;
        }

        RepairScope scope = RepairScope.ALL;
        boolean forceFree = false;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            if (arg.equals("--free") || arg.equals("-f") || arg.equals("free")) {
                forceFree = true;
            } else {
                RepairScope parsed = RepairScope.fromString(arg);
                if (parsed != null) {
                    scope = parsed;
                }
            }
        }

        RepairResult result = repairService.repair(target, scope, forceFree, true);
        if (result.success()) {
            messages.send(sender, "admin-repair-success",
                    "player", target.getName(),
                    "count", String.valueOf(result.itemsRepaired()));
            messages.send(target, "admin-repair-target-notify");
            SoundUtil.playRepairAll(target, config);
        } else {
            sender.sendMessage("§cRepair failed for " + target.getName() + ": " + result.failureReasonKey());
        }
    }

    private void handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission("easymending.admin.inspect") && !sender.hasPermission("easymending.admin")) {
            messages.send(sender, "no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ema inspect <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            messages.send(sender, "player-not-found", "player", args[1]);
            return;
        }

        RepairEstimate est = repairService.estimate(target, RepairScope.ALL);
        int totalXp = ExperienceUtil.getPlayerTotalExperience(target);

        messages.send(sender, "admin-inspect-header", "player", target.getName());
        messages.send(sender, "admin-inspect-level",
                "level", String.valueOf(target.getLevel()),
                "total_xp", String.valueOf(totalXp));
        messages.send(sender, "admin-inspect-damaged-count",
                "count", String.valueOf(est.eligibleItemsCount()),
                "damage", String.valueOf(est.totalMissingDurability()));
        messages.send(sender, "admin-inspect-repair-cost",
                "cost", String.valueOf(est.totalXpCost()));
    }

    private void handleSetRatio(CommandSender sender, String[] args) {
        if (!sender.hasPermission("easymending.admin.setratio") && !sender.hasPermission("easymending.admin")) {
            messages.send(sender, "no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ema setratio <durabilityPerXp>");
            return;
        }

        try {
            double ratio = Double.parseDouble(args[1]);
            if (ratio <= 0 || Double.isNaN(ratio) || Double.isInfinite(ratio)) {
                messages.send(sender, "admin-setratio-invalid");
                return;
            }
            double old = config.getDurabilityPerXp();
            config.setDurabilityPerXp(ratio);
            messages.send(sender, "admin-setratio-success", "old", String.valueOf(old), "new", String.valueOf(ratio));
        } catch (NumberFormatException e) {
            messages.send(sender, "admin-setratio-invalid");
        }
    }

    private void handleBypass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("easymending.admin.bypass") && !sender.hasPermission("easymending.admin")) {
            messages.send(sender, "no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ema bypass <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            messages.send(sender, "player-not-found", "player", args[1]);
            return;
        }

        boolean enabled = repairService.toggleAdminBypass(target.getUniqueId());
        if (enabled) {
            messages.send(sender, "admin-bypass-enabled", "player", target.getName());
        } else {
            messages.send(sender, "admin-bypass-disabled", "player", target.getName());
        }
    }

    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("easymending.admin.stats") && !sender.hasPermission("easymending.admin")) {
            messages.send(sender, "no-permission");
            return;
        }
        messages.send(sender, "admin-stats",
                "total_repairs", String.valueOf(repairService.getTotalRepairs()),
                "total_xp", String.valueOf(repairService.getTotalXpSpent()));
    }

    private void sendAdminHelp(CommandSender sender, String label) {
        messages.send(sender, "help-header");
        messages.send(sender, "help-line", "cmd", label + " reload (rl)", "desc", "Reload configuration and language");
        messages.send(sender, "help-line", "cmd", label + " repair <player> [scope] [-f]", "desc", "Force repair player equipment");
        messages.send(sender, "help-line", "cmd", label + " inspect <player> (i)", "desc", "Inspect player's mending items & XP");
        messages.send(sender, "help-line", "cmd", label + " setratio <value> (sr)", "desc", "Set durability restored per 1 XP");
        messages.send(sender, "help-line", "cmd", label + " bypass <player> (bp)", "desc", "Toggle free repair bypass for player");
        messages.send(sender, "help-line", "cmd", label + " stats (s)", "desc", "View global repair statistics");
        messages.send(sender, "help-footer");
    }
}
