package dev.superseller.playerbank.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class BankAccount {

    private final UUID uuid;
    private String lastName;
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

    public double balance() {
        return balance;
    }

    public void balance(double balance) {
        this.balance = Math.max(0, balance);
    }

    public void add(double amount) {
        this.balance = Math.max(0, this.balance + amount);
    }

    public boolean subtract(double amount) {
        if (amount > balance + 1e-9) {
            return false;
        }
        this.balance = Math.max(0, this.balance - amount);
        return true;
    }

    public Deque<BankLogEntry> logs() {
        return logs;
    }

    public void addLog(BankLogEntry entry, int max) {
        logs.addFirst(entry);
        while (logs.size() > max) {
            logs.removeLast();
        }
    }

    public List<BankLogEntry> logPage(int page, int pageSize) {
        List<BankLogEntry> all = new ArrayList<>(logs);
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(all.size(), from + pageSize);
        if (from >= all.size()) {
            return List.of();
        }
        return all.subList(from, to);
    }

    public int logPages(int pageSize) {
        if (logs.isEmpty()) {
            return 1;
        }
        return (int) Math.ceil(logs.size() / (double) pageSize);
    }
}
