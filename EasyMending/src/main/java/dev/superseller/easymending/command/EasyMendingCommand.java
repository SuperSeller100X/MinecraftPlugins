package dev.superseller.easymending.command;

import dev.superseller.easymending.config.Messages;
import dev.superseller.easymending.config.PluginConfig;
import dev.superseller.easymending.gui.EasyMendingGui;
import dev.superseller.easymending.model.RepairEstimate;
import dev.superseller.easymending.model.RepairResult;
import dev.superseller.easymending.model.RepairScope;
import dev.superseller.easymending.service.RepairService;
import dev.superseller.easymending.util.ExperienceUtil;
import dev.superseller.easymending.util.ItemUtil;
import dev.superseller.easymending.util.SoundUtil;
import java.util.Arrays;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Main command executor for /easymending and its player-facing subcommands.
 */
public final class EasyMendingCommand implements CommandExecutor {

    private final PluginConfig config;
    private final Messages messages;
    private final RepairService repairService;
    private final EasyMendingGui gui;
    private final EasyMendingAdminCommand adminCommand;

    public EasyMendingCommand(PluginConfig config, Messages messages, RepairService repairService, EasyMendingGui gui, EasyMendingAdminCommand adminCommand) {
        this.config = config;
        this.messages = messages;
        this.repairService = repairService;
        this.gui = gui;
        this.adminCommand = adminCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("easymending.use")) {
            messages.send(sender, "no-permission");
            return true;
        }

        // If no arguments provided: open GUI for player, or show help for console
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("easymending.gui") && !player.hasPermission("easymending.use")) {
                    messages.send(player, "no-permission");
                    return true;
                }
                SoundUtil.playGuiOpen(player, config);
                gui.open(player);
            } else {
                sendHelp(sender, label);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Forward to admin command if "admin" is passed as first arg
        if (sub.equals("admin") || sub.equals("adm")) {
            String[] adminArgs = Arrays.copyOfRange(args, 1, args.length);
            return adminCommand.onCommand(sender, command, label + " admin", adminArgs);
        }

        switch (sub) {
            case "gui", "g", "menu" -> handleGui(sender);
            case "hand", "h", "main" -> handleRepair(sender, RepairScope.HAND, "easymending.hand");
            case "offhand", "oh", "off" -> handleRepair(sender, RepairScope.OFFHAND, "easymending.offhand");
            case "armor", "a", "armour" -> handleRepair(sender, RepairScope.ARMOR, "easymending.armor");
            case "hotbar", "hb", "hot" -> handleRepair(sender, RepairScope.HOTBAR, "easymending.hotbar");
            case "all", "*", "inv" -> handleRepair(sender, RepairScope.ALL, "easymending.all");
            case "info", "i" -> handleInfo(sender);
            case "cost", "c" -> handleCost(sender);
            case "help", "?", "hlp" -> sendHelp(sender, label);
            default -> {
                RepairScope scope = RepairScope.fromString(sub);
                if (scope != null) {
                    handleRepair(sender, scope, "easymending." + scope.name().toLowerCase(Locale.ROOT));
                } else {
                    sendHelp(sender, label);
                }
            }
        }
        return true;
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("easymending.gui") && !player.hasPermission("easymending.use")) {
            messages.send(player, "no-permission");
            return;
        }
        SoundUtil.playGuiOpen(player, config);
        gui.open(player);
    }

    private void handleRepair(CommandSender sender, RepairScope scope, String permission) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission(permission) && !player.hasPermission("easymending.use")) {
            messages.send(player, "no-permission");
            return;
        }

        RepairResult result = repairService.repair(player, scope, false, false);
        if (result.success()) {
            if (result.bypassCost()) {
                if (result.itemsRepaired() > 1) {
                    messages.send(player, "repair-free-multiple", "count", String.valueOf(result.itemsRepaired()));
                } else {
                    messages.send(player, "repair-free-single",
                            "item", result.primaryItemName(),
                            "repaired", String.valueOf(result.totalDurabilityRestored()));
                }
            } else {
                if (result.itemsRepaired() > 1) {
                    messages.send(player, "repair-success-multiple",
                            "count", String.valueOf(result.itemsRepaired()),
                            "repaired", String.valueOf(result.totalDurabilityRestored()),
                            "cost", String.valueOf(result.xpSpent()));
                } else {
                    messages.send(player, "repair-success-single",
                            "item", result.primaryItemName(),
                            "repaired", String.valueOf(result.totalDurabilityRestored()),
                            "cost", String.valueOf(result.xpSpent()));
                }
            }

            if (result.partial()) {
                messages.send(player, "repair-partial-notice", "repaired", String.valueOf(result.totalDurabilityRestored()));
            }

            if (result.itemsRepaired() > 1) {
                SoundUtil.playRepairAll(player, config);
            } else {
                SoundUtil.playRepairSuccess(player, config);
            }
        } else {
            handleFailure(player, scope, result.failureReasonKey());
        }
    }

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("easymending.info") && !player.hasPermission("easymending.use")) {
            messages.send(player, "no-permission");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!ItemUtil.isRepairable(item)) {
            messages.send(player, "not-repairable");
            SoundUtil.playNoDamage(player, config);
            return;
        }

        int damage = ItemUtil.getDamage(item);
        int max = ItemUtil.getMaxDurability(item);
        int remaining = max - damage;
        int percent = max > 0 ? (int) Math.round(((double) remaining / max) * 100.0) : 100;
        boolean mending = ItemUtil.hasMending(item);
        boolean bypass = player.hasPermission("easymending.bypass.mending") || repairService.hasAdminBypass(player.getUniqueId());
        int cost = repairService.calculateItemCost(item, bypass);
        int currentXp = ExperienceUtil.getPlayerTotalExperience(player);

        messages.send(player, "info-header");
        messages.send(player, "info-item", "item", ItemUtil.getFriendlyName(item));
        messages.send(player, "info-durability",
                "current", String.valueOf(remaining),
                "max", String.valueOf(max),
                "percent", String.valueOf(percent));
        messages.send(player, "info-damage-needed", "damage", String.valueOf(damage));
        if (mending) {
            messages.send(player, "info-has-mending");
        } else {
            messages.send(player, "info-no-mending");
        }
        messages.send(player, "info-cost",
                "cost", String.valueOf(Math.max(0, cost)),
                "current_xp", String.valueOf(currentXp));
        messages.send(player, "info-footer");
        SoundUtil.playGuiClick(player, config);
    }

    private void handleCost(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("easymending.cost") && !player.hasPermission("easymending.use")) {
            messages.send(player, "no-permission");
            return;
        }

        RepairEstimate est = repairService.estimate(player, RepairScope.ALL);
        if (!est.hasRepairableItems()) {
            messages.send(player, "cost-none-damaged");
            SoundUtil.playNoDamage(player, config);
            return;
        }

        messages.send(player, "cost-header");
        messages.send(player, "cost-summary",
                "count", String.valueOf(est.eligibleItemsCount()),
                "total_cost", String.valueOf(est.totalXpCost()),
                "current_xp", String.valueOf(est.availableXp()));
        SoundUtil.playGuiClick(player, config);
    }

    private void handleFailure(Player player, RepairScope scope, String reasonKey) {
        if (reasonKey == null) reasonKey = "no-damage-target";

        switch (reasonKey) {
            case "cooldown-active" -> {
                long rem = repairService.getRemainingCooldownSeconds(player.getUniqueId());
                messages.send(player, "cooldown-active", "seconds", String.valueOf(rem));
                SoundUtil.playNoDamage(player, config);
            }
            case "insufficient-xp" -> {
                RepairEstimate est = repairService.estimate(player, scope);
                int currentXp = ExperienceUtil.getPlayerTotalExperience(player);
                messages.send(player, "insufficient-xp",
                        "cost", String.valueOf(est.totalXpCost()),
                        "current_xp", String.valueOf(currentXp));
                SoundUtil.playInsufficientXp(player, config);
            }
            case "not-repairable" -> {
                messages.send(player, "not-repairable");
                SoundUtil.playNoDamage(player, config);
            }
            case "no-mending" -> {
                messages.send(player, "no-mending");
                SoundUtil.playNoMending(player, config);
            }
            case "no-damage-held" -> {
                messages.send(player, "no-damage-held");
                SoundUtil.playNoDamage(player, config);
            }
            default -> {
                messages.send(player, "no-damage-target");
                SoundUtil.playNoDamage(player, config);
            }
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        messages.send(sender, "help-header");
        messages.send(sender, "help-line", "cmd", label, "desc", "Open the interactive repair GUI");
        messages.send(sender, "help-line", "cmd", label + " hand (h)", "desc", "Repair the held item with XP");
        messages.send(sender, "help-line", "cmd", label + " offhand (oh)", "desc", "Repair offhand item with XP");
        messages.send(sender, "help-line", "cmd", label + " armor (a)", "desc", "Repair equipped armor with XP");
        messages.send(sender, "help-line", "cmd", label + " hotbar (hb)", "desc", "Repair hotbar items with XP");
        messages.send(sender, "help-line", "cmd", label + " all (*)", "desc", "Repair all inventory items with XP");
        messages.send(sender, "help-line", "cmd", label + " info (i)", "desc", "Check held item repair cost & durability");
        messages.send(sender, "help-line", "cmd", label + " cost (c)", "desc", "Estimate total inventory repair cost");
        if (sender.hasPermission("easymending.admin")) {
            messages.send(sender, "help-line", "cmd", label + " admin (ema)", "desc", "Access administrator commands");
        }
        messages.send(sender, "help-footer");
    }
}
