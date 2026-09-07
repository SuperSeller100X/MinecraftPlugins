package dev.superseller.playerbank.bank;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.config.BankConfig;
import dev.superseller.playerbank.gui.Amount;
import dev.superseller.playerbank.model.BankAccount;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * The single deposit / withdraw pipeline shared by the chat command and both
 * menu styles (chest inventory and native dialog). Applies the same limits,
 * rounding rules, storage logging and message keys everywhere, so GUI
 * behaviour can never drift from command behaviour.
 */
public final class Transactor {

    private final PlayerBankPlugin plugin;

    public Transactor(PlayerBankPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Moves {@code amount} from the player's wallet into the bank.
     * {@code allKeyword} only selects the friendlier "deposited everything"
     * message; the amount is resolved and floored by the caller.
     */
    public TransferResult deposit(Player player, double amount, boolean allKeyword) {
        if (!plugin.vault().isEnabled()) {
            return TransferResult.failure("economy-missing", Map.of());
        }
        BankConfig cfg = plugin.bankConfig();
        double wallet = plugin.vault().wallet(player);
        // "all" must round down: half-up rounding could exceed the wallet and fail.
        double resolved = allKeyword ? cfg.floorMoney(amount) : cfg.roundMoney(amount);
        if (resolved < cfg.minTransaction()) {
            return TransferResult.failure("below-min",
                    Map.of("amount", plugin.vault().format(cfg.minTransaction())));
        }
        if (cfg.maxDeposit() > 0 && resolved > cfg.maxDeposit()) {
            return TransferResult.failure("above-max",
                    Map.of("amount", plugin.vault().format(cfg.maxDeposit())));
        }
        if (resolved > wallet + 1e-9) {
            return TransferResult.failure("insufficient-wallet",
                    Map.of("amount", plugin.vault().format(wallet)));
        }
        if (!plugin.vault().takeWallet(player, resolved)) {
            return TransferResult.failure("not-enough-wallet", Map.of());
        }
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());
        acc.add(resolved);
        plugin.storage().log(acc, "DEPOSIT", resolved, "wallet → bank");
        return TransferResult.success(allKeyword ? "deposit-all" : "deposit-success", Map.of(
                "amount", plugin.vault().format(resolved),
                "bank", plugin.vault().format(acc.balance())
        ));
    }

    /** Moves {@code amount} from the bank back into the wallet. */
    public TransferResult withdraw(Player player, double amount, boolean allKeyword) {
        if (!plugin.vault().isEnabled()) {
            return TransferResult.failure("economy-missing", Map.of());
        }
        BankConfig cfg = plugin.bankConfig();
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());
        // "all" must round down: half-up rounding could exceed the balance and fail.
        double resolved = allKeyword ? cfg.floorMoney(amount) : cfg.roundMoney(amount);
        if (resolved < cfg.minTransaction()) {
            return TransferResult.failure("below-min",
                    Map.of("amount", plugin.vault().format(cfg.minTransaction())));
        }
        if (cfg.maxWithdraw() > 0 && resolved > cfg.maxWithdraw()) {
            return TransferResult.failure("above-max",
                    Map.of("amount", plugin.vault().format(cfg.maxWithdraw())));
        }
        if (!acc.subtract(resolved)) {
            return TransferResult.failure("insufficient-bank",
                    Map.of("amount", plugin.vault().format(acc.balance())));
        }
        if (!plugin.vault().giveWallet(player, resolved)) {
            acc.add(resolved);
            return TransferResult.failure("not-enough-bank", Map.of());
        }
        plugin.storage().log(acc, "WITHDRAW", resolved, "bank → wallet");
        return TransferResult.success(allKeyword ? "withdraw-all" : "withdraw-success", Map.of(
                "amount", plugin.vault().format(resolved),
                "bank", plugin.vault().format(acc.balance())
        ));
    }

    /** Convenience wrapper: resolves the {@link Amount} against the source balance first. */
    public TransferResult deposit(Player player, Amount amount) {
        return deposit(player, amount.resolve(plugin.vault().wallet(player)), amount.isAll());
    }

    /** Convenience wrapper: resolves the {@link Amount} against the bank balance first. */
    public TransferResult withdraw(Player player, Amount amount) {
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());
        return withdraw(player, amount.resolve(acc.balance()), amount.isAll());
    }
}
