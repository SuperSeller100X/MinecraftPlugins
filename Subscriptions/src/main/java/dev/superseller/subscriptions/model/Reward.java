package dev.superseller.subscriptions.model;

import dev.superseller.subscriptions.util.Numbers;

import java.util.Locale;
import java.util.Objects;

/**
 * One reward granted every successful (or trial) billing cycle.
 *
 * <p>Encoded as a single line for SQLite:</p>
 * <pre>
 * ITEM|&lt;base64&gt;|&lt;amount&gt;
 * MONEY|&lt;amount&gt;
 * COMMAND|CONSOLE|say hello {player}
 * COMMAND|PLAYER|warp spawn
 * PERMISSION|some.node
 * GROUP|vip
 * MESSAGE|&amp;aThanks for subscribing!
 * </pre>
 */
public final class Reward {

    private final RewardType type;
    private final String data;
    private final String extra;
    private final double amount;

    public Reward(RewardType type, String data, String extra, double amount) {
        this.type = Objects.requireNonNull(type, "type");
        this.data = data == null ? "" : data;
        this.extra = extra == null ? "" : extra;
        this.amount = amount;
    }

    public static Reward item(String itemBase64, int amount) {
        return new Reward(RewardType.ITEM, itemBase64, "", Math.max(1, amount));
    }

    public static Reward money(double amount) {
        return new Reward(RewardType.MONEY, "", "", Math.max(0d, amount));
    }

    public static Reward command(boolean console, String command) {
        return new Reward(RewardType.COMMAND, console ? "CONSOLE" : "PLAYER", command, 0d);
    }

    public static Reward permission(String node) {
        return new Reward(RewardType.PERMISSION, node, "", 0d);
    }

    public static Reward group(String group) {
        return new Reward(RewardType.GROUP, group, "", 0d);
    }

    public static Reward message(String text) {
        return new Reward(RewardType.MESSAGE, text, "", 0d);
    }

    public RewardType type() {
        return type;
    }

    public String data() {
        return data;
    }

    public String extra() {
        return extra;
    }

    public double amount() {
        return amount;
    }

    public int intAmount() {
        return (int) Math.max(1d, Math.round(amount));
    }

    public boolean consoleCommand() {
        return type == RewardType.COMMAND && "CONSOLE".equalsIgnoreCase(data);
    }

    public String encode() {
        return switch (type) {
            case ITEM -> "ITEM|" + data + "|" + intAmount();
            case MONEY -> "MONEY|" + amount;
            case COMMAND -> "COMMAND|" + data + "|" + extra.replace('\n', ' ');
            case PERMISSION -> "PERMISSION|" + data;
            case GROUP -> "GROUP|" + data;
            case MESSAGE -> "MESSAGE|" + data.replace('\n', ' ');
        };
    }

    public static Reward decode(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\\|", 3);
        if (parts.length == 0) {
            return null;
        }
        RewardType type = RewardType.fromString(parts[0], null);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case ITEM -> item(parts.length > 1 ? parts[1] : "", parts.length > 2 ? parseInt(parts[2], 1) : 1);
            case MONEY -> money(parts.length > 1 ? parseDouble(parts[1], 0d) : 0d);
            case COMMAND -> command(parts.length > 1 && parts[1].equalsIgnoreCase("CONSOLE"),
                    parts.length > 2 ? parts[2] : "");
            case PERMISSION -> permission(parts.length > 1 ? parts[1] : "");
            case GROUP -> group(parts.length > 1 ? parts[1] : "");
            case MESSAGE -> message(parts.length > 1 ? joinRest(parts) : "");
        };
    }

    public String describe() {
        return switch (type) {
            case ITEM -> intAmount() + "x item kit";
            case MONEY -> Numbers.compact(amount) + " money";
            case COMMAND -> (consoleCommand() ? "console" : "player") + " command";
            case PERMISSION -> "permission " + data;
            case GROUP -> "rank " + data;
            case MESSAGE -> "message";
        };
    }

    private static String joinRest(String[] parts) {
        if (parts.length == 1) {
            return "";
        }
        if (parts.length == 2) {
            return parts[1];
        }
        return parts[1] + "|" + parts[2];
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return type.name().toLowerCase(Locale.ROOT) + "(" + describe() + ")";
    }
}
