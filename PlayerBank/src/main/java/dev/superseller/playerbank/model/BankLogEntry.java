package dev.superseller.playerbank.model;

public final class BankLogEntry {

    private final long time;
    private final String type;
    private final double amount;
    private final String note;

    public BankLogEntry(long time, String type, double amount, String note) {
        this.time = time;
        this.type = type;
        this.amount = amount;
        this.note = note;
    }

    public long time() {
        return time;
    }

    public String type() {
        return type;
    }

    public double amount() {
        return amount;
    }

    public String note() {
        return note;
    }
}
