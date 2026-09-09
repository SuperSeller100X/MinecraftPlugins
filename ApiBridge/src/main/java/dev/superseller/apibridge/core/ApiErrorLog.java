package dev.superseller.apibridge.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public final class ApiErrorLog {
    private final int capacity;
    private final LinkedList<Entry> entries = new LinkedList<>();

    public ApiErrorLog(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized void add(String requestId, String route, int status, String code, String safeMessage) {
        entries.addFirst(new Entry(Instant.now(), requestId, route, status, code, safeMessage));
        while (entries.size() > capacity) {
            entries.removeLast();
        }
    }

    public synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    public record Entry(Instant timestamp, String requestId, String route, int status, String code, String message) { }
}
