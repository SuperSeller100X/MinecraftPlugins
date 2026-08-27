package dev.superseller.xpbank.command;

import dev.superseller.xpbank.XPBankPlugin;
import dev.superseller.xpbank.bank.BankService;
import dev.superseller.xpbank.bank.TransactionResult;
import dev.superseller.xpbank.config.Messages;
import dev.superseller.xpbank.config.XPBankConfig;
import dev.superseller.xpbank.gui.BankGui;
import dev.superseller.xpbank.scheduler.PlatformScheduler;
import dev.superseller.xpbank.storage.BankStorage;
import dev.superseller.xpbank.util.ExperienceUtil;
import dev.superseller.xpbank.util.Numbers;
import dev.superseller.xpbank.util.Sounds;

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
import org.jetbrains.annotations.NotNull;

/**
 * The player-facing {@code /xpbank} command with sub-commands, short aliases
 * and tab completion. Every action that touches player XP is dispatched to the
 * player's owning thread so it is correct on Paper, Purpur and Folia.
 */
public final class BankCommand implements CommandExecutor, TabCompleter {

    private final XPBankPlugin plugin;
    private final BankService bank;
    private final XPBankConfig config;
    private final Messages messages;
    private final BankGui gui;

    private static final List<String> AMOUNT_SUGGESTIONS = List.of("10", "100", "1000", "all", "half");

    public BankCommand(XPBankPlugin plugin) {
        this.plugin = plugin;
        this.bank = plugin.bank();
        this.config = plugin.bankConfig();
        this.messages = plugin.messages();
        this.gui = plugin.gui();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                openOrBalance(player);
            } else {
                messages.send(sender, "players-only");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.US);
        switch (sub) {
            case "help", "?", "h" -> messages.sendHelp(sender, "player");
            case "balance", "bal", "b" -> balance(sender, args);
            case "deposit", "dep", "d", "store", "in" -> deposit(sender, args);
            case "withdraw", "with", "w", "take", "out" -> withdraw(sender, args);
            case "pay", "send", "transfer", "give", "p" -> pay(sender, args);
            case "top", "leaderboard", "lb" -> top(sender, args);
            case "gui", "menu", "open", "g" -> guiOpen(sender);
            case "reload", "rl" -> reload(sender);
            default -> messages.send(sender, "unknown-subcommand", Map.of("input", args[0]));
        }
        return true;
    }

    private void openOrBalance(Player player) {
        if (player.hasPermission("xpbank.gui")) {
            guiOpen(player);
        } else {
            showBalance(player, player);
        }
    }

    // ---- balance -----------------------------------------------------------

    private void balance(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!sender.hasPermission("xpbank.balance.others")) {
                messages.send(sender, "no-permission");
                return;
            }
            OfflinePlayer target = resolveOffline(args[1]);
            if (target == null || (target.getName() == null && bank.getBalance(target.getUniqueId()) == 0)) {
                messages.send(sender, "player-not-found", Map.of("input", args[1]));
                return;
            }
            long banked = bank.getBalance(target.getUniqueId());
            messages.send(sender, "balance-other", Map.of(
                    "player", target.getName() == null ? args[1] : target.getName(),
                    "banked", Numbers.grouped(banked),
                    "banked_short", Numbers.compact(banked)));
            return;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("xpbank.balance")) {
            messages.send(sender, "no-permission");
            return;
        }
        showBalance(player, player);
    }

    private void showBalance(CommandSender viewer, Player subject) {
        long banked = bank.getBalance(subject.getUniqueId());
        long onHand = ExperienceUtil.getPlayerExp(subject);
        messages.send(viewer, "balance-self", Map.of(
                "banked", Numbers.grouped(banked),
                "banked_short", Numbers.compact(banked),
                "onhand", Numbers.grouped(onHand),
                "onhand_short", Numbers.compact(onHand)));
    }

    // ---- deposit / withdraw ------------------------------------------------

    private void deposit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("xpbank.deposit")) {
            messages.send(sender, "no-permission");
            return;
        }
        long amount = args.length >= 2 ? Numbers.parseAmount(args[1]) : Numbers.ALL;
        if (amount == Numbers.INVALID) {
            messages.send(sender, "invalid-amount", Map.of("input", args[1]));
            Sounds.play(config, player, "error");
            return;
        }
        final long amt = amount;
        PlatformScheduler.runForPlayer(player, () -> {
            TransactionResult result = bank.deposit(player, amt);
            report(player, result, "deposit");
        });
    }

    private void withdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("xpbank.withdraw")) {
            messages.send(sender, "no-permission");
            return;
        }
        long amount = args.length >= 2 ? Numbers.parseAmount(args[1]) : Numbers.ALL;
        if (amount == Numbers.INVALID) {
            messages.send(sender, "invalid-amount", Map.of("input", args[1]));
            Sounds.play(config, player, "error");
            return;
        }
        final long amt = amount;
        PlatformScheduler.runForPlayer(player, () -> {
            TransactionResult result = bank.withdraw(player, amt);
            report(player, result, "withdraw");
        });
    }

    private void report(Player player, TransactionResult result, String type) {
        if (result.ok()) {
            Sounds.play(config, player, type);
            messages.send(player, type + "-success", Map.of(
                    "amount", Numbers.grouped(result.amount()),
                    "amount_short", Numbers.compact(result.amount()),
                    "onhand", Numbers.grouped(result.onHand()),
                    "banked", Numbers.grouped(result.banked())));
        } else {
            Sounds.play(config, player, "error");
            messages.send(player, "error." + result.status().name().toLowerCase(Locale.US),
                    Map.of("amount", Numbers.grouped(result.amount())));
        }
    }

    // ---- pay ---------------------------------------------------------------

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        if (!config.transfersEnabled()) {
            messages.send(sender, "error.disabled");
            return;
        }
        if (!player.hasPermission("xpbank.pay")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "usage-pay");
            return;
        }
        OfflinePlayer target = resolveOffline(args[1]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            messages.send(sender, "player-not-found", Map.of("input", args[1]));
            return;
        }
        long amount = Numbers.parseAmount(args[2]);
        if (amount == Numbers.INVALID) {
            messages.send(sender, "invalid-amount", Map.of("input", args[2]));
            return;
        }
        UUID toId = target.getUniqueId();
        String toName = target.getName() != null ? target.getName() : args[1];
        TransactionResult result = bank.transfer(player, toId, toName, amount);
        if (result.ok()) {
            Sounds.play(config, player, "transfer");
            messages.send(player, "pay-sent", Map.of(
                    "amount", Numbers.grouped(result.amount()),
                    "player", toName,
                    "banked", Numbers.grouped(result.banked())));
            Player online = Bukkit.getPlayer(toId);
            if (online != null) {
                messages.send(online, "pay-received", Map.of(
                        "amount", Numbers.grouped(result.amount()),
                        "player", player.getName(),
                        "banked", Numbers.grouped(bank.getBalance(toId))));
                Sounds.play(config, online, "deposit");
            }
        } else {
            Sounds.play(config, player, "error");
            messages.send(player, "error." + result.status().name().toLowerCase(Locale.US),
                    Map.of("amount", Numbers.grouped(result.amount()), "player", toName));
        }
    }

    // ---- top ---------------------------------------------------------------

    private void top(CommandSender sender, String[] args) {
        if (!config.leaderboardEnabled()) {
            messages.send(sender, "error.disabled");
            return;
        }
        if (!sender.hasPermission("xpbank.top")) {
            messages.send(sender, "no-permission");
            return;
        }
        List<BankStorage.Entry> entries = bank.storage().top(config.leaderboardSize());
        messages.send(sender, "top-header", Map.of("count", String.valueOf(entries.size())));
        if (entries.isEmpty()) {
            messages.send(sender, "top-empty");
            return;
        }
        int rank = 1;
        for (BankStorage.Entry e : entries) {
            String name = e.name() != null ? e.name() : e.uuid().toString().substring(0, 8);
            sender.sendMessage(messages.componentNoPrefix("top-line", Map.of(
                    "rank", String.valueOf(rank),
                    "player", name,
                    "banked", Numbers.grouped(e.amount()),
                    "banked_short", Numbers.compact(e.amount()))));
            rank++;
        }
    }

    private void guiOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("xpbank.gui")) {
            messages.send(sender, "no-permission");
            return;
        }
        PlatformScheduler.runForPlayer(player, () -> {
            gui.open(player);
            Sounds.play(config, player, "gui-open");
        });
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("xpbank.admin.reload")) {
            messages.send(sender, "no-permission");
            return;
        }
        plugin.reloadAll();
        messages.send(sender, "reloaded");
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolveOffline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off;
    }

    // ---- tab completion ----------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("xpbank.balance")) {
                subs.add("balance");
            }
            if (sender.hasPermission("xpbank.deposit")) {
                subs.add("deposit");
            }
            if (sender.hasPermission("xpbank.withdraw")) {
                subs.add("withdraw");
            }
            if (config.transfersEnabled() && sender.hasPermission("xpbank.pay")) {
                subs.add("pay");
            }
            if (config.leaderboardEnabled() && sender.hasPermission("xpbank.top")) {
                subs.add("top");
            }
            if (sender.hasPermission("xpbank.gui")) {
                subs.add("gui");
            }
            if (sender.hasPermission("xpbank.admin.reload")) {
                subs.add("reload");
            }
            subs.add("help");
            return filter(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.US);
        if (args.length == 2) {
            if (isDepositOrWithdraw(sub)) {
                return filter(AMOUNT_SUGGESTIONS, args[1]);
            }
            if (isPay(sub) || isBalance(sub)) {
                return filter(onlinePlayerNames(), args[1]);
            }
        }
        if (args.length == 3 && isPay(sub)) {
            return filter(AMOUNT_SUGGESTIONS, args[2]);
        }
        return List.of();
    }

    private boolean isDepositOrWithdraw(String sub) {
        return switch (sub) {
            case "deposit", "dep", "d", "store", "in", "withdraw", "with", "w", "take", "out" -> true;
            default -> false;
        };
    }

    private boolean isPay(String sub) {
        return switch (sub) {
            case "pay", "send", "transfer", "give", "p" -> true;
            default -> false;
        };
    }

    private boolean isBalance(String sub) {
        return switch (sub) {
            case "balance", "bal", "b" -> true;
            default -> false;
        };
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.US);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.US).startsWith(lower)) {
                out.add(o);
            }
        }
        return out;
    }
}
