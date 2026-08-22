package dev.superseller.subscriptions.hook;

import dev.superseller.subscriptions.SubscriptionsPlugin;
import dev.superseller.subscriptions.model.Plan;
import dev.superseller.subscriptions.model.Subscription;
import dev.superseller.subscriptions.storage.Database;
import dev.superseller.subscriptions.util.Numbers;
import dev.superseller.subscriptions.util.TimeParser;

import java.util.Locale;
import java.util.UUID;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class SubscriptionsExpansion extends PlaceholderExpansion {

    private final SubscriptionsPlugin plugin;
    private final Database database;

    public SubscriptionsExpansion(SubscriptionsPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "subscriptions";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SuperSeller100X";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return resolve(database, player, params);
    }

    static String resolve(Database database, OfflinePlayer player, String params) {
        if (params == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);
        UUID uuid = player == null ? null : player.getUniqueId();
        return switch (key) {
            case "active" -> String.valueOf(countLiving(database, uuid));
            case "owned" -> uuid == null ? "0" : String.valueOf(database.plansByOwner(uuid).size());
            case "inbox" -> uuid == null ? "0" : String.valueOf(database.inboxSize(uuid));
            case "spent" -> uuid == null ? "0" : Numbers.compact(database.spent(uuid));
            case "earned" -> uuid == null ? "0" : Numbers.compact(database.earned(uuid));
            case "plans" -> String.valueOf(database.planCount());
            case "live" -> String.valueOf(database.liveSubscriptionCount());
            case "next" -> nextCharge(database, uuid);
            default -> planPlaceholder(database, key);
        };
    }

    private static String planPlaceholder(Database database, String key) {
        if (!key.startsWith("plan_")) {
            return "";
        }
        String rest = key.substring(5);
        int split = rest.lastIndexOf('_');
        if (split <= 0) {
            return "";
        }
        Plan plan = database.plan(rest.substring(0, split));
        if (plan == null) {
            return "";
        }
        return switch (rest.substring(split + 1)) {
            case "name" -> plan.name();
            case "price" -> Numbers.compact(plan.price());
            case "subs", "subscribers" -> String.valueOf(plan.subscriberCount());
            case "stock" -> plan.infiniteStock() ? "∞" : String.valueOf(plan.stockKits());
            case "status" -> plan.status().name();
            default -> "";
        };
    }

    private static int countLiving(Database database, UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        int n = 0;
        for (Subscription sub : database.subscriptionsOf(uuid)) {
            if (sub.status().living()) {
                n++;
            }
        }
        return n;
    }

    private static String nextCharge(Database database, UUID uuid) {
        if (uuid == null) {
            return "";
        }
        long soonest = Long.MAX_VALUE;
        for (Subscription sub : database.subscriptionsOf(uuid)) {
            if (sub.status().billable() && sub.nextChargeAt() < soonest) {
                soonest = sub.nextChargeAt();
            }
        }
        return soonest == Long.MAX_VALUE ? "" : TimeParser.formatRemaining(soonest);
    }
}
