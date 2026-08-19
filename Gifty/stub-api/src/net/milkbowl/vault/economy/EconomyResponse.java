package net.milkbowl.vault.economy;

public class EconomyResponse {
    public final double amount;
    public final double balance;
    public final String errorMessage;

    public EconomyResponse(double amount, double balance, String errorMessage) {
        this.amount = amount;
        this.balance = balance;
        this.errorMessage = errorMessage;
    }

    public boolean transactionSuccess() {
        return errorMessage == null || errorMessage.isEmpty();
    }
}
