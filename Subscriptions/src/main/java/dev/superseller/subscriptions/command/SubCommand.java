package dev.superseller.subscriptions.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.superseller.subscriptions.config.PluginSettings;
import dev.superseller.subscriptions.gui.GuiManager;
import dev.superseller.subscriptions.model.ChargePolicy;
import dev.superseller.subscriptions.model.Plan;
import dev.superseller.subscriptions.model.Subscription;
import dev.superseller.subscriptions.model.SubscriptionStatus;
import dev.superseller.subscriptions.service.BillingService;
import dev.superseller.subscriptions.storage.Database;
import dev.superseller.subscriptions.util.Colors;
import dev.superseller.subscriptions.util.Text;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class SubCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "browse", "create", "info", "subscribe", "cancel", "pause", "resume",
            "policy", "inbox", "mysubs", "myplans", "history", "stock", "help");

    private final PluginSettings settings;
    private final Database database;
    private final BillingService billing;
    private final GuiManager gui;

    public SubCommand(PluginSettings settings, Database database, BillingService billing, GuiManager gui) {
        this.settings = settings;
        this.database = database;
        this.billing = billing;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Colors.parse(settings.msg("generic.players-only")));
            return true;
        }
        if (!player.hasPermission("subscriptions.use")) {
            tell(player, settings.msg("generic.no-permission"));
            return true;
        }
        if (args.length == 0) {
            gui.openMain(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "browse" -> {
                if (!player.hasPermission("subscriptions.browse")) {
                    tell(player, settings.msg("generic.no-permission"));
                    return true;
                }
                String query = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
                gui.openBrowse(player, 0, query, "ALL");
            }
            case "create" -> {
                if (!player.hasPermission("subscriptions.create")) {
                    tell(player, settings.msg("generic.no-permission"));
                    return true;
                }
                gui.drafts().remove(player.getUniqueId());
                gui.openCreate(player);
            }
            case "info" -> {
                Plan plan = args.length > 1 ? database.plan(args[1]) : null;
                if (plan == null) {
                    tell(player, settings.msg("generic.unknown-plan"));
                    return true;
                }
                gui.openPlan(player, plan.id());
            }
            case "subscribe" -> {
                if (args.length < 2) {
                    tell(player, "&cUsage: /" + label + " subscribe <plan>");
                    return true;
                }
                Plan plan = database.plan(args[1]);
                String error = billing.subscribe(player, plan, false);
                if (error != null) {
                    tell(player, error);
                }
            }
            case "cancel" -> mutate(player, args, true, false);
            case "pause" -> mutate(player, args, false, true);
            case "resume" -> mutate(player, args, false, false);
            case "policy" -> {
                if (args.length < 3) {
                    tell(player, "&cUsage: /" + label + " policy <id> <pause|skip|cancel>");
                    return true;
                }
                Subscription subscription = owned(player, args[1]);
                if (subscription == null) {
                    return true;
                }
                ChargePolicy policy = ChargePolicy.fromString(args[2], null);
                if (policy == null) {
                    tell(player, settings.msg("generic.unknown-command"));
                    return true;
                }
                billing.setPolicy(subscription, policy);
                Plan plan = database.plan(subscription.planId());
                tell(player, Text.apply(settings.msg("sub.policy"), Map.of(
                        "name", plan == null ? subscription.planId() : plan.name(),
                        "policy", policy.display())));
            }
            case "inbox" -> gui.openInbox(player, 0);
            case "mysubs" -> gui.openMySubs(player, 0);
            case "myplans" -> gui.openMyPlans(player, 0);
            case "history" -> gui.openHistory(player, 0);
            case "stock" -> {
                if (args.length < 2) {
                    tell(player, "&cUsage: /" + label + " stock <plan>");
                    return true;
                }
                Plan plan = database.plan(args[1]);
                if (plan == null || (!plan.ownedBy(player.getUniqueId()) && !player.hasPermission("subscriptions.admin"))) {
                    tell(player, settings.msg("plan.not-owner"));
                    return true;
                }
                gui.openStock(player, plan.id());
            }
            case "help" -> {
                for (String line : settings.msgList("help")) {
                    player.sendMessage(Colors.parse(line));
                }
            }
            default -> tell(player, settings.msg("generic.unknown-command"));
        }
        return true;
    }

    private void mutate(Player player, String[] args, boolean cancel, boolean pause) {
        if (args.length < 2) {
            tell(player, "&cUsage: /sub " + args[0] + " <id>");
            return;
        }
        Subscription subscription = owned(player, args[1]);
        if (subscription == null) {
            return;
        }
        Plan plan = database.plan(subscription.planId());
        if (cancel) {
            String error = billing.cancel(subscription, "Cancelled by player", false);
            if (error != null) {
                tell(player, error);
            }
            return;
        }
        boolean shouldPause = pause && subscription.status() != SubscriptionStatus.PAUSED;
        billing.pause(subscription, shouldPause);
        tell(player, Text.apply(settings.msg(shouldPause ? "sub.paused" : "sub.resumed"),
                Map.of("name", plan == null ? subscription.planId() : plan.name())));
    }

    private Subscription owned(Player player, String id) {
        Subscription subscription = database.subscription(id);
        if (subscription == null || !subscription.subscriberId().equals(player.getUniqueId())) {
            tell(player, settings.msg("generic.unknown-sub"));
            return null;
        }
        return subscription;
    }

    private void tell(Player player, String message) {
        player.sendMessage(Colors.parse(settings.prefix() + message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        if (args.length <= 1) {
            values.addAll(SUBS);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("info") || sub.equals("subscribe") || sub.equals("stock")) {
                for (Plan plan : database.plans()) {
                    values.add(plan.id());
                }
            } else if (sub.equals("cancel") || sub.equals("pause") || sub.equals("resume") || sub.equals("policy")) {
                if (sender instanceof Player player) {
                    for (Subscription subscription : database.subscriptionsOf(player.getUniqueId())) {
                        values.add(subscription.id());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("policy")) {
            values.addAll(List.of("pause", "skip", "cancel"));
        }
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
