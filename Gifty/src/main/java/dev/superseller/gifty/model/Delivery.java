package dev.superseller.gifty.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A single gift/delivery sent to a player's inbox.
 */
public final class Delivery {

    private final long id;
    private final UUID fromUuid; // null when sent by the system (refunds)
    private final String fromName;
    private final long sentAt;
    private final String message; // legacy §-coded or null
    private final double money;
    private final List<SerialItem> items;

    public Delivery(long id, UUID fromUuid, String fromName, long sentAt, String message,
                    double money, List<SerialItem> items) {
        this.id = id;
        this.fromUuid = fromUuid;
        this.fromName = fromName;
        this.sentAt = sentAt;
        this.message = message;
        this.money = money;
        this.items = items == null ? new ArrayList<SerialItem>() : items;
    }

    public long id() {
        return id;
    }

    public UUID fromUuid() {
        return fromUuid;
    }

    public String fromName() {
        return fromName;
    }

    public long sentAt() {
        return sentAt;
    }

    public String message() {
        return message;
    }

    public double money() {
        return money;
    }

    public List<SerialItem> items() {
        return items;
    }

    public int totalItems() {
        int n = 0;
        for (SerialItem i : items) {
            n += i.amount();
        }
        return n;
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    public boolean hasMoney() {
        return money > 0;
    }

    public boolean isEmpty() {
        return !hasItems() && !hasMoney();
    }

    // ------------------------------------------------------------ storage

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("from", fromUuid == null ? "system" : fromUuid.toString());
        map.put("fromName", fromName);
        map.put("sentAt", sentAt);
        if (message != null) {
            map.put("message", message);
        }
        map.put("money", money);
        List<Object> itemList = new ArrayList<>();
        for (SerialItem i : items) {
            itemList.add(i.toMap());
        }
        map.put("items", itemList);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Delivery fromMap(Map<String, Object> map) {
        long id = num(map.get("id"), 0L);
        String from = String.valueOf(map.getOrDefault("from", "system"));
        UUID fromUuid = null;
        if (!"system".equals(from)) {
            try {
                fromUuid = UUID.fromString(from);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String fromName = String.valueOf(map.getOrDefault("fromName", "Unknown"));
        long sentAt = num(map.get("sentAt"), System.currentTimeMillis());
        Object msg = map.get("message");
        String message = msg == null ? null : String.valueOf(msg);
        double money = dbl(map.get("money"));
        List<SerialItem> items = new ArrayList<>();
        Object it = map.get("items");
        if (it instanceof List) {
            for (Object o : (List<Object>) it) {
                if (o instanceof Map) {
                    items.add(SerialItem.fromMap((Map<String, Object>) o));
                }
            }
        }
        return new Delivery(id, fromUuid, fromName, sentAt, message, money, items);
    }

    private static long num(Object o, long def) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o != null) {
            try {
                return Long.parseLong(String.valueOf(o));
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static double dbl(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(String.valueOf(o));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0d;
    }
}
