package dev.superseller.veltryx.util;

import dev.superseller.veltryx.config.Messages;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Sell payout notifications: the compact amount ({@code $2.3k}, {@code $500k})
 * is shown above the hotbar (action bar) with a lime {@code $}, and again in
 * chat through the configurable templates.
 */
public final class SellNotices {

    private SellNotices() {
    }

    /**
     * Shows the payout on the action bar and in chat.
     *
     * @param chatKey   messages.yml key for the chat line (gets {@code {money}}
     *                  plus everything in {@code extra})
     * @param extra     extra placeholder values (e.g. item name, count, balance)
     * @param earnedCents payout amount in cents
     */
    public static void show(Player player, Messages messages, String chatKey,
                            Map<String, String> extra, long earnedCents) {
        String compact = Money.formatCompact(earnedCents);
        Map<String, String> values = new HashMap<>(extra);
        values.put("money", compact);
        player.sendActionBar(messages.render("sell.actionbar", Map.of("money", compact)));
        player.sendMessage(messages.render(chatKey, values));
    }

    /** Exact marketplace payout in chat; the action bar remains compact. */
    public static void show(Player player, Messages messages, String chatKey,
                            Map<String, String> extra, java.math.BigInteger earnedCents) {
        Map<String, String> values = new HashMap<>(extra);
        values.put("money", Money.formatNumber(earnedCents));
        player.sendActionBar(messages.render("sell.actionbar", Map.of("money", Money.formatCompact(earnedCents))));
        player.sendMessage(messages.render(chatKey, values));
    }

    /** Compact balance string for templates ({@code 2.3k}). */
    public static String compact(java.math.BigInteger cents) { return Money.formatCompact(cents); }

    public static String compact(long cents) {
        return Money.formatCompact(cents);
    }
}
