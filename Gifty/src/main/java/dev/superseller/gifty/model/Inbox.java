package dev.superseller.gifty.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player's inbox: an ordered list of pending deliveries (newest first).
 */
public final class Inbox {

    private final UUID owner;
    private final List<Delivery> deliveries = new ArrayList<>();

    public Inbox(UUID owner) {
        this.owner = owner;
    }

    public UUID owner() {
        return owner;
    }

    public synchronized void add(Delivery delivery) {
        deliveries.add(0, delivery);
    }

    public synchronized Delivery remove(long id) {
        for (int i = 0; i < deliveries.size(); i++) {
            if (deliveries.get(i).id() == id) {
                return deliveries.remove(i);
            }
        }
        return null;
    }

    public synchronized Delivery get(long id) {
        for (Delivery d : deliveries) {
            if (d.id() == id) {
                return d;
            }
        }
        return null;
    }

    public synchronized List<Delivery> list() {
        return new ArrayList<>(deliveries);
    }

    public synchronized int size() {
        return deliveries.size();
    }

    public synchronized boolean isEmpty() {
        return deliveries.isEmpty();
    }

    public synchronized double totalMoney() {
        double sum = 0;
        for (Delivery d : deliveries) {
            sum += d.money();
        }
        return sum;
    }

    public synchronized long oldestSentAt() {
        long oldest = Long.MAX_VALUE;
        for (Delivery d : deliveries) {
            if (d.sentAt() < oldest) {
                oldest = d.sentAt();
            }
        }
        return oldest == Long.MAX_VALUE ? 0L : oldest;
    }

    // ------------------------------------------------------------ storage

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("owner", owner.toString());
        List<Object> list = new ArrayList<>();
        synchronized (this) {
            for (Delivery d : deliveries) {
                list.add(d.toMap());
            }
        }
        map.put("deliveries", list);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Inbox fromMap(UUID owner, Map<String, Object> map) {
        Inbox inbox = new Inbox(owner);
        Object d = map.get("deliveries");
        if (d instanceof List) {
            for (Object o : (List<Object>) d) {
                if (o instanceof Map) {
                    inbox.add(Delivery.fromMap((Map<String, Object>) o));
                }
            }
        }
        return inbox;
    }
}
