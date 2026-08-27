package dev.superseller.justgambling.command;

import dev.superseller.justgambling.JustGamblingPlugin;
import dev.superseller.justgambling.config.Messages;
import dev.superseller.justgambling.config.PluginSettings;
import dev.superseller.justgambling.economy.EconomyService;
import dev.superseller.justgambling.game.GameService;
import dev.superseller.justgambling.gui.GamblingGui;
import dev.superseller.justgambling.model.GameType;
import dev.superseller.justgambling.model.PlayerStats;
import dev.superseller.justgambling.model.RiskProfile;
import dev.superseller.justgambling.model.RiskTier;
import dev.superseller.justgambling.storage.GamblingStore;
import dev.superseller.justgambling.util.Numbers;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Administrative command for diagnostics, rules and fallback balances. */
public final class JustGamblingAdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT = List.of("reload", "status", "diagnostics", "rules", "limits", "enable", "disable", "set", "give", "take", "refund", "recover", "history", "stats", "reset", "pool");
    private final JustGamblingPlugin plugin;
    private final PluginSettings settings;
    private final Messages messages;
    private final EconomyService economy;
    private final GamblingStore store;
    private final GameService games;
    private final GamblingGui gui;

    public JustGamblingAdminCommand(JustGamblingPlugin plugin, PluginSettings settings, Messages messages,
                                    EconomyService economy, GamblingStore store, GameService games, GamblingGui gui) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.economy = economy;
        this.store = store;
        this.games = games;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("justgambling.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload", "rl" -> reload(sender);
            case "status", "st", "info", "i", "diagnostics", "diag" -> status(sender);
            case "rules", "rule", "limits", "limit" -> rules(sender);
            case "enable", "on" -> toggle(sender, args, true);
            case "disable", "off" -> toggle(sender, args, false);
            case "set" -> adjust(sender, args, "set");
            case "give", "add" -> adjust(sender, args, "give");
            case "take", "remove" -> adjust(sender, args, "take");
            case "refund", "recover", "reimburse" -> refund(sender, args);
            case "history", "hist", "logs", "h" -> history(sender, args);
            case "stats", "stat" -> stats(sender, args);
            case "reset", "clear" -> reset(sender, args);
            case "pool", "jackpot", "jp" -> pool(sender, args);
            default -> {
                messages.send(sender, "unknown-admin-subcommand");
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("justgambling.admin.reload")) {
            messages.send(sender, "no-permission");
            return true;
        }
        plugin.reloadEverything();
        messages.send(sender, "reloaded");
        return true;
    }

    private boolean status(CommandSender sender) {
        if (!sender.hasPermission("justgambling.admin.diagnostics")) {
            messages.send(sender, "no-permission");
            return true;
        }
        messages.send(sender, "admin-status", Map.of(
                "provider", economy.providerName(),
                "folia", dev.superseller.justgambling.scheduler.PlatformScheduler.isFoliaSchedulerAvailable(),
                "jackpot", economy.format(store.jackpotPool()),
                "active", games.activeMinesCount()));
        return true;
    }

    private boolean rules(CommandSender sender) {
        if (!sender.hasPermission("justgambling.admin.rules")) {
            messages.send(sender, "no-permission");
            return true;
        }
        messages.send(sender, "admin-rules", Map.of(
                "minimum", economy.format(settings.minimumStake()),
                "maximum", limit(settings.maximumStake()),
                "payout", limit(settings.maximumPayout()),
                "cooldown", Numbers.format(settings.cooldownMillis() / 1000.0, 1)));
        for (Map.Entry<RiskTier, RiskProfile> entry : settings.riskProfiles().entrySet()) {
            messages.send(sender, "admin-risk", Map.of(
                    "risk", entry.getKey().displayName(),
                    "chance", Numbers.format(entry.getValue().chance() * 100.0, 2),
                    "multiplier", Numbers.format(entry.getValue().multiplier(), 2)));
        }
        return true;
    }

    private boolean refund(CommandSender sender, String[] args) {
        if (!sender.hasPermission("justgambling.admin.refund")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "admin-refund-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(args[1]);
        }
        Optional<Double> amount = Numbers.parsePositive(args[2]);
        if (amount.isEmpty()) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        String targetName = target.getName() == null ? args[1] : target.getName();
        if (!economy.deposit(target, amount.get())) {
            messages.send(sender, "admin-refund-failed", Map.of("player", targetName));
            return true;
        }
        plugin.getLogger().info("Admin " + sender.getName() + " issued a recovery refund of "
                + economy.format(amount.get()) + " to " + targetName + ".");
        store.saveAsync();
        messages.send(sender, "admin-refunded", Map.of("player", targetName,
                "amount", economy.format(amount.get())));
        return true;
    }

    private boolean stats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("justgambling.admin.stats")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "admin-player-required");
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(args[1]);
        }
        String name = target.getName() == null ? args[1] : target.getName();
        PlayerStats playerStats = store.stats(target.getUniqueId(), name);
        messages.send(sender, "stats", Map.of("player", name, "games", playerStats.games(), "wins", playerStats.wins(),
                "losses", playerStats.losses(), "rate", Numbers.format(playerStats.winRate(), 1) + "%",
                "wagered", economy.format(playerStats.wagered()), "paid", economy.format(playerStats.paidOut()),
                "best", economy.format(playerStats.bestWin())));
        return true;
    }

    private boolean toggle(CommandSender sender, String[] args, boolean enabled) {
        if (!sender.hasPermission("justgambling.admin.games")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2 || GameType.fromId(args[1]).isEmpty()) {
            messages.send(sender, "admin-game-required");
            return true;
        }
        GameType game = GameType.fromId(args[1]).get();
        plugin.getConfig().set("games." + game.id() + ".enabled", enabled);
        plugin.saveConfig();
        plugin.reloadEverything();
        messages.send(sender, enabled ? "game-enabled" : "game-disabled", Map.of("game", game.displayName()));
        return true;
    }

    private boolean adjust(CommandSender sender, String[] args, String mode) {
        if (!sender.hasPermission("justgambling.admin.balance")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "admin-balance-usage");
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(args[1]);
        }
        Optional<Double> amount = Numbers.parsePositive(args[2]);
        if (mode.equals("set") && args[2].trim().equals("0")) {
            amount = Optional.of(0.0);
        }
        if (amount.isEmpty()) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        double current = store.fallbackBalance(target.getUniqueId(), target.getName());
        double updated;
        if (mode.equals("set")) {
            updated = store.setFallback(target.getUniqueId(), target.getName(), amount.get());
        } else if (mode.equals("give")) {
            if (!store.creditFallback(target.getUniqueId(), target.getName(), amount.get())) {
                messages.send(sender, "amount-overflow");
                return true;
            }
            updated = current + amount.get();
        } else {
            if (amount.get() > current || !store.debitFallback(target.getUniqueId(), target.getName(), amount.get())) {
                messages.send(sender, "admin-balance-too-low", Map.of("player", target.getName() == null ? args[1] : target.getName()));
                return true;
            }
            updated = current - amount.get();
        }
        store.saveAsync();
        messages.send(sender, "admin-balance-updated", Map.of("player", target.getName() == null ? args[1] : target.getName(),
                "balance", economy.format(updated)));
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        if (!sender.hasPermission("justgambling.admin.history")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "admin-player-required");
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(args[1]);
        }
        int page = args.length > 2 ? Numbers.parseInt(args[2], 1, 10_000).orElse(1) - 1 : 0;
        gui.openHistory(player, target.getUniqueId(), target.getName() == null ? args[1] : target.getName(), page, true);
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("justgambling.admin.reset")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "admin-player-required");
            return true;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(args[1]);
        }
        store.resetAccount(target.getUniqueId());
        store.saveAsync();
        messages.send(sender, "admin-reset", Map.of("player", target.getName() == null ? args[1] : target.getName()));
        return true;
    }

    private boolean pool(CommandSender sender, String[] args) {
        if (!sender.hasPermission("justgambling.admin.pool")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 1 || args[1].equalsIgnoreCase("info")) {
            messages.send(sender, "admin-pool", Map.of("pool", economy.format(store.jackpotPool())));
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "admin-pool-usage");
            return true;
        }
        Optional<Double> amount = Numbers.parsePositive(args[2]);
        if (args[1].equalsIgnoreCase("set") && args[2].trim().equals("0")) {
            amount = Optional.of(0.0);
        }
        if (amount.isEmpty()) {
            messages.send(sender, "invalid-amount");
            return true;
        }
        double value;
        if (args[1].equalsIgnoreCase("set")) {
            store.restoreJackpot(amount.get());
            value = amount.get();
        } else if (args[1].equalsIgnoreCase("add")) {
            value = store.addToJackpot(amount.get());
        } else {
            messages.send(sender, "admin-pool-usage");
            return true;
        }
        store.saveAsync();
        messages.send(sender, "admin-pool", Map.of("pool", economy.format(value)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(ROOT, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ((sub.equals("enable") || sub.equals("disable") || sub.equals("on") || sub.equals("off")) && args.length == 2) {
            return matching(java.util.Arrays.stream(GameType.values()).map(GameType::id).toList(), args[1]);
        }
        if ((sub.equals("set") || sub.equals("give") || sub.equals("take") || sub.equals("add") || sub.equals("remove")
                || sub.equals("refund") || sub.equals("recover") || sub.equals("reimburse")
                || sub.equals("history") || sub.equals("hist") || sub.equals("logs") || sub.equals("reset") || sub.equals("clear")
                || sub.equals("stats") || sub.equals("stat")) && args.length == 2) {
            return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList(), args[1]);
        }
        if (sub.equals("pool") || sub.equals("jackpot") || sub.equals("jp")) {
            if (args.length == 2) {
                return matching(List.of("info", "set", "add"), args[1]);
            }
        }
        return List.of();
    }

    private static String limit(double value) {
        return value <= 0.0 ? "unlimited" : Numbers.format(value);
    }

    private static List<String> matching(List<String> options, String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
