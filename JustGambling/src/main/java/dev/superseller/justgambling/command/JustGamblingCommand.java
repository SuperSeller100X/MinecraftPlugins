package dev.superseller.justgambling.command;

import dev.superseller.justgambling.JustGamblingPlugin;
import dev.superseller.justgambling.config.Messages;
import dev.superseller.justgambling.config.PluginSettings;
import dev.superseller.justgambling.economy.EconomyService;
import dev.superseller.justgambling.game.GameService;
import dev.superseller.justgambling.gui.GamblingGui;
import dev.superseller.justgambling.model.GameType;
import dev.superseller.justgambling.model.PlayerStats;
import dev.superseller.justgambling.model.RiskTier;
import dev.superseller.justgambling.storage.GamblingStore;
import dev.superseller.justgambling.util.Numbers;
import dev.superseller.justgambling.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
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

/** Main player command and tab completer. */
public final class JustGamblingCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT = List.of("menu", "play", "games", "balance", "history", "stats", "cashout", "help", "reload");
    private static final List<String> PLAY_ALIASES = List.of("play", "p");
    private final JustGamblingPlugin plugin;
    private final PluginSettings settings;
    private final Messages messages;
    private final EconomyService economy;
    private final GamblingStore store;
    private final GameService games;
    private final GamblingGui gui;

    public JustGamblingCommand(JustGamblingPlugin plugin, PluginSettings settings, Messages messages, EconomyService economy,
                               GamblingStore store, GameService games, GamblingGui gui) {
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
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "menu", "gui", "open", "g" -> menu(sender);
            case "play", "p", "bet", "wager" -> play(sender, args);
            case "games", "list", "l" -> games(sender);
            case "balance", "bal", "b", "money" -> balance(sender);
            case "history", "hist", "h", "logs" -> history(sender, args);
            case "stats", "stat", "st" -> stats(sender, args);
            case "cashout", "cash", "co" -> cashout(sender);
            case "help", "?" -> help(sender);
            case "reload", "rl" -> reload(sender);
            default -> {
                messages.send(sender, "unknown-subcommand");
                yield true;
            }
        };
    }

    private boolean menu(CommandSender sender) {
        Player player = player(sender);
        if (player == null || !require(player, "justgambling.use")) {
            return true;
        }
        gui.openMain(player);
        return true;
    }

    private boolean play(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        if (!require(player, "justgambling.play")) {
            return true;
        }
        if (args.length < 2) {
            messages.send(player, "usage-play");
            return true;
        }
        Optional<GameType> parsedGame = GameType.fromId(args[1]);
        if (parsedGame.isEmpty()) {
            messages.send(player, "unknown-game");
            return true;
        }
        GameType game = parsedGame.get();
        if (args.length < 3) {
            gui.openGame(player, game);
            return true;
        }
        Optional<Double> amount = Numbers.parseAmount(args[2], economy.balance(player));
        if (amount.isEmpty()) {
            messages.send(player, "invalid-amount");
            return true;
        }
        RiskTier risk = RiskTier.BALANCED;
        String option = "";
        for (int index = 3; index < args.length; index++) {
            Optional<RiskTier> possibleRisk = RiskTier.parse(args[index]);
            if (possibleRisk.isPresent()) {
                risk = possibleRisk.get();
            } else {
                option = args[index].toLowerCase(Locale.ROOT);
            }
        }
        games.play(player, game, amount.get(), risk, option);
        return true;
    }

    private boolean games(CommandSender sender) {
        if (!require(sender, "justgambling.games")) {
            return true;
        }
        messages.send(sender, "games-header");
        for (GameType game : GameType.values()) {
            messages.send(sender, "games-line", Map.of("game", game.id(), "name", game.displayName(),
                    "status", settings.isGameEnabled(game) ? "<green>enabled" : "<red>disabled"));
        }
        return true;
    }

    private boolean balance(CommandSender sender) {
        Player player = player(sender);
        if (player == null || !require(player, "justgambling.balance")) {
            return true;
        }
        messages.send(player, "balance", Map.of("balance", economy.format(economy.balance(player)),
                "provider", economy.providerName()));
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !require(player, "justgambling.history")) {
            return true;
        }
        int page = args.length > 1 ? Numbers.parseInt(args[1], 1, 10_000).orElse(1) - 1 : 0;
        gui.openHistory(player, page);
        return true;
    }

    private boolean stats(CommandSender sender, String[] args) {
        if (!require(sender, "justgambling.stats")) {
            return true;
        }
        Player viewer = sender instanceof Player player ? player : null;
        OfflinePlayer target = viewer;
        String targetName = viewer == null ? "" : viewer.getName();
        if (args.length > 1) {
            if (!sender.hasPermission("justgambling.stats.others")) {
                messages.send(sender, "no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                target = Bukkit.getOfflinePlayer(args[1]);
            }
            targetName = target.getName() == null ? args[1] : target.getName();
        }
        if (target == null) {
            messages.send(sender, "player-only");
            return true;
        }
        PlayerStats stats = store.stats(target.getUniqueId(), targetName);
        messages.send(sender, "stats", Map.of("player", targetName, "games", stats.games(), "wins", stats.wins(),
                "losses", stats.losses(), "rate", Numbers.format(stats.winRate(), 1) + "%",
                "wagered", economy.format(stats.wagered()), "paid", economy.format(stats.paidOut()),
                "best", economy.format(stats.bestWin())));
        return true;
    }

    private boolean cashout(CommandSender sender) {
        Player player = player(sender);
        if (player != null && require(player, "justgambling.play")) {
            games.cashOut(player);
        }
        return true;
    }

    private boolean help(CommandSender sender) {
        for (String line : messages.list("help")) {
            sender.sendMessage(Text.parse(line));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!require(sender, "justgambling.reload")) {
            return true;
        }
        plugin.reloadEverything();
        messages.send(sender, "reloaded");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(ROOT, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (PLAY_ALIASES.contains(sub) || sub.equals("bet") || sub.equals("wager")) {
            if (args.length == 2) {
                return matching(Arrays.stream(GameType.values()).map(GameType::id).toList(), args[1]);
            }
            if (args.length == 4) {
                Optional<GameType> type = GameType.fromId(args[1]);
                if (type.isPresent() && type.get().needsChoice()) {
                    List<String> suggestions = new ArrayList<>();
                    suggestions.addAll(Arrays.asList(type.get().choices()));
                    suggestions.addAll(Arrays.stream(RiskTier.values()).map(RiskTier::id).toList());
                    return matching(suggestions, args[3]);
                }
                return matching(Arrays.stream(RiskTier.values()).map(RiskTier::id).toList(), args[3]);
            }
            if (args.length >= 5) {
                return matching(Arrays.stream(RiskTier.values()).map(RiskTier::id).toList(), args[args.length - 1]);
            }
        }
        if (sub.equals("history") || sub.equals("hist") || sub.equals("h") || sub.equals("logs")) {
            return List.of("1", "2", "3");
        }
        if (sub.equals("stats") && args.length == 2 && sender.hasPermission("justgambling.stats.others")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList();
        }
        return List.of();
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        messages.send(sender, "player-only");
        return null;
    }

    private boolean require(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "no-permission");
            return false;
        }
        return true;
    }

    private static List<String> matching(List<String> options, String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
