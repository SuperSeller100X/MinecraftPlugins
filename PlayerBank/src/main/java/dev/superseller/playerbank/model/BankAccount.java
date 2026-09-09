package dev.superseller.playerbank.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * A player's bank account. On Folia this object is touched from the owning
 * player's region thread (deposits, withdrawals, GUI) and from the global
 * region thread (interest ticks) at the same time, so all state access is
 * synchronized. Contention is negligible: operations are O(1) map/arith.
 */
public final class BankAccount {

    private final UUID uuid;
    private volatile String lastName;
    private double balance;
    private final Deque<BankLogEntry> logs = new ArrayDeque<>();

    public BankAccount(UUID uuid, String lastName) {
        this.uuid = uuid;
        this.lastName = lastName;
    }

    public UUID uuid() {
        return uuid;
    }

    public String lastName() {
        return lastName;
    }

    public void lastName(String lastName) {
        this.lastName = lastName;
    }

    public synchronized double balance() {
        return balance;
    }

    public synchronized void balance(double balance) {
        this.balance = Math.max(0, balance);
    }

    public synchronized void add(double amount) {
        this.balance = Math.max(0, this.balance + amount);
    }

    public synchronized boolean subtract(double amount) {
        if (amount > balance + 1e-9) {
            return false;
        }
        this.balance = Math.max(0, this.balance - amount);
        return true;
    }

    /** Appends the newest entry and trims the ring buffer to {@code max}. */
    public synchronized void addLog(BankLogEntry entry, int max) {
        logs.addFirst(entry);
        while (logs.size() > max) {
            logs.removeLast();
        }
    }

    /** Used only while (re)loading an account from disk — oldest first. */
    public synchronized void appendLoadedLog(BankLogEntry entry) {
        logs.addLast(entry);
    }

    /** Consistent copy of the log ring buffer, newest first. */
    public synchronized List<BankLogEntry> logSnapshot() {
        return new ArrayList<>(logs);
    }

    public List<BankLogEntry> logPage(int page, int pageSize) {
        List<BankLogEntry> all = logSnapshot();
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(all.size(), from + pageSize);
        if (from >= all.size()) {
            return List.of();
        }
        return new ArrayList<>(all.subList(from, to));
    }

    public int logPages(int pageSize) {
        int size = logSnapshot().size();
        if (size == 0) {
            return 1;
        }
        return (int) Math.ceil(size / (double) pageSize);
    }
}
