package dev.superseller.xpbank.command;

import dev.superseller.xpbank.XPBankPlugin;
import dev.superseller.xpbank.bank.BankService;
import dev.superseller.xpbank.config.Messages;
import dev.superseller.xpbank.storage.BankStorage;
import dev.superseller.xpbank.util.Numbers;

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
 * Administrative {@code /xpbankadmin} command: inspect, set, add, take and reset
 * any player's banked XP, plus reload and stats. Storage-only operations, so it
 * works for offline players and is Folia-safe.
 */
public final class BankAdminCommand implements CommandExecutor, TabCompleter {

    private final XPBankPlugin plugin;
    private final BankService bank;
    private final Messages messages;

    private static final List<String> SUBS = List.of("set", "add", "take", "reset", "give", "info", "stats", "reload");

    public BankAdminCommand(XPBankPlugin plugin) {
        this.plugin = plugin;
        this.bank = plugin.bank();
        this.messages = plugin.messages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("xpbank.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            messages.sendHelp(sender, "admin");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.US);
        switch (sub) {
            case "reload", "rl" -> {
                plugin.reloadAll();
                messages.send(sender, "reloaded");
            }
            case "stats" -> stats(sender);
            case "info", "check" -> info(sender, args);
            case "set" -> mutate(sender, args, Mode.SET);
            case "add", "give" -> mutate(sender, args, Mode.ADD);
            case "take", "remove" -> mutate(sender, args, Mode.TAKE);
            case "reset", "clear" -> reset(sender, args);
            default -> messages.send(sender, "unknown-subcommand", Map.of("input", args[0]));
        }
        return true;
    }

    private enum Mode { SET, ADD, TAKE }

    private void stats(CommandSender sender) {
        BankStorage storage = bank.storage();
        messages.send(sender, "admin.stats", Map.of(
                "accounts", String.valueOf(storage.all().size()),
                "total", Numbers.grouped(storage.totalStored()),
                "total_short", Numbers.compact(storage.totalStored()),
                "storage", plugin.bankConfig().storageType()));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "admin.usage-info");
            return;
        }
        OfflinePlayer target = resolveOffline(args[1]);
        UUID id = target.getUniqueId();
        long banked = bank.getBalance(id);
        messages.send(sender, "admin.info", Map.of(
                "player", target.getName() != null ? target.getName() : args[1],
                "uuid", id.toString(),
                "banked", Numbers.grouped(banked),
                "banked_short", Numbers.compact(banked)));
    }

    private void mutate(CommandSender sender, String[] args, Mode mode) {
        if (args.length < 3) {
            messages.send(sender, "admin.usage-" + mode.name().toLowerCase(Locale.US));
            return;
        }
        OfflinePlayer target = resolveOffline(args[1]);
        long amount = Numbers.parseAmount(args[2]);
        if (amount == Numbers.INVALID || amount == Numbers.ALL || amount == Numbers.HALF) {
            messages.send(sender, "invalid-amount", Map.of("input", args[2]));
            return;
        }
        UUID id = target.getUniqueId();
        String name = target.getName();
        long result = switch (mode) {
            case SET -> bank.adminSet(id, name, amount);
            case ADD -> bank.adminAdd(id, name, amount);
            case TAKE -> bank.adminTake(id, name, amount);
        };
        messages.send(sender, "admin." + mode.name().toLowerCase(Locale.US) + "-done", Map.of(
                "player", name != null ? name : args[1],
                "amount", Numbers.grouped(amount),
                "banked", Numbers.grouped(result)));
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "admin.usage-reset");
            return;
        }
        OfflinePlayer target = resolveOffline(args[1]);
        bank.adminSet(target.getUniqueId(), target.getName(), 0L);
        messages.send(sender, "admin.reset-done", Map.of(
                "player", target.getName() != null ? target.getName() : args[1]));
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolveOffline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayer(name);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("xpbank.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.US);
        if (args.length == 2 && !sub.equals("reload") && !sub.equals("stats")) {
            return filter(onlinePlayerNames(), args[1]);
        }
        if (args.length == 3 && (sub.equals("set") || sub.equals("add") || sub.equals("give")
                || sub.equals("take") || sub.equals("remove"))) {
            return filter(List.of("10", "100", "1000"), args[2]);
        }
        return List.of();
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
