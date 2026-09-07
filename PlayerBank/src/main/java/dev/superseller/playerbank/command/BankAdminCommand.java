package dev.superseller.playerbank.command;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.model.BankAccount;
import dev.superseller.playerbank.util.MoneyAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class BankAdminCommand implements CommandExecutor, TabCompleter {

    private final PlayerBankPlugin plugin;

    public BankAdminCommand(PlayerBankPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playerbank.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.messages().deserialize(
                    "<gold>/bankadmin <reload|set|give|take|forceinterest>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("playerbank.admin.reload")) {
                    plugin.messages().send(sender, "no-permission");
                    yield true;
                }
                plugin.reloadAll();
                plugin.messages().send(sender, "reload-ok");
                yield true;
            }
            case "forceinterest", "tick" -> {
                if (!sender.hasPermission("playerbank.admin.forceinterest")) {
                    plugin.messages().send(sender, "no-permission");
                    yield true;
                }
                int n = plugin.interest().tick();
                plugin.messages().send(sender, "interest-forced", Map.of("count", String.valueOf(n)));
                yield true;
            }
            case "set", "give", "take" -> mutate(sender, sub, args);
            default -> {
                sender.sendMessage(plugin.messages().deserialize(
                        "<gold>/bankadmin <reload|set|give|take|forceinterest>"));
                yield true;
            }
        };
    }

    private boolean mutate(CommandSender sender, String sub, String[] args) {
        String perm = "playerbank.admin." + sub;
        if (!sender.hasPermission(perm)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.messages().deserialize("<red>Usage: /bankadmin " + sub + " <player> <amount>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        Double parsed = MoneyAmount.parse(args[2]);
        if (parsed == null) {
            plugin.messages().send(sender, "invalid-amount");
            return true;
        }
        double amount = parsed;
        amount = plugin.bankConfig().roundMoney(amount);
        BankAccount acc = plugin.storage().getOrCreate(target.getUniqueId(), args[1]);
        switch (sub) {
            case "set" -> {
                acc.balance(amount);
                plugin.storage().log(acc, "ADMIN_SET", amount, "by " + sender.getName());
                plugin.messages().send(sender, "admin-set", Map.of(
                        "player", args[1],
                        "amount", plugin.vault().format(amount)
                ));
            }
            case "give" -> {
                acc.add(amount);
                plugin.storage().log(acc, "ADMIN_GIVE", amount, "by " + sender.getName());
                plugin.messages().send(sender, "admin-give", Map.of(
                        "player", args[1],
                        "amount", plugin.vault().format(amount),
                        "bank", plugin.vault().format(acc.balance())
                ));
            }
            case "take" -> {
                acc.subtract(amount);
                plugin.storage().log(acc, "ADMIN_TAKE", amount, "by " + sender.getName());
                plugin.messages().send(sender, "admin-take", Map.of(
                        "player", args[1],
                        "amount", plugin.vault().format(amount),
                        "bank", plugin.vault().format(acc.balance())
                ));
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("reload", "set", "give", "take", "forceinterest")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("forceinterest")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
