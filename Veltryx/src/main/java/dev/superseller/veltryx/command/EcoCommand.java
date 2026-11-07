package dev.superseller.veltryx.command;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.Account;
import dev.superseller.veltryx.util.Money;
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

/**
 * {@code /eco} — the economy hub: balance, pay, top and (admin) give, take,
 * set and reset.
 */
public final class EcoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("balance", "pay", "top", "give", "take", "set", "reset", "help");

    private final EconomyService economy;
    private final Messages messages;
    private final VeltryxConfig config;

    public EcoCommand(EconomyService economy, Messages messages, VeltryxConfig config) {
        this.economy = economy;
        this.messages = messages;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "balance" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "balance", "bal" -> balance(sender, args);
            case "pay" -> pay(sender, args);
            case "top" -> top(sender);
            case "give", "take", "set", "reset" -> admin(sender, sub, args);
            case "help" -> help(sender, label);
            default -> messages.send(sender, "general.unknown-subcommand",
                    Map.of("input", args[0], "command", "/" + label));
        }
        return true;
    }

    private void balance(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!sender.hasPermission("veltryx.eco.admin")) {
                messages.send(sender, "general.no-permission");
                return;
            }
            OfflinePlayer target = lookupOffline(args[1]);
            if (target == null) {
                messages.send(sender, "general.player-not-found", Map.of("input", args[1]));
                return;
            }
            String name = target.getName() == null ? args[1] : target.getName();
            messages.send(sender, "economy.balance-other",
                    Map.of("player", name, "amount", economy.format(economy.getBalance(target.getUniqueId()))));
            return;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only");
            return;
        }
        if (!sender.hasPermission("veltryx.eco.balance")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        messages.send(player, "economy.balance-self",
                Map.of("amount", economy.format(economy.getBalance(player.getUniqueId()))));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only");
            return;
        }
        if (!sender.hasPermission("veltryx.eco.pay")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "economy.usage-pay");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[1]));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(sender, "economy.pay-self");
            return;
        }
        long amount = Money.parse(args[2]);
        if (!Money.isValid(amount)) {
            messages.send(sender, "general.invalid-amount", Map.of("input", args[2]));
            return;
        }
        if (amount <= 0) {
            messages.send(sender, "general.invalid-amount", Map.of("input", args[2]));
            return;
        }
        if (amount < config.minTransfer()) {
            messages.send(sender, "economy.below-minimum",
                    Map.of("amount", economy.format(config.minTransfer())));
            return;
        }
        boolean ok = economy.transfer(player.getUniqueId(), player.getName(),
                target.getUniqueId(), target.getName(), amount);
        if (!ok) {
            messages.send(sender, "economy.not-enough",
                    Map.of("amount", economy.format(amount)));
            return;
        }
        messages.send(player, "economy.pay-sent",
                Map.of("player", target.getName(), "amount", economy.format(amount)));
        messages.send(target, "economy.pay-received",
                Map.of("player", player.getName(), "amount", economy.format(amount)));
    }

    private void top(CommandSender sender) {
        if (!sender.hasPermission("veltryx.eco.top")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        List<Account> top = economy.top(config.baltopSize());
        messages.send(sender, "economy.top-header", Map.of("count", String.valueOf(top.size())));
        for (int i = 0; i < top.size(); i++) {
            Account account = top.get(i);
            sender.sendMessage(messages.render("economy.top-entry", Map.of(
                    "position", String.valueOf(i + 1),
                    "player", account.name() == null ? "?" : account.name(),
                    "amount", economy.format(account.balance()))));
        }
    }

    private void admin(CommandSender sender, String action, String[] args) {
        if (!sender.hasPermission("veltryx.eco.admin")) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "economy.usage-admin", Map.of("command", "/" + "eco " + action));
            return;
        }
        OfflinePlayer target = lookupOffline(args[1]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[1]));
            return;
        }
        long amount = Money.parse(args[2]);
        if (!Money.isValid(amount)) {
            messages.send(sender, "general.invalid-amount", Map.of("input", args[2]));
            return;
        }
        String name = target.getName() == null ? args[1] : target.getName();
        UUID uuid = target.getUniqueId();
        switch (action) {
            case "give" -> {
                economy.deposit(uuid, name, amount);
                messages.send(sender, "economy.admin-given",
                        Map.of("player", name, "amount", economy.format(amount),
                                "balance", economy.format(economy.getBalance(uuid))));
            }
            case "take" -> {
                boolean ok = economy.withdraw(uuid, name, amount);
                if (!ok) {
                    messages.send(sender, "economy.not-enough", Map.of("amount", economy.format(amount)));
                    return;
                }
                messages.send(sender, "economy.admin-taken",
                        Map.of("player", name, "amount", economy.format(amount),
                                "balance", economy.format(economy.getBalance(uuid))));
            }
            case "set" -> {
                economy.setBalance(uuid, name, amount);
                messages.send(sender, "economy.admin-set",
                        Map.of("player", name, "amount", economy.format(amount)));
            }
            case "reset" -> {
                economy.setBalance(uuid, name, 0L);
                messages.send(sender, "economy.admin-reset", Map.of("player", name));
            }
            default -> {
            }
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(messages.render("economy.help-header"));
        sender.sendMessage(messages.render("economy.help-balance", Map.of("command", "/" + label)));
        if (sender.hasPermission("veltryx.eco.pay")) {
            sender.sendMessage(messages.render("economy.help-pay", Map.of("command", "/" + label)));
        }
        if (sender.hasPermission("veltryx.eco.top")) {
            sender.sendMessage(messages.render("economy.help-top", Map.of("command", "/" + label)));
        }
        if (sender.hasPermission("veltryx.eco.admin")) {
            sender.sendMessage(messages.render("economy.help-admin", Map.of("command", "/" + label)));
        }
    }

    private static OfflinePlayer lookupOffline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() || offline.isOnline() ? offline : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(SUBS);
            if (!sender.hasPermission("veltryx.eco.admin")) {
                options.removeIf(s -> s.equals("give") || s.equals("take")
                        || s.equals("set") || s.equals("reset"));
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            return filter(onlineNames(), args[1]);
        }
        return List.of();
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
