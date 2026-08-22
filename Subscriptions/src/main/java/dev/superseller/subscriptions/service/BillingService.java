package dev.superseller.subscriptions.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.superseller.subscriptions.api.event.SubscriptionCancelEvent;
import dev.superseller.subscriptions.api.event.SubscriptionChargeEvent;
import dev.superseller.subscriptions.api.event.SubscriptionStartEvent;
import dev.superseller.subscriptions.config.PluginSettings;
import dev.superseller.subscriptions.economy.EconomyService;
import dev.superseller.subscriptions.hook.CsvLogger;
import dev.superseller.subscriptions.hook.DiscordHook;
import dev.superseller.subscriptions.hook.LuckPermsHook;
import dev.superseller.subscriptions.model.BillingDecision;
import dev.superseller.subscriptions.model.ChargePolicy;
import dev.superseller.subscriptions.model.Plan;
import dev.superseller.subscriptions.model.Reward;
import dev.superseller.subscriptions.model.RewardType;
import dev.superseller.subscriptions.model.Subscription;
import dev.superseller.subscriptions.model.SubscriptionStatus;
import dev.superseller.subscriptions.scheduler.PlatformScheduler;
import dev.superseller.subscriptions.storage.Database;
import dev.superseller.subscriptions.util.Colors;
import dev.superseller.subscriptions.util.Ids;
import dev.superseller.subscriptions.util.ItemSerial;
import dev.superseller.subscriptions.util.Numbers;
import dev.superseller.subscriptions.util.Text;
import dev.superseller.subscriptions.util.TimeParser;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Marketplace subscribe / cancel / billing. Charge happens only after both
 * sides can complete the cycle (anti-scam). Folia: this runs on the global
 * region scheduler; player messages are bounced to the entity scheduler.
 */
public final class BillingService {

    private final JavaPlugin plugin;
    private final Database database;
    private final EconomyService economy;
    private final InboxService inbox;
    private final PluginSettings settings;
    private final LuckPermsHook luckPerms;
    private final DiscordHook discord;
    private final CsvLogger csv;
    private final PlatformScheduler scheduler;
    private final AtomicBoolean ticking = new AtomicBoolean();

    public BillingService(JavaPlugin plugin, Database database, EconomyService economy, InboxService inbox,
                          PluginSettings settings, LuckPermsHook luckPerms, DiscordHook discord,
                          CsvLogger csv, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.inbox = inbox;
        this.settings = settings;
        this.luckPerms = luckPerms;
        this.discord = discord;
        this.csv = csv;
        this.scheduler = scheduler;
    }

    public String subscribe(Player player, Plan plan, boolean adminForce) {
        if (plan == null) {
            return settings.msg("generic.unknown-plan");
        }
        if (!adminForce && !plan.status().joinable()) {
            return settings.msg("plan.not-joinable");
        }
        if (!adminForce && !plan.hasCapacity()) {
            return settings.msg("plan.no-capacity");
        }
        if (database.activeOn(player.getUniqueId(), plan.id()) != null) {
            return settings.msg("plan.already-subscribed");
        }
        if (!adminForce && !player.hasPermission("subscriptions.bypass.limit")
                && livingCount(player.getUniqueId()) >= settings.maxActiveSubs()) {
            return Text.apply(settings.msg("plan.too-many-subs"),
                    Map.of("max", String.valueOf(settings.maxActiveSubs())));
        }
        if (plan.rewards().isEmpty()) {
            return settings.msg("plan.no-rewards");
        }
        double first = firstCharge(plan);
        if (first > 0d && !economy.has(player.getUniqueId(), first)) {
            return settings.msg("generic.economy-missing").equals(settings.msg("generic.economy-missing"))
                    && !economy.isEnabled()
                    ? settings.msg("generic.economy-missing")
                    : Colors.parse("&cYou need " + economy.format(first) + " to start this subscription.");
        }
        Subscription sub = new Subscription(Ids.subscriptionId(), plan.id(), player.getUniqueId());
        sub.subscriberName(player.getName());
        sub.nextChargeAt(System.currentTimeMillis());
        SubscriptionStartEvent event = new SubscriptionStartEvent(sub, plan);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Colors.parse("&cAnother plugin blocked that subscription.");
        }
        database.saveSubscription(sub);
        tell(player, Text.apply(settings.msg("sub.started"), Map.of(
                "name", plan.name(),
                "when", TimeParser.formatRemaining(sub.nextChargeAt()))));
        process(sub, System.currentTimeMillis());
        return null;
    }

    public String cancel(Subscription sub, String reason, boolean byAdmin) {
        if (sub == null) {
            return settings.msg("generic.unknown-sub");
        }
        if (!sub.status().living() && !byAdmin) {
            return settings.msg("sub.cannot-cancel");
        }
        Plan plan = database.plan(sub.planId());
        revokePrivileges(sub, plan);
        sub.status(SubscriptionStatus.CANCELLED);
        sub.cancelledAt(System.currentTimeMillis());
        sub.cancelReason(reason);
        sub.autoRenew(false);
        database.saveSubscription(sub);
        Bukkit.getPluginManager().callEvent(new SubscriptionCancelEvent(sub, plan, reason));
        discord.cancel(sub.subscriberName(), plan == null ? sub.planId() : plan.name(), reason);
        Player player = Bukkit.getPlayer(sub.subscriberId());
        if (player != null) {
            tell(player, Text.apply(settings.msg("sub.cancelled"),
                    Map.of("name", plan == null ? sub.planId() : plan.name())));
        }
        return null;
    }

    public void pause(Subscription sub, boolean paused) {
        if (sub == null || !sub.status().living()) {
            return;
        }
        sub.status(paused ? SubscriptionStatus.PAUSED : SubscriptionStatus.ACTIVE);
        if (!paused && sub.nextChargeAt() < System.currentTimeMillis()) {
            sub.nextChargeAt(System.currentTimeMillis());
        }
        database.saveSubscription(sub);
    }

    public void setPolicy(Subscription sub, ChargePolicy policy) {
        if (sub == null) {
            return;
        }
        sub.policyOverride(policy);
        database.saveSubscription(sub);
    }

    public void tick() {
        if (!ticking.compareAndSet(false, true)) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            for (Subscription sub : database.due(now)) {
                try {
                    process(sub, now);
                } catch (Exception e) {
                    plugin.getLogger().warning("Billing failed for " + sub.id() + ": " + e.getMessage());
                }
            }
        } finally {
            ticking.set(false);
        }
    }

    public void process(Subscription sub, long now) {
        Plan plan = database.plan(sub.planId());
        if (plan == null || !plan.status().joinable() && sub.status() == SubscriptionStatus.ACTIVE) {
            if (plan == null) {
                cancel(sub, "Plan deleted", true);
            }
            return;
        }
        if (sub.status() == SubscriptionStatus.PAUSED) {
            return;
        }
        if (sub.reachedMaxCycles(plan) || !sub.autoRenew()) {
            expire(sub, plan);
            return;
        }
        boolean trial = sub.inTrial(plan);
        boolean firstCycle = sub.cycles() == 0;
        double due = (trial ? 0d : plan.price()) + (firstCycle ? plan.signupFee() : 0d);
        boolean canPay = due <= 0d || (economy.isEnabled() && economy.has(sub.subscriberId(), due));
        String fulfillBlock = fulfillBlockReason(plan, sub);
        boolean canFulfill = fulfillBlock == null;
        ChargePolicy policy = sub.effectivePolicy(plan);
        BillingDecision.Action action = BillingDecision.decide(canPay, canFulfill, policy, trial);

        if (action == BillingDecision.Action.CHARGE || action == BillingDecision.Action.TRIAL) {
            SubscriptionChargeEvent event = new SubscriptionChargeEvent(sub, plan, due, trial);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                sub.bump(plan.intervalMs());
                database.saveSubscription(sub);
                return;
            }
        }

        switch (action) {
            case CHARGE -> charge(sub, plan, due, false);
            case TRIAL -> charge(sub, plan, due, true);
            case PAUSE -> pauseFor(sub, plan, canPay ? "stock" : "funds",
                    canPay ? (fulfillBlock == null ? "seller cannot fulfil" : fulfillBlock) : "not enough money");
            case SKIP -> skip(sub, plan, canPay ? (fulfillBlock == null ? "unfulfilled" : fulfillBlock) : "not enough money");
            case CANCEL -> cancel(sub, canPay
                    ? (fulfillBlock == null ? "seller cannot fulfil" : fulfillBlock)
                    : "not enough money", true);
        }
    }

    private void charge(Subscription sub, Plan plan, double due, boolean trial) {
        if (due > 0d) {
            if (!economy.withdraw(sub.subscriberId(), due)) {
                pauseFor(sub, plan, "funds", "withdraw failed");
                return;
            }
        }
        if (plan.needsItemStock() && !plan.consumeKit()) {
            if (due > 0d) {
                economy.deposit(sub.subscriberId(), due);
            }
            pauseFor(sub, plan, "stock", "out of stock");
            return;
        }
        if (!deliverRewards(sub, plan)) {
            if (due > 0d) {
                economy.deposit(sub.subscriberId(), due);
            }
            if (plan.needsItemStock() && !plan.infiniteStock()) {
                plan.addKits(1);
            }
            database.savePlan(plan);
            pauseFor(sub, plan, "stock", "delivery failed");
            return;
        }
        double tax = due > 0d ? Numbers.round(due * settings.taxPercent() / 100d, settings.decimalPlaces()) : 0d;
        double sellerGets = Math.max(0d, due - tax);
        if (sellerGets > 0d && plan.ownerId() != null) {
            economy.deposit(plan.ownerId(), sellerGets);
            Player seller = Bukkit.getPlayer(plan.ownerId());
            if (seller != null) {
                tell(seller, Text.apply(settings.msg("sub.seller-paid"), Map.of(
                        "player", sub.subscriberName(),
                        "amount", economy.format(sellerGets),
                        "name", plan.name())));
            }
        }
        sub.incrementCycles();
        sub.status(SubscriptionStatus.ACTIVE);
        if (sub.reachedMaxCycles(plan)) {
            expire(sub, plan);
        } else {
            sub.bump(plan.intervalMs());
            database.saveSubscription(sub);
        }
        database.savePlan(plan);
        database.logCharge(sub.id(), plan.id(), sub.subscriberId(), plan.ownerId(), due, tax,
                trial ? "TRIAL" : "CHARGE", trial ? "trial cycle" : "ok");
        csv.write(sub.id(), plan.id(), sub.subscriberName(), trial ? "TRIAL" : "CHARGE", due, plan.name());
        discord.charge(sub.subscriberName(), plan.name(), economy.format(due), trial ? "trial" : "charged");
        Player player = Bukkit.getPlayer(sub.subscriberId());
        if (player != null) {
            tell(player, Text.apply(settings.msg(trial ? "sub.trial" : "sub.charged"), Map.of(
                    "name", plan.name(),
                    "amount", economy.format(due),
                    "when", TimeParser.format(plan.intervalMs()))));
        }
        if (plan.needsItemStock() && !plan.infiniteStock() && plan.stockKits() == 0) {
            discord.stock(plan.name(), plan.ownerName());
            Player owner = plan.ownerId() == null ? null : Bukkit.getPlayer(plan.ownerId());
            if (owner != null) {
                tell(owner, Text.apply(settings.msg("plan.stock-empty"), Map.of("name", plan.name())));
            }
        }
    }

    private void skip(Subscription sub, Plan plan, String reason) {
        sub.status(SubscriptionStatus.ACTIVE);
        sub.bump(plan.intervalMs());
        database.saveSubscription(sub);
        database.logCharge(sub.id(), plan.id(), sub.subscriberId(), plan.ownerId(), 0d, 0d, "SKIP", reason);
        csv.write(sub.id(), plan.id(), sub.subscriberName(), "SKIP", 0d, reason);
        Player player = Bukkit.getPlayer(sub.subscriberId());
        if (player != null) {
            tell(player, Text.apply(settings.msg("sub.skipped"), Map.of(
                    "name", plan.name(),
                    "reason", reason,
                    "when", TimeParser.format(plan.intervalMs()))));
        }
    }

    private void pauseFor(Subscription sub, Plan plan, String kind, String reason) {
        sub.status("stock".equals(kind) ? SubscriptionStatus.PAUSED_STOCK : SubscriptionStatus.PAUSED_FUNDS);
        database.saveSubscription(sub);
        database.logCharge(sub.id(), plan.id(), sub.subscriberId(), plan.ownerId(), 0d, 0d, "PAUSE", reason);
        csv.write(sub.id(), plan.id(), sub.subscriberName(), "PAUSE", 0d, reason);
        Player player = Bukkit.getPlayer(sub.subscriberId());
        if (player != null) {
            tell(player, Text.apply(settings.msg("sub.auto-paused"), Map.of(
                    "name", plan.name(),
                    "reason", reason)));
        }
    }

    private void expire(Subscription sub, Plan plan) {
        revokePrivileges(sub, plan);
        sub.status(SubscriptionStatus.EXPIRED);
        sub.autoRenew(false);
        sub.cancelledAt(System.currentTimeMillis());
        sub.cancelReason("max cycles");
        database.saveSubscription(sub);
        Player player = Bukkit.getPlayer(sub.subscriberId());
        if (player != null) {
            tell(player, Text.apply(settings.msg("sub.expired"),
                    Map.of("name", plan == null ? sub.planId() : plan.name())));
        }
    }

    private String fulfillBlockReason(Plan plan, Subscription sub) {
        if (plan.rewards().isEmpty()) {
            return "no rewards";
        }
        if (plan.needsItemStock() && !plan.infiniteStock() && plan.stockKits() <= 0) {
            return "out of stock";
        }
        if (plan.needsSellerFunds()) {
            if (!economy.isEnabled()) {
                return "no economy";
            }
            if (plan.ownerId() == null) {
                return null;
            }
            if (!economy.has(plan.ownerId(), plan.sellerPayoutCost())) {
                return "seller cannot fund payout";
            }
        }
        if (inbox.size(sub.subscriberId()) >= settings.maxInbox() && plan.needsItemStock()) {
            return "subscriber inbox full";
        }
        for (Reward reward : plan.rewards()) {
            if (reward.type().requiresLuckPerms() && !luckPerms.isEnabled()) {
                return "LuckPerms missing";
            }
            if (reward.type() == RewardType.GROUP && !luckPerms.groupExists(reward.data())) {
                return "unknown rank " + reward.data();
            }
        }
        return null;
    }

    private boolean deliverRewards(Subscription sub, Plan plan) {
        Duration duration = Duration.ofMillis(plan.intervalMs());
        for (Reward reward : plan.rewards()) {
            switch (reward.type()) {
                case ITEM -> {
                    ItemStack template = ItemSerial.decode(reward.data());
                    if (template == null) {
                        return false;
                    }
                    ItemStack give = ItemSerial.cloneWithAmount(template, reward.intAmount());
                    if (!inbox.deliver(sub.subscriberId(), give, plan.name())) {
                        return false;
                    }
                    Player target = Bukkit.getPlayer(sub.subscriberId());
                    if (target != null) {
                        tell(target, Text.apply(settings.msg("inbox.delivered"), Map.of("name", plan.name())));
                    }
                }
                case MONEY -> {
                    if (reward.amount() <= 0d) {
                        continue;
                    }
                    if (plan.ownerId() != null && !economy.withdraw(plan.ownerId(), reward.amount())) {
                        return false;
                    }
                    if (!economy.deposit(sub.subscriberId(), reward.amount())) {
                        if (plan.ownerId() != null) {
                            economy.deposit(plan.ownerId(), reward.amount());
                        }
                        return false;
                    }
                }
                case COMMAND -> {
                    String command = interpolate(reward.extra(), sub, plan);
                    if (command.startsWith("/")) {
                        command = command.substring(1);
                    }
                    if (reward.consoleCommand()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    } else {
                        Player target = Bukkit.getPlayer(sub.subscriberId());
                        if (target != null) {
                            target.performCommand(command);
                        } else {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                        }
                    }
                }
                case PERMISSION -> luckPerms.grantPermission(sub.subscriberId(), reward.data(), duration);
                case GROUP -> luckPerms.grantGroup(sub.subscriberId(), reward.data(), duration);
                case MESSAGE -> {
                    Player target = Bukkit.getPlayer(sub.subscriberId());
                    if (target != null) {
                        tell(target, interpolate(reward.data(), sub, plan));
                    }
                }
            }
        }
        return true;
    }

    private void revokePrivileges(Subscription sub, Plan plan) {
        if (plan == null) {
            return;
        }
        for (Reward reward : plan.rewards()) {
            if (reward.type() == RewardType.PERMISSION) {
                luckPerms.revokePermission(sub.subscriberId(), reward.data());
            } else if (reward.type() == RewardType.GROUP) {
                luckPerms.revokeGroup(sub.subscriberId(), reward.data());
            }
        }
    }

    private String interpolate(String template, Subscription sub, Plan plan) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{player}", sub.subscriberName())
                .replace("{uuid}", sub.subscriberId().toString())
                .replace("{plan}", plan.name())
                .replace("{plan_id}", plan.id())
                .replace("{price}", Numbers.compact(plan.price()))
                .replace("{seller}", plan.ownerName());
    }

    public double firstCharge(Plan plan) {
        if (plan.trialCycles() > 0) {
            return plan.signupFee();
        }
        return plan.price() + plan.signupFee();
    }

    public int livingCount(UUID player) {
        int n = 0;
        for (Subscription sub : database.subscriptionsOf(player)) {
            if (sub.status().living()) {
                n++;
            }
        }
        return n;
    }

    public void tryResumePaused() {
        for (Subscription sub : database.subscriptions()) {
            if (sub.status() != SubscriptionStatus.PAUSED_STOCK && sub.status() != SubscriptionStatus.PAUSED_FUNDS) {
                continue;
            }
            Plan plan = database.plan(sub.planId());
            if (plan == null) {
                continue;
            }
            boolean trial = sub.inTrial(plan);
            double due = trial ? 0d : plan.price();
            boolean canPay = due <= 0d || economy.has(sub.subscriberId(), due);
            boolean canFulfill = fulfillBlockReason(plan, sub) == null;
            if (canPay && canFulfill) {
                sub.status(SubscriptionStatus.ACTIVE);
                sub.nextChargeAt(System.currentTimeMillis());
                database.saveSubscription(sub);
            }
        }
    }

    private void tell(Player player, String message) {
        scheduler.runEntity(player, () -> player.sendMessage(Colors.parse(settings.prefix() + message)));
    }
}
