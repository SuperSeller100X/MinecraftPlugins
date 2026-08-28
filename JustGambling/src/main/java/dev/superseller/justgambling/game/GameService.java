package dev.superseller.justgambling.game;

import dev.superseller.justgambling.config.Messages;
import dev.superseller.justgambling.config.PluginSettings;
import dev.superseller.justgambling.economy.EconomyService;
import dev.superseller.justgambling.gui.GamblingGui;
import dev.superseller.justgambling.model.GameType;
import dev.superseller.justgambling.model.MinesSession;
import dev.superseller.justgambling.model.RiskProfile;
import dev.superseller.justgambling.model.RiskTier;
import dev.superseller.justgambling.model.Transaction;
import dev.superseller.justgambling.scheduler.PlatformScheduler;
import dev.superseller.justgambling.storage.GamblingStore;
import dev.superseller.justgambling.util.Numbers;
import dev.superseller.justgambling.util.Sounds;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.security.SecureRandom;
import java.util.Random;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Single source of truth for wager validation, game outcomes and settlement.
 * All public methods are safe to call from a player's region thread.
 */
public final class GameService {
    private static final Set<Integer> RED_NUMBERS = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    private static final int MINES_SIZE = 25;

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final Messages messages;
    private final EconomyService economy;
    private final GamblingStore store;
    private final Sounds sounds;
    private volatile Random random;
    private final ConcurrentHashMap<UUID, Long> lastWager = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PendingWager> pendingWagers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, MinesSession> activeMines = new ConcurrentHashMap<>();
    private volatile GamblingGui gui;

    public GameService(JavaPlugin plugin, PluginSettings settings, Messages messages, EconomyService economy,
                       GamblingStore store, Sounds sounds) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.economy = economy;
        this.store = store;
        this.sounds = sounds;
        this.random = settings.useSecureRandom() ? new SecureRandom() : new Random();
    }

    public void setGui(GamblingGui gui) {
        this.gui = gui;
    }

    public void reload() {
        random = settings.useSecureRandom() ? new SecureRandom() : new Random();
    }

    public boolean hasActiveMines(UUID playerId) {
        return playerId != null && activeMines.containsKey(playerId);
    }

    public int activeMinesCount() {
        return activeMines.size();
    }

    public boolean hasPendingWager(UUID playerId) {
        return playerId != null && pendingWagers.containsKey(playerId);
    }

    public int pendingWagerCount() {
        return pendingWagers.size();
    }

    /** Called by the animation GUI once its result has been shown. */
    public void finishAnimated(Player player) {
        if (player == null) {
            return;
        }
        PendingWager pending = pendingWagers.remove(player.getUniqueId());
        if (pending != null) {
            settlePending(player, pending, true);
        }
    }

    public void play(Player player, GameType game, double stake, RiskTier risk, String option) {
        if (player == null || game == null) {
            return;
        }
        if (hasPendingWager(player.getUniqueId())) {
            messages.send(player, "wager-pending");
            return;
        }
        if (!player.hasPermission("justgambling.play") || !player.hasPermission("justgambling.play." + game.id())) {
            messages.send(player, "no-permission");
            return;
        }
        if (!settings.isGameEnabled(game)) {
            messages.send(player, "game-disabled", Map.of("game", game.displayName()));
            return;
        }
        if (hasActiveMines(player.getUniqueId())) {
            messages.send(player, "active-mines");
            return;
        }
        if (game == GameType.MINES) {
            startMines(player, stake, risk == null ? RiskTier.BALANCED : risk);
            return;
        }
        if (!validateWager(player, stake)) {
            return;
        }
        RiskTier selectedRisk = risk == null ? RiskTier.BALANCED : risk;
        String selectedOption = option == null ? "" : option.trim().toLowerCase(Locale.ROOT);
        if (!validOption(game, selectedOption)) {
            messages.send(player, "invalid-option", Map.of("game", game.displayName()));
            return;
        }
        Outcome outcome = resolve(game, stake, selectedRisk, selectedOption);
        if (outcome == null) {
            messages.send(player, "invalid-option", Map.of("game", game.displayName()));
            return;
        }

        double estimatedPayout = payoutFor(stake, outcome.multiplier,
                outcome.jackpotPool ? store.jackpotPool() : 0.0);
        if (!validPayout(estimatedPayout) || (!outcome.win && !validContribution(stake, game))) {
            messages.send(player, "payout-too-large");
            return;
        }
        if (!economy.withdraw(player, stake)) {
            messages.send(player, "not-enough-money", Map.of("balance", economy.format(economy.balance(player))));
            return;
        }

        PendingWager pending = new PendingWager(player.getUniqueId(), player.getName(), game, stake, outcome);
        if (pendingWagers.putIfAbsent(player.getUniqueId(), pending) != null) {
            // The entity scheduler normally serializes this already, but keep the
            // settlement boundary safe if another integration calls play directly.
            economy.deposit(player, stake);
            messages.send(player, "wager-pending");
            return;
        }
        lastWager.put(player.getUniqueId(), System.currentTimeMillis());

        if (gui != null && settings.animationsEnabled()) {
            try {
                gui.animate(player, game, selectedRisk, selectedOption, outcome.details(), outcome.win());
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("JustGambling animation could not start: " + exception.getMessage());
                finishAnimated(player);
            }
        } else {
            finishAnimated(player);
        }
    }

    public void startMines(Player player, double stake, RiskTier risk) {
        if (player == null) {
            return;
        }
        if (!settings.isGameEnabled(GameType.MINES)) {
            messages.send(player, "game-disabled", Map.of("game", GameType.MINES.displayName()));
            return;
        }
        if (hasActiveMines(player.getUniqueId())) {
            messages.send(player, "active-mines");
            return;
        }
        if (!validateWager(player, stake)) {
            return;
        }
        RiskTier selectedRisk = risk == null ? RiskTier.BALANCED : risk;
        int mineCount = Math.min(MINES_SIZE - 1, settings.mineCount(selectedRisk));
        double maximumMultiplier = 1.0 + (MINES_SIZE - mineCount) * settings.mineStep(selectedRisk);
        if (!validPayout(payoutFor(stake, maximumMultiplier, 0.0))
                || !validContribution(stake, GameType.MINES)) {
            messages.send(player, "payout-too-large");
            return;
        }
        if (!economy.withdraw(player, stake)) {
            messages.send(player, "not-enough-money", Map.of("balance", economy.format(economy.balance(player))));
            return;
        }
        boolean[] mines = new boolean[MINES_SIZE];
        int placed = 0;
        while (placed < mineCount) {
            int index = nextInt(MINES_SIZE);
            if (!mines[index]) {
                mines[index] = true;
                placed++;
            }
        }
        MinesSession session = new MinesSession(player.getUniqueId(), stake, selectedRisk, mines);
        if (activeMines.putIfAbsent(player.getUniqueId(), session) != null) {
            economy.deposit(player, stake);
            messages.send(player, "active-mines");
            return;
        }
        lastWager.put(player.getUniqueId(), System.currentTimeMillis());
        messages.send(player, "mines-start", Map.of("stake", economy.format(stake), "risk", selectedRisk.displayName(),
                "mines", mineCount));
        sounds.play(player, "start", "BLOCK_NOTE_BLOCK_PLING");
        if (gui != null) {
            gui.openMines(player, session);
        }
    }

    public void revealMine(Player player, int index) {
        if (player == null) {
            return;
        }
        MinesSession session = activeMines.get(player.getUniqueId());
        if (session == null) {
            messages.send(player, "no-active-mines");
            return;
        }
        if (index < 0 || index >= session.size()) {
            return;
        }
        synchronized (session) {
            if (session.resolved() || session.isRevealed(index)) {
                return;
            }
            session.reveal(index);
            if (session.isMine(index)) {
                session.markResolved();
                activeMines.remove(player.getUniqueId(), session);
                double contribution = session.stake() * (contributionPercent(GameType.MINES) / 100.0);
                if (Double.isFinite(contribution) && contribution > 0.0) {
                    store.addToJackpot(contribution);
                }
                store.record(new Transaction(UUID.randomUUID(), player.getUniqueId(), player.getName(), GameType.MINES,
                        session.stake(), 0.0, false, "Mine hit after " + session.safeRevealed() + " safe tile(s)", Instant.now()));
                store.saveAsync();
                messages.send(player, "mines-hit", Map.of("safe", session.safeRevealed()));
                sounds.play(player, "lose", "ENTITY_VILLAGER_NO");
                if (gui != null) {
                    gui.closeAfterResult(player);
                }
                return;
            }
            sounds.play(player, "reveal", "BLOCK_GLASS_BREAK");
            if (session.allSafeRevealed()) {
                cashOutInternal(player, session, false);
                if (gui != null) {
                    gui.closeAfterResult(player);
                }
            } else if (gui != null) {
                gui.updateMines(player, session);
            }
            messages.send(player, "mines-safe", Map.of("safe", session.safeRevealed(),
                    "multiplier", Numbers.format(currentMinesMultiplier(session), 2)));
        }
    }

    public void cashOut(Player player) {
        if (player == null) {
            return;
        }
        MinesSession session = activeMines.get(player.getUniqueId());
        if (session == null) {
            messages.send(player, "no-active-mines");
            return;
        }
        synchronized (session) {
            if (!session.resolved()) {
                cashOutInternal(player, session, false);
            }
        }
    }

    /** Closing a board is a safe cash-out, never a silent loss. */
    public void closeMines(Player player) {
        if (player == null) {
            return;
        }
        MinesSession session = activeMines.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        synchronized (session) {
            if (!session.resolved()) {
                cashOutInternal(player, session, true);
            }
        }
    }

    public void onQuit(Player player) {
        if (player == null) {
            return;
        }
        // Resolve a wager that is mid-animation rather than letting a quit
        // become a free retry or a lost stake.
        finishPending(player.getUniqueId(), player, false);

        MinesSession session = activeMines.get(player.getUniqueId());
        if (session != null) {
            synchronized (session) {
                if (!session.resolved()) {
                    cashOutInternal(player, session, true);
                }
            }
        }
    }

    public void shutdown() {
        for (PendingWager pending : new ArrayList<>(pendingWagers.values())) {
            if (pendingWagers.remove(pending.playerId(), pending)) {
                settlePending(null, pending, false);
            }
        }
        for (MinesSession session : new ArrayList<>(activeMines.values())) {
            if (!session.resolved()) {
                session.markResolved();
                activeMines.remove(session.playerId(), session);
                if (!economy.deposit(session.playerId(), session.stake())) {
                    plugin.getLogger().severe("Could not refund active Mines stake for " + session.playerId());
                }
            }
        }
    }

    public double currentMinesMultiplier(MinesSession session) {
        if (session == null) {
            return 1.0;
        }
        return Math.max(1.0, 1.0 + session.safeRevealed() * settings.mineStep(session.risk()));
    }

    public String providerName() {
        return economy.providerName();
    }

    public String format(double amount) {
        return economy.format(amount);
    }

    private void cashOutInternal(Player player, MinesSession session, boolean silent) {
        session.markResolved();
        activeMines.remove(player.getUniqueId(), session);
        double multiplier = currentMinesMultiplier(session);
        double payout = payoutFor(session.stake(), multiplier, 0.0);
        if (!validPayout(payout) || !economy.deposit(player, payout)) {
            // The current stake is the minimum safe refund if a large payout or
            // provider operation cannot be completed.
            economy.deposit(player, session.stake());
            plugin.getLogger().severe("Mines payout failed for " + player.getName() + "; stake refunded.");
            if (!silent) {
                messages.send(player, "payout-failed");
            }
            return;
        }
        Transaction transaction = new Transaction(UUID.randomUUID(), player.getUniqueId(), player.getName(), GameType.MINES,
                session.stake(), payout, payout >= session.stake(),
                "Cashed out after " + session.safeRevealed() + " safe tile(s)", Instant.now());
        store.record(transaction);
        store.saveAsync();
        if (!silent) {
            messages.send(player, "mines-cashout", Map.of("payout", economy.format(payout),
                    "multiplier", Numbers.format(multiplier, 2), "safe", session.safeRevealed()));
            sounds.play(player, "cashout", "ENTITY_EXPERIENCE_ORB_PICKUP");
        }
    }

    private void finishPending(UUID playerId, Player player, boolean notify) {
        PendingWager pending = pendingWagers.remove(playerId);
        if (pending != null) {
            settlePending(player, pending, notify);
        }
    }

    private void settlePending(Player player, PendingWager pending, boolean notify) {
        Outcome outcome = pending.outcome();
        double payout = 0.0;
        double claimedJackpot = 0.0;
        if (outcome.win()) {
            if (outcome.jackpotPool()) {
                claimedJackpot = store.claimJackpot(settings.jackpotSeed());
                payout = payoutFor(pending.stake(), outcome.multiplier(), claimedJackpot);
            } else {
                payout = payoutFor(pending.stake(), outcome.multiplier(), 0.0);
            }
            if (!validPayout(payout) || (payout > 0.0 && !deposit(pending.playerId(), player, payout))) {
                boolean refunded = deposit(pending.playerId(), player, pending.stake());
                if (claimedJackpot > 0.0) {
                    store.restoreJackpot(claimedJackpot);
                }
                plugin.getLogger().severe("Could not settle a JustGambling payout for " + pending.playerName()
                        + "; stake refund success: " + refunded + ".");
                if (notify && player != null && player.isOnline()) {
                    messages.send(player, "payout-failed");
                }
                return;
            }
        } else {
            double contribution = pending.stake() * (contributionPercent(pending.game()) / 100.0);
            if (Double.isFinite(contribution) && contribution > 0.0) {
                store.addToJackpot(contribution);
            }
        }

        Transaction transaction = new Transaction(UUID.randomUUID(), pending.playerId(), pending.playerName(),
                pending.game(), pending.stake(), payout, outcome.win(), outcome.details(), Instant.now());
        store.record(transaction);
        store.saveAsync();
        if (notify && player != null && player.isOnline()) {
            sendOutcome(player, transaction, outcome);
            if (gui != null) {
                gui.closeAfterResult(player);
            }
        }
    }

    private boolean deposit(UUID playerId, Player player, double amount) {
        return player != null && player.isOnline()
                ? economy.deposit(player, amount) : economy.deposit(playerId, amount);
    }

    private boolean validateWager(Player player, double stake) {
        if (hasPendingWager(player.getUniqueId())) {
            messages.send(player, "wager-pending");
            return false;
        }
        if (!economy.available()) {
            messages.send(player, "economy-unavailable");
            return false;
        }
        if (!Double.isFinite(stake) || stake <= 0.0 || stake < settings.minimumStake()) {
            messages.send(player, "invalid-stake", Map.of("minimum", economy.format(settings.minimumStake())));
            return false;
        }
        if (settings.maximumStake() > 0.0 && stake > settings.maximumStake()) {
            messages.send(player, "stake-too-high", Map.of("maximum", economy.format(settings.maximumStake())));
            return false;
        }
        long now = System.currentTimeMillis();
        Long previous = lastWager.get(player.getUniqueId());
        long cooldown = settings.cooldownMillis();
        if (cooldown > 0L && previous != null && now - previous < cooldown) {
            double seconds = (cooldown - (now - previous)) / 1000.0;
            messages.send(player, "cooldown", Map.of("seconds", Numbers.format(seconds, 1)));
            return false;
        }
        double balance = economy.balance(player);
        if (!Double.isFinite(balance) || balance + 1.0e-9 < stake) {
            messages.send(player, "not-enough-money", Map.of("balance", economy.format(balance)));
            return false;
        }
        return true;
    }

    private boolean validPayout(double payout) {
        if (!Double.isFinite(payout) || payout < 0.0) {
            return false;
        }
        return settings.maximumPayout() <= 0.0 || payout <= settings.maximumPayout();
    }

    private boolean validContribution(double stake, GameType game) {
        double contribution = stake * (contributionPercent(game) / 100.0);
        if (!Double.isFinite(contribution) || contribution < 0.0) {
            return false;
        }
        return contribution <= Double.MAX_VALUE - store.jackpotPool();
    }

    private double payoutFor(double stake, double multiplier, double jackpotPool) {
        if (!Double.isFinite(stake) || !Double.isFinite(multiplier) || multiplier < 0.0) {
            return Double.NaN;
        }
        double payout = stake * multiplier;
        if (jackpotPool > payout) {
            payout = jackpotPool;
        }
        if (!Double.isFinite(payout) || payout < 0.0) {
            return Double.NaN;
        }
        return Numbers.roundMoney(payout, settings.decimalPlaces());
    }

    private Outcome resolve(GameType game, double stake, RiskTier risk, String option) {
        RiskProfile profile = settings.risk(risk);
        double chance = profile.chance();
        return switch (game) {
            case COINFLIP -> {
                String side = nextInt(2) == 0 ? "Heads" : "Tails";
                boolean win = random.nextDouble() < chance;
                yield new Outcome(win, profile.multiplier(), side + " — " + risk.displayName() + " risk", false);
            }
            case DICE -> {
                int roll = nextInt(100) + 1;
                boolean win = roll <= Math.round(chance * 100.0);
                yield new Outcome(win, profile.multiplier(), "Rolled " + roll + "/100 (needed "
                        + Math.round(chance * 100.0) + " or lower)", false);
            }
            case ROULETTE -> resolveRoulette(profile, option);
            case WHEEL -> resolveWheel(profile);
            case HIGHLOW -> resolveHighLow(profile, option);
            case SLOTS -> resolveSlots(profile);
            case SCRATCH -> {
                boolean win = random.nextDouble() < chance;
                String symbols = win ? "★ ★ ★" : "✦ ○ ✧";
                yield new Outcome(win, profile.multiplier(), "Scratch: " + symbols, false);
            }
            case CRASH -> resolveCrash(option);
            case JACKPOT -> {
                boolean win = random.nextDouble() < settings.jackpotChance();
                yield new Outcome(win, Math.max(profile.multiplier(), settings.jackpotMultiplier()),
                        win ? "The house jackpot hit" : "The jackpot did not trigger", win);
            }
            case LOTTERY -> resolveLottery(option, profile);
            case DOUBLE_OR_NOTHING -> {
                boolean win = random.nextDouble() < chance;
                yield new Outcome(win, Math.max(2.0, profile.multiplier()),
                        win ? "Double-or-nothing succeeded" : "Double-or-nothing failed", false);
            }
            case MINES -> null;
        };
    }

    private Outcome resolveWheel(RiskProfile profile) {
        int winningSlices = Math.max(1, Math.min(7, (int) Math.round(profile.chance() * 8.0)));
        int slice = nextInt(8);
        boolean win = slice < winningSlices;
        return new Outcome(win, profile.multiplier(), "Wheel landed on slice " + (slice + 1)
                + " (" + (win ? "WIN" : "MISS") + ")", false);
    }

    private Outcome resolveRoulette(RiskProfile profile, String option) {
        String selected = option.isBlank() ? "red" : option;
        int number = nextInt(37);
        String colour = number == 0 ? "green" : RED_NUMBERS.contains(number) ? "red" : "black";
        double multiplier;
        boolean win;
        if (selected.equals("green")) {
            multiplier = settings.rouletteGreenPayout();
            win = colour.equals("green");
        } else if (selected.equals("black")) {
            multiplier = settings.rouletteRedBlackPayout();
            win = colour.equals("black");
        } else {
            multiplier = settings.rouletteRedBlackPayout();
            selected = "red";
            win = colour.equals("red");
        }
        return new Outcome(win, multiplier, "Roulette: " + number + " (" + colour + "), picked " + selected, false);
    }

    private Outcome resolveHighLow(RiskProfile profile, String option) {
        String selected = option.equals("low") ? "low" : "high";
        int roll = nextInt(100) + 1;
        int window = Math.max(1, (int) Math.round(profile.chance() * 100.0));
        boolean win = selected.equals("high") ? roll > 100 - window : roll <= window;
        return new Outcome(win, profile.multiplier(), "High/Low rolled " + roll + ", picked " + selected, false);
    }

    private Outcome resolveSlots(RiskProfile profile) {
        String[] symbols = {"🍒", "🔔", "💎", "7"};
        boolean win = random.nextDouble() < profile.chance();
        String symbol = symbols[nextInt(symbols.length)];
        String reels = win ? symbol + " " + symbol + " " + symbol
                : symbols[nextInt(symbols.length)] + " " + symbols[nextInt(symbols.length)] + " " + symbol;
        return new Outcome(win, profile.multiplier(), "Slots: " + reels, false);
    }

    private Outcome resolveCrash(String option) {
        double target = Numbers.parseDouble(option.isBlank() ? "2.0" : option, 1.01, 1000.0).orElse(2.0);
        double randomValue = Math.max(0.0, Math.min(0.999999, random.nextDouble()));
        double crashPoint = 1.0 / Math.max(0.01, 1.0 - randomValue);
        crashPoint = Math.max(1.0, Numbers.roundMoney(crashPoint * (1.0 - settings.crashHouseEdge()), 2));
        crashPoint = Math.min(1000.0, crashPoint);
        boolean win = crashPoint >= target;
        return new Outcome(win, target, "Crash reached " + Numbers.format(crashPoint, 2) + "x; target "
                + Numbers.format(target, 2) + "x", false);
    }

    private Outcome resolveLottery(String option, RiskProfile profile) {
        int picked = Numbers.parseInt(option.isBlank() ? "1" : option, 1, 10).orElse(1);
        int drawn = nextInt(10) + 1;
        boolean win = picked == drawn;
        return new Outcome(win, settings.lotteryMultiplier(), "Lucky number drew " + drawn + "; picked " + picked, false);
    }

    private boolean validOption(GameType game, String option) {
        if (!game.needsChoice() || option.isBlank()) {
            return true;
        }
        return switch (game) {
            case ROULETTE -> option.equals("red") || option.equals("black") || option.equals("green");
            case HIGHLOW -> option.equals("high") || option.equals("low");
            case CRASH -> Numbers.parseDouble(option, 1.01, 1000.0).isPresent();
            case LOTTERY -> Numbers.parseInt(option, 1, 10).isPresent();
            default -> true;
        };
    }

    private double contributionPercent(GameType game) {
        return game == GameType.LOTTERY ? settings.lotteryContribution() : settings.jackpotContribution();
    }

    private void sendOutcome(Player player, Transaction transaction, Outcome outcome) {
        if (transaction.win()) {
            messages.send(player, "game-win", Map.of("game", transaction.game().displayName(),
                    "stake", economy.format(transaction.stake()), "payout", economy.format(transaction.payout()),
                    "net", economy.format(transaction.net()), "details", transaction.details()));
            sounds.play(player, outcome.jackpotPool ? "jackpot" : "win", "ENTITY_PLAYER_LEVELUP");
            if (outcome.jackpotPool || (settings.broadcastBigWins() && transaction.payout() >= settings.broadcastThreshold())) {
                Component announcement = messages.rawComponent("announcement", Map.of(
                        "player", player.getName(), "game", transaction.game().displayName(),
                        "payout", economy.format(transaction.payout())));
                PlatformScheduler.runGlobal(() -> Bukkit.broadcast(announcement));
            }
        } else {
            messages.send(player, "game-loss", Map.of("game", transaction.game().displayName(),
                    "stake", economy.format(transaction.stake()), "details", transaction.details()));
            sounds.play(player, "lose", "ENTITY_VILLAGER_NO");
        }
    }

    private int nextInt(int bound) {
        return random.nextInt(bound);
    }

    private record PendingWager(UUID playerId, String playerName, GameType game, double stake, Outcome outcome) {
    }

    private record Outcome(boolean win, double multiplier, String details, boolean jackpotPool) {
    }
}
