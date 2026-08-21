package dev.superseller.playerbank.command;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.config.BankConfig;
import dev.superseller.playerbank.model.BankAccount;
import dev.superseller.playerbank.model.BankLogEntry;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class BankCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final PlayerBankPlugin plugin;

    public BankCommand(PlayerBankPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return showBalance(sender, null);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help" -> {
                plugin.messages().sendHelp(sender);
                yield true;
            }
            case "balance", "bal" -> showBalance(sender, args.length > 1 ? args[1] : null);
            case "deposit", "dep" -> deposit(sender, args);
            case "withdraw", "wd", "take" -> withdraw(sender, args);
            case "interest", "rate" -> interest(sender);
            case "logs", "log", "history" -> logs(sender, args);
            case "refresh", "reload" -> refresh(sender);
            default -> {
                plugin.messages().sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean showBalance(CommandSender sender, String targetName) {
        if (targetName != null) {
            if (!sender.hasPermission("playerbank.balance.others")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            BankAccount acc = plugin.storage().getOrCreate(target.getUniqueId(), targetName);
            plugin.messages().send(sender, "balance-other", Map.of(
                    "player", targetName,
                    "bank", plugin.vault().format(acc.balance())
            ));
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("playerbank.balance")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());
        plugin.messages().send(player, "balance-self", Map.of(
                "bank", plugin.vault().format(acc.balance()),
                "wallet", plugin.vault().format(plugin.vault().wallet(player))
        ));
        return true;
    }

    private boolean deposit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("playerbank.deposit")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (!plugin.vault().isEnabled()) {
            plugin.messages().send(player, "economy-missing");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().sendHelp(sender);
            return true;
        }
        BankConfig cfg = plugin.bankConfig();
        double wallet = plugin.vault().wallet(player);
        Double parsed = parseAmount(args[1], wallet);
        if (parsed == null) {
            plugin.messages().send(player, "invalid-amount");
            return true;
        }
        boolean allKeyword = args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("max");
        // "all" must round down: half-up rounding could exceed the wallet and fail.
        double amount = allKeyword ? cfg.floorMoney(parsed) : cfg.roundMoney(parsed);
        if (amount < cfg.minTransaction()) {
            plugin.messages().send(player, "below-min", Map.of("amount", plugin.vault().format(cfg.minTransaction())));
            return true;
        }
        if (cfg.maxDeposit() > 0 && amount > cfg.maxDeposit()) {
            plugin.messages().send(player, "above-max", Map.of("amount", plugin.vault().format(cfg.maxDeposit())));
            return true;
        }
        if (amount > wallet + 1e-9) {
            plugin.messages().send(player, "insufficient-wallet", Map.of("amount", plugin.vault().format(wallet)));
            return true;
        }
        if (!plugin.vault().takeWallet(player, amount)) {
            plugin.messages().send(player, "not-enough-wallet");
            return true;
        }
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());
        acc.add(amount);
        plugin.storage().log(acc, "DEPOSIT", amount, "wallet → bank");
        String key = "all".equalsIgnoreCase(args[1]) ? "deposit-all" : "deposit-success";
        plugin.messages().send(player, key, Map.of(
                "amount", plugin.vault().format(amount),
                "bank", plugin.vault().format(acc.balance())
        ));
        return true;
    }

    private boolean withdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("playerbank.withdraw")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (!plugin.vault().isEnabled()) {
            plugin.messages().send(player, "economy-missing");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().sendHelp(sender);
            return true;
        }
        BankConfig cfg = plugin.bankConfig();
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());
        Double parsed = parseAmount(args[1], acc.balance());
        if (parsed == null) {
            plugin.messages().send(player, "invalid-amount");
            return true;
        }
        boolean allKeyword = args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("max");
        // "all" must round down: half-up rounding could exceed the balance and fail.
        double amount = allKeyword ? cfg.floorMoney(parsed) : cfg.roundMoney(parsed);
        if (amount < cfg.minTransaction()) {
            plugin.messages().send(player, "below-min", Map.of("amount", plugin.vault().format(cfg.minTransaction())));
            return true;
        }
        if (cfg.maxWithdraw() > 0 && amount > cfg.maxWithdraw()) {
            plugin.messages().send(player, "above-max", Map.of("amount", plugin.vault().format(cfg.maxWithdraw())));
            return true;
        }
        if (!acc.subtract(amount)) {
            plugin.messages().send(player, "insufficient-bank", Map.of("amount", plugin.vault().format(acc.balance())));
            return true;
        }
        if (!plugin.vault().giveWallet(player, amount)) {
            acc.add(amount);
            plugin.messages().send(player, "not-enough-bank");
            return true;
        }
        plugin.storage().log(acc, "WITHDRAW", amount, "bank → wallet");
        String key = "all".equalsIgnoreCase(args[1]) ? "withdraw-all" : "withdraw-success";
        plugin.messages().send(player, key, Map.of(
                "amount", plugin.vault().format(amount),
                "bank", plugin.vault().format(acc.balance())
        ));
        return true;
    }

    private boolean interest(CommandSender sender) {
        if (!sender.hasPermission("playerbank.interest")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        BankConfig cfg = plugin.bankConfig();
        if (!cfg.interestEnabled()) {
            plugin.messages().send(sender, "interest-disabled");
            return true;
        }
        plugin.messages().send(sender, "interest-lookup", Map.of(
                "rate", String.valueOf(cfg.ratePercent()),
                "interval", cfg.intervalDescription(),
                "next", plugin.interest().nextRunHuman()
        ));
        return true;
    }

    private boolean logs(CommandSender sender, String[] args) {
        UUID uuid;
        String name;
        int page = 1;
        if (args.length >= 2 && !isInt(args[1]) && sender.hasPermission("playerbank.logs.others")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            uuid = target.getUniqueId();
            name = args[1];
            if (args.length >= 3 && isInt(args[2])) {
                page = Integer.parseInt(args[2]);
            }
        } else {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "player-only");
                return true;
            }
            if (!player.hasPermission("playerbank.logs")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            uuid = player.getUniqueId();
            name = player.getName();
            if (args.length >= 2 && isInt(args[1])) {
                page = Integer.parseInt(args[1]);
            }
        }
        page = Math.max(1, page);
        BankAccount acc = plugin.storage().getOrCreate(uuid, name);
        int pages = acc.logPages(plugin.bankConfig().logPageSize());
        page = Math.min(page, pages);
        List<BankLogEntry> slice = acc.logPage(page, plugin.bankConfig().logPageSize());
        plugin.messages().send(sender, "logs-header", Map.of(
                "page", String.valueOf(page),
                "pages", String.valueOf(pages),
                "player", name
        ));
        if (slice.isEmpty()) {
            plugin.messages().send(sender, "logs-empty");
            return true;
        }
        for (BankLogEntry e : slice) {
            plugin.messages().send(sender, "logs-line", Map.of(
                    "time", TIME.format(Instant.ofEpochMilli(e.time())),
                    "type", e.type(),
                    "amount", plugin.vault().format(e.amount()),
                    "note", e.note()
            ));
        }
        return true;
    }

    private boolean refresh(CommandSender sender) {
        if (!sender.hasPermission("playerbank.refresh")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        plugin.reloadAll();
        plugin.messages().send(sender, "refresh-ok");
        return true;
    }

    private static Double parseAmount(String raw, double allValue) {
        if (raw == null) {
            return null;
        }
        if (raw.equalsIgnoreCase("all") || raw.equalsIgnoreCase("max")) {
            return allValue;
        }
        try {
            double v = Double.parseDouble(raw.replace(",", ""));
            if (v <= 0 || Double.isNaN(v) || Double.isInfinite(v)) {
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("deposit", "withdraw", "interest", "logs", "refresh", "balance", "help")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("deposit") || args[0].equalsIgnoreCase("withdraw"))) {
            out.add("all");
            out.add("100");
            out.add("1000");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("logs") && sender.hasPermission("playerbank.logs.others")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
