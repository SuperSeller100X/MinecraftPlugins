package dev.superseller.subscriptions.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.superseller.subscriptions.SubscriptionsPlugin;
import dev.superseller.subscriptions.config.PluginSettings;
import dev.superseller.subscriptions.gui.GuiManager;
import dev.superseller.subscriptions.hook.DiscordHook;
import dev.superseller.subscriptions.model.Plan;
import dev.superseller.subscriptions.model.Subscription;
import dev.superseller.subscriptions.service.BillingService;
import dev.superseller.subscriptions.storage.Database;
import dev.superseller.subscriptions.util.Colors;
import dev.superseller.subscriptions.util.Text;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class SubAdminCommand implements CommandExecutor, TabCompleter {

    private final SubscriptionsPlugin plugin;
    private final PluginSettings settings;
    private final Database database;
    private final BillingService billing;
    private final DiscordHook discord;
    private final GuiManager gui;

    public SubAdminCommand(SubscriptionsPlugin plugin, PluginSettings settings, Database database,
                           BillingService billing, DiscordHook discord, GuiManager gui) {
        this.plugin = plugin;
        this.settings = settings;
        this.database = database;
        this.billing = billing;
        this.discord = discord;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("subscriptions.admin")) {
            sender.sendMessage(Colors.parse(settings.prefix() + settings.msg("generic.no-permission")));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Colors.parse("&e/subadmin reload|give|cancel|info|stats|forcebill|webhook|inbox"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(Colors.parse(settings.prefix() + settings.msg("generic.reload")));
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(Colors.parse("&cUsage: /subadmin give <player> <plan>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                Plan plan = database.plan(args[2]);
                if (target == null) {
                    sender.sendMessage(Colors.parse("&cThat player is offline."));
                    return true;
                }
                String error = billing.subscribe(target, plan, true);
                if (error != null) {
                    sender.sendMessage(Colors.parse(settings.prefix() + error));
                } else {
                    sender.sendMessage(Colors.parse(settings.prefix() + Text.apply(settings.msg("admin.gave"),
                            Map.of("player", target.getName(), "name", plan == null ? args[2] : plan.name()))));
                }
            }
            case "cancel" -> {
                if (args.length < 2) {
                    sender.sendMessage(Colors.parse("&cUsage: /subadmin cancel <id>"));
                    return true;
                }
                Subscription subscription = database.subscription(args[1]);
                String error = billing.cancel(subscription, "Cancelled by admin", true);
                sender.sendMessage(Colors.parse(settings.prefix()
                        + (error == null ? Text.apply(settings.msg("admin.cancelled"), Map.of("id", args[1])) : error)));
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(Colors.parse("&cUsage: /subadmin info <plan|sub id>"));
                    return true;
                }
                Plan plan = database.plan(args[1]);
                Subscription subscription = database.subscription(args[1]);
                if (plan != null) {
                    sender.sendMessage(Colors.parse("&ePlan &f" + plan.id() + " &7" + plan.name()
                            + " &8• &7" + plan.status() + " &8• &e" + plan.subscriberCount() + " subs"
                            + " &8• &7stock " + (plan.infiniteStock() ? "∞" : plan.stockKits())));
                } else if (subscription != null) {
                    sender.sendMessage(Colors.parse("&eSub &f" + subscription.id()
                            + " &7" + subscription.subscriberName()
                            + " &8• &7" + subscription.status()
                            + " &8• &7plan " + subscription.planId()
                            + " &8• &7cycles " + subscription.cycles()));
                } else {
                    sender.sendMessage(Colors.parse(settings.prefix() + settings.msg("generic.unknown-plan")));
                }
            }
            case "stats" -> sender.sendMessage(Colors.parse(settings.prefix() + Text.apply(settings.msg("admin.stats"), Map.of(
                    "plans", String.valueOf(database.planCount()),
                    "subs", String.valueOf(database.liveSubscriptionCount()),
                    "inbox", String.valueOf(database.totalInbox()),
                    "economy", plugin.economy().isEnabled() ? plugin.economy().providerName() : "disabled"))));
            case "forcebill" -> {
                billing.tryResumePaused();
                billing.tick();
                sender.sendMessage(Colors.parse(settings.prefix() + settings.msg("admin.forcebill")));
            }
            case "webhook" -> {
                if (discord.test()) {
                    sender.sendMessage(Colors.parse(settings.prefix() + settings.msg("admin.webhook-ok")));
                } else {
                    sender.sendMessage(Colors.parse(settings.prefix() + settings.msg("admin.webhook-missing")));
                }
            }
            case "inbox" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    sender.sendMessage(Colors.parse("&cUsage: /subadmin inbox <player>"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                sender.sendMessage(Colors.parse("&eInbox size for &f" + args[1] + "&e: &f"
                        + database.inboxSize(target.getUniqueId())));
                gui.openInbox(player, 0);
            }
            default -> sender.sendMessage(Colors.parse(settings.prefix() + settings.msg("generic.unknown-command")));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        if (args.length <= 1) {
            values.addAll(List.of("reload", "give", "cancel", "info", "stats", "forcebill", "webhook", "inbox"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("inbox"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                values.add(player.getName());
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("cancel") || args[0].equalsIgnoreCase("info"))) {
            for (Plan plan : database.plans()) {
                values.add(plan.id());
            }
            for (Subscription subscription : database.subscriptions()) {
                values.add(subscription.id());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Plan plan : database.plans()) {
                values.add(plan.id());
            }
        }
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
