package dev.superseller.justgambling.gui;

import dev.superseller.justgambling.config.Messages;
import dev.superseller.justgambling.config.PluginSettings;
import dev.superseller.justgambling.economy.EconomyService;
import dev.superseller.justgambling.game.GameService;
import dev.superseller.justgambling.input.InputManager;
import dev.superseller.justgambling.model.GameType;
import dev.superseller.justgambling.model.MinesSession;
import dev.superseller.justgambling.model.RiskProfile;
import dev.superseller.justgambling.model.RiskTier;
import dev.superseller.justgambling.model.Transaction;
import dev.superseller.justgambling.scheduler.PlatformScheduler;
import dev.superseller.justgambling.storage.GamblingStore;
import dev.superseller.justgambling.util.ItemBuilder;
import dev.superseller.justgambling.util.Sounds;
import dev.superseller.justgambling.util.Numbers;
import dev.superseller.justgambling.util.Text;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** All player-facing inventory menus. */
public final class GamblingGui {
    private static final Set<Integer> RED_NUMBERS = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    private static final int[] GAME_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34};
    private static final int[] RISK_SLOTS = {19, 21, 23, 25};
    private static final int[] AMOUNT_SLOTS = {28, 30, 32, 34};
    private static final int[] MINE_SLOTS = {
            10, 11, 12, 13, 14,
            19, 20, 21, 22, 23,
            28, 29, 30, 31, 32,
            37, 38, 39, 40, 41,
            46, 47, 48, 49, 50
    };
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PluginSettings settings;
    private final Messages messages;
    private final EconomyService economy;
    private final GamblingStore store;
    private final GameService games;
    private final InputManager input;
    private final Sounds sounds;

    public GamblingGui(PluginSettings settings, Messages messages, EconomyService economy, GamblingStore store,
                       GameService games, InputManager input, Sounds sounds) {
        this.settings = settings;
        this.messages = messages;
        this.economy = economy;
        this.store = store;
        this.games = games;
        this.input = input;
        this.sounds = sounds;
    }

    public void openMain(Player player) {
        if (player == null) {
            return;
        }
        Map<Integer, GameType> slots = new LinkedHashMap<>();
        GameType[] types = GameType.values();
        for (int index = 0; index < Math.min(GAME_SLOTS.length, types.length); index++) {
            slots.put(GAME_SLOTS[index], types[index]);
        }
        GamblingHolder holder = new GamblingHolder(GamblingHolder.Kind.MAIN, player.getUniqueId(), null, null,
                null, null, 0, false, slots);
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.parse(settings.guiMainTitle()));
        holder.inventory(inventory);
        fill(inventory, ItemBuilder.pane(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>"));
        inventory.setItem(4, ItemBuilder.item(Material.EMERALD, "<green>Your balance</green>", List.of(
                "<gray>Available: <white>" + economy.format(economy.balance(player)) + "</white>",
                "<gray>Provider: <white>" + economy.providerName() + "</white>")));
        for (Map.Entry<Integer, GameType> entry : slots.entrySet()) {
            GameType game = entry.getValue();
            boolean enabled = settings.isGameEnabled(game);
            inventory.setItem(entry.getKey(), ItemBuilder.item(enabled ? game.icon() : Material.BARRIER,
                    (enabled ? "<gold>" : "<dark_red>") + game.displayName() + (enabled ? "</gold>" : "</dark_red>"),
                    List.of("<gray>" + game.description() + "</gray>", enabled
                            ? "<yellow>Click to configure a wager.</yellow>"
                            : "<red>Disabled by an administrator.</red>")));
        }
        inventory.setItem(49, ItemBuilder.item(Material.BOOK, "<aqua>History</aqua>",
                List.of("<gray>Review your recent wagers.</gray>", "<yellow>Click to open.</yellow>")));
        inventory.setItem(53, ItemBuilder.item(Material.BARRIER, "<red>Close</red>", List.of()));
        player.openInventory(inventory);
    }

    public void openGame(Player player, GameType game) {
        openGame(player, game, RiskTier.BALANCED, "");
    }

    public void openGame(Player player, GameType game, RiskTier risk, String option) {
        if (player == null || game == null) {
            return;
        }
        GamblingHolder holder = new GamblingHolder(GamblingHolder.Kind.GAME, player.getUniqueId(), null, game,
                risk == null ? RiskTier.BALANCED : risk, option == null ? "" : option, 0, false, Map.of());
        String title = settings.guiGameTitle().replace("{game}", game.displayName());
        Inventory inventory = Bukkit.createInventory(holder, 45, Text.parse(title));
        holder.inventory(inventory);
        fill(inventory, ItemBuilder.pane(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>"));

        RiskProfile profile = settings.risk(holder.risk());
        inventory.setItem(4, ItemBuilder.item(game.icon(), "<gold>" + game.displayName() + "</gold>", List.of(
                "<gray>" + game.description() + "</gray>",
                "<gray>Selected risk: <white>" + holder.risk().displayName() + "</white>",
                "<gray>Chance: <white>" + Numbers.format(profile.chance() * 100.0, 1) + "%</white>",
                "<gray>Win payout: <white>" + Numbers.format(profile.multiplier(), 2) + "x</white>")));

        for (int index = 0; index < RISK_SLOTS.length; index++) {
            RiskTier tier = RiskTier.values()[index];
            RiskProfile tierProfile = settings.risk(tier);
            boolean selected = tier == holder.risk();
            inventory.setItem(RISK_SLOTS[index], ItemBuilder.item(selected ? Material.LIME_DYE : Material.GRAY_DYE,
                    (selected ? "<green>" : "<yellow>") + tier.displayName() + (selected ? "</green>" : "</yellow>"),
                    List.of("<gray>Chance: <white>" + Numbers.format(tierProfile.chance() * 100.0, 1) + "%</white>",
                            "<gray>Payout: <white>" + Numbers.format(tierProfile.multiplier(), 2) + "x</white>",
                            "<yellow>Click to select.</yellow>")));
        }

        if (game.needsChoice()) {
            String selected = holder.option().isBlank() ? "Not selected" : holder.option().toUpperCase(Locale.ROOT);
            inventory.setItem(13, ItemBuilder.item(Material.COMPASS, "<aqua>Choose your play</aqua>", List.of(
                    "<gray>Current choice: <white>" + selected + "</white>",
                    "<yellow>Click to choose.</yellow>")));
        }

        List<Double> presets = settings.amountPresets();
        for (int index = 0; index < Math.min(AMOUNT_SLOTS.length, presets.size()); index++) {
            double amount = presets.get(index);
            inventory.setItem(AMOUNT_SLOTS[index], ItemBuilder.item(Material.GOLD_INGOT,
                    "<gold>Stake " + economy.format(amount) + "</gold>",
                    List.of("<gray>Use the selected risk and choice.</gray>", "<yellow>Click to play.</yellow>")));
        }
        inventory.setItem(37, ItemBuilder.item(Material.ANVIL, "<aqua>Custom stake</aqua>", List.of(
                "<gray>Type any finite amount, including 1k or all.</gray>", "<yellow>Click to open input.</yellow>")));
        inventory.setItem(39, ItemBuilder.item(Material.OAK_SIGN, "<aqua>Chat stake</aqua>", List.of(
                "<gray>Close the menu and type the amount in chat.</gray>", "<yellow>Click to begin.</yellow>")));
        inventory.setItem(44, ItemBuilder.item(Material.ARROW, "<yellow>Back</yellow>", List.of()));
        player.openInventory(inventory);
    }

    public void openChoice(Player player, GameType game, RiskTier risk) {
        if (player == null || game == null) {
            return;
        }
        GamblingHolder holder = new GamblingHolder(GamblingHolder.Kind.CHOICE, player.getUniqueId(), null, game,
                risk == null ? RiskTier.BALANCED : risk, "", 0, false, Map.of());
        String title = settings.guiChoiceTitle().replace("{game}", game.displayName());
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.parse(title));
        holder.inventory(inventory);
        fill(inventory, ItemBuilder.pane(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>"));
        String[] choices = game.choices();
        for (int index = 0; index < choices.length; index++) {
            String choice = choices[index];
            inventory.setItem(10 + index, ItemBuilder.item(choiceMaterial(game, choice),
                    "<gold>" + choice.toUpperCase(Locale.ROOT) + "</gold>",
                    List.of("<gray>Use this choice with " + risk.displayName() + " risk.</gray>",
                            "<yellow>Click to select.</yellow>")));
        }
        inventory.setItem(49, ItemBuilder.item(Material.ARROW, "<yellow>Back</yellow>", List.of()));
        player.openInventory(inventory);
    }

    public void openAnvil(Player player, GameType game, RiskTier risk, String option) {
        if (player == null || game == null) {
            return;
        }
        GamblingHolder holder = new GamblingHolder(GamblingHolder.Kind.ANVIL, player.getUniqueId(), null, game,
                risk, option, 0, false, Map.of());
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.ANVIL, Text.parse(settings.guiAnvilTitle()));
        holder.inventory(inventory);
        inventory.setItem(0, ItemBuilder.item(Material.PAPER, "<yellow>Type amount</yellow>",
                List.of("<gray>Rename this item, then click the result.</gray>")));
        player.openInventory(inventory);
    }

    /** Opens a non-clickable, per-game animation and settles only after the result frame. */
    public void animate(Player player, GameType game, RiskTier risk, String option, String details, boolean win) {
        if (player == null || game == null || !games.hasPendingWager(player.getUniqueId())) {
            return;
        }
        RiskTier selectedRisk = risk == null ? RiskTier.BALANCED : risk;
        String selectedOption = option == null ? "" : option;
        GamblingHolder holder = new GamblingHolder(GamblingHolder.Kind.ANIMATION, player.getUniqueId(), null, game,
                selectedRisk, selectedOption, 0, false, Map.of());
        String title = settings.guiAnimationTitle().replace("{game}", game.displayName());
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.parse(title));
        holder.inventory(inventory);
        fill(inventory, ItemBuilder.pane(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>"));
        int frames = settings.animationFrames(game);
        renderAnimationFrame(inventory, game, selectedRisk, selectedOption, details, win, 0, frames, false);
        player.openInventory(inventory);
        scheduleAnimation(player, holder, game, selectedRisk, selectedOption, details, win, 1, frames);
    }

    private void scheduleAnimation(Player player, GamblingHolder holder, GameType game, RiskTier risk, String option,
                                    String details, boolean win, int frame, int totalFrames) {
        PlatformScheduler.runEntityDelayed(player, () -> {
            if (!games.hasPendingWager(player.getUniqueId())) {
                return;
            }
            if (player.getOpenInventory().getTopInventory().getHolder() != holder) {
                // Closing an animation settles the already-determined result;
                // it cannot be used to retry or cancel a wager.
                games.finishAnimated(player);
                return;
            }
            boolean resultFrame = frame >= totalFrames;
            renderAnimationFrame(player.getOpenInventory().getTopInventory(), game, risk, option, details, win,
                    frame, totalFrames, resultFrame);
            if (!resultFrame && frame % 2 == 0) {
                sounds.play(player, "click", "UI_BUTTON_CLICK");
            }
            if (resultFrame) {
                PlatformScheduler.runEntityDelayed(player, () -> games.finishAnimated(player),
                        settings.animationResultPauseTicks());
            } else {
                scheduleAnimation(player, holder, game, risk, option, details, win, frame + 1, totalFrames);
            }
        }, animationDelay(frame, totalFrames));
    }

    private long animationDelay(int frame, int totalFrames) {
        long base = settings.animationStepTicks();
        double progress = totalFrames <= 0 ? 1.0 : frame / (double) totalFrames;
        if (progress <= 0.65) {
            return base;
        }
        long deceleration = Math.min(8L, Math.max(1L, Math.round((progress - 0.65) * 12.0)));
        return base + deceleration;
    }

    private void renderAnimationFrame(Inventory inventory, GameType game, RiskTier risk, String option,
                                      String details, boolean win, int frame, int totalFrames, boolean resultFrame) {
        String state = resultFrame
                ? (win ? "<green>WIN!</green>" : "<red>HOUSE WINS</red>")
                : "<yellow>Resolving…</yellow>";
        String visibleDetails = resultFrame ? details : "Outcome hidden until the animation stops.";
        inventory.setItem(4, ItemBuilder.item(resultFrame ? (win ? Material.EMERALD : Material.REDSTONE_BLOCK) : Material.CLOCK,
                state, List.of("<gray>" + visibleDetails + "</gray>", resultFrame
                        ? "<white>The result is locked.</white>"
                        : "<gray>One wager, one server-side result.</gray>")));
        inventory.setItem(49, ItemBuilder.item(resultFrame ? (win ? Material.EMERALD : Material.BARRIER) : Material.HOPPER,
                resultFrame ? "<gold>Result shown</gold>" : "<aqua>House game in progress</aqua>",
                List.of("<gray>Risk: <white>" + risk.displayName() + "</white></gray>",
                        "<gray>Stake: <white>settling</white></gray>")));

        switch (game) {
            case WHEEL -> renderWheel(inventory, settings.risk(risk), details, frame, resultFrame);
            case ROULETTE -> renderRoulette(inventory, details, frame, resultFrame);
            case SLOTS -> renderSlots(inventory, frame, resultFrame, win);
            case SCRATCH -> renderScratch(inventory, frame, totalFrames, resultFrame, win);
            case COINFLIP -> renderCoinFlip(inventory, details, frame, resultFrame);
            case DICE -> renderDice(inventory, details, frame, resultFrame);
            case HIGHLOW -> renderHighLow(inventory, option, details, frame, resultFrame);
            case CRASH -> renderCrash(inventory, details, frame, resultFrame, win);
            case JACKPOT -> renderJackpot(inventory, frame, resultFrame, win);
            case LOTTERY -> renderLottery(inventory, details, frame, resultFrame);
            case DOUBLE_OR_NOTHING -> renderDouble(inventory, frame, resultFrame, win);
            case MINES -> {
                // Mines has its own interactive board and never enters this path.
            }
        }
    }

    private void renderWheel(Inventory inventory, RiskProfile profile, String details, int frame, boolean resultFrame) {
        // Eight slices rotate around a fixed pointer so this reads as a wheel,
        // rather than another horizontal result line.
        int[] slots = {20, 21, 22, 29, 31, 38, 39, 40};
        int winningSlices = Math.max(1, Math.min(7, (int) Math.round(profile.chance() * 8.0)));
        String[] labels = new String[slots.length];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = index < winningSlices ? "WIN x" + Numbers.format(profile.multiplier(), 2) : "MISS";
        }
        Material[] materials = {Material.LIME_WOOL, Material.LIME_WOOL, Material.GOLD_BLOCK, Material.GOLD_BLOCK,
                Material.RED_WOOL, Material.RED_WOOL, Material.BLACK_WOOL, Material.BLACK_WOOL};
        int landed = resultFrame ? extractInt(details, "slice ", -1) - 1 : -1;
        for (int index = 0; index < slots.length; index++) {
            int labelIndex = landed >= 0 ? Math.floorMod(landed + index, labels.length)
                    : Math.floorMod(frame + index, labels.length);
            boolean selected = index == 0;
            inventory.setItem(slots[index], ItemBuilder.item(selected ? Material.NETHER_STAR : materials[labelIndex],
                    (selected ? "<gold>▶ " : labels[labelIndex].startsWith("WIN") ? "<green>" : "<red>")
                            + labels[labelIndex] + (selected ? " ◀</gold>" : labels[labelIndex].startsWith("WIN") ? "</green>" : "</red>"),
                    List.of(selected ? "<yellow>Pointer</yellow>" : "<dark_gray>Spinning slice</dark_gray>")));
        }
        inventory.setItem(30, ItemBuilder.item(resultFrame ? Material.NETHER_STAR : Material.COMPASS,
                resultFrame ? "<gold>Wheel stopped</gold>" : "<aqua>Wheel spinning…</aqua>",
                List.of("<gray>Winning slices: <white>" + winningSlices + "/8</white></gray>")));
    }

    private void renderRoulette(Inventory inventory, String details, int frame, boolean resultFrame) {
        int[] slots = {20, 21, 22, 23, 24, 25, 26, 27, 28};
        int landed = resultFrame ? extractInt(details, "Roulette: ", -1) : -1;
        for (int index = 0; index < slots.length; index++) {
            int number = landed >= 0 ? Math.floorMod(landed + index - 4, 37) : Math.floorMod(frame + index, 37);
            boolean red = RED_NUMBERS.contains(number);
            Material material = number == 0 ? Material.LIME_WOOL : red ? Material.RED_WOOL : Material.BLACK_WOOL;
            boolean selected = index == slots.length / 2;
            inventory.setItem(slots[index], ItemBuilder.item(selected ? Material.GOLD_BLOCK : material,
                    (selected ? "<gold>▶ " : "<white>") + number + (selected ? " ◀</gold>" : "</white>"),
                    List.of(selected ? "<yellow>Pointer</yellow>" : "<gray>Roulette track</gray>")));
        }
        inventory.setItem(31, ItemBuilder.item(resultFrame ? Material.NETHER_STAR : Material.COMPASS,
                resultFrame ? "<gold>Roulette stopped</gold>" : "<aqua>Roulette spinning…</aqua>",
                List.of("<gray>Green is zero.</gray>")));
    }

    private void renderSlots(Inventory inventory, int frame, boolean resultFrame, boolean win) {
        String[] symbols = {"🍒", "🔔", "💎", "7"};
        Material[] materials = {Material.RED_DYE, Material.BELL, Material.DIAMOND, Material.GOLD_INGOT};
        int[] slots = {20, 22, 24};
        for (int index = 0; index < slots.length; index++) {
            int symbol = resultFrame && win ? 3 : Math.floorMod(frame + index * 2, symbols.length);
            inventory.setItem(slots[index], ItemBuilder.item(resultFrame && win ? Material.DIAMOND_BLOCK : materials[symbol],
                    resultFrame && win ? "<green>7</green>" : "<gold>" + symbols[symbol] + "</gold>",
                    List.of(resultFrame ? "<gray>Reel locked.</gray>" : "<yellow>Reel spinning…</yellow>")));
        }
        inventory.setItem(31, ItemBuilder.item(Material.JUKEBOX,
                resultFrame ? (win ? "<green>Triple match!</green>" : "<red>No match</red>") : "<aqua>Three reels spinning…</aqua>",
                List.of("<gray>Each reel stops in sequence.</gray>")));
    }

    private void renderScratch(Inventory inventory, int frame, int totalFrames, boolean resultFrame, boolean win) {
        int[] slots = {19, 21, 23, 28, 30, 32, 37, 39, 41};
        int revealed = resultFrame ? slots.length : Math.min(slots.length,
                Math.max(0, (frame * slots.length) / Math.max(1, totalFrames)));
        for (int index = 0; index < slots.length; index++) {
            boolean open = index < revealed;
            Material material = !open ? Material.PAPER : resultFrame ? (win ? Material.GOLD_INGOT : Material.COAL)
                    : Material.GRAY_STAINED_GLASS_PANE;
            String label = !open ? "<yellow>?</yellow>" : resultFrame
                    ? (win ? "<gold>★</gold>" : "<gray>○</gray>") : "<white>•</white>";
            inventory.setItem(slots[index], ItemBuilder.item(material, label,
                    List.of(open && resultFrame ? "<gray>Revealed.</gray>" : "<yellow>Scratch to reveal.</yellow>")));
        }
    }

    private void renderCoinFlip(Inventory inventory, String details, int frame, boolean resultFrame) {
        boolean heads = resultFrame ? !details.toLowerCase(Locale.ROOT).contains("tails") : frame % 2 == 0;
        inventory.setItem(22, ItemBuilder.item(heads ? Material.GOLD_INGOT : Material.IRON_INGOT,
                heads ? "<gold>HEADS</gold>" : "<gray>TAILS</gray>",
                List.of(resultFrame ? "<gray>Coin landed.</gray>" : "<yellow>Flipping…</yellow>")));
    }

    private void renderDice(Inventory inventory, String details, int frame, boolean resultFrame) {
        int roll = resultFrame ? extractInt(details, "Rolled ", 100) : Math.floorMod(frame * 17 + 7, 100) + 1;
        inventory.setItem(22, ItemBuilder.item(Material.BONE, "<white>Roll: " + roll + "/100</white>",
                List.of(resultFrame ? "<gray>Dice stopped.</gray>" : "<yellow>Rolling…</yellow>")));
    }

    private void renderHighLow(Inventory inventory, String option, String details, int frame, boolean resultFrame) {
        boolean high = resultFrame ? details.toLowerCase(Locale.ROOT).contains("picked high") : frame % 2 == 0;
        if (resultFrame && !details.toLowerCase(Locale.ROOT).contains("picked high")
                && !details.toLowerCase(Locale.ROOT).contains("picked low")) {
            high = option.equalsIgnoreCase("high");
        }
        inventory.setItem(22, ItemBuilder.item(high ? Material.SPECTRAL_ARROW : Material.ARROW,
                high ? "<gold>HIGH</gold>" : "<aqua>LOW</aqua>",
                List.of(resultFrame ? "<gray>Prediction locked.</gray>" : "<yellow>Choosing a side…</yellow>")));
    }

    private void renderCrash(Inventory inventory, String details, int frame, boolean resultFrame, boolean win) {
        double multiplier = resultFrame ? extractDouble(details, "Crash reached ", win ? 3.0 : 1.0)
                : 1.0 + frame * 0.25;
        inventory.setItem(22, ItemBuilder.item(resultFrame && !win ? Material.TNT : Material.FIREWORK_ROCKET,
                (resultFrame && !win ? "<red>CRASHED</red>" : "<gold>" + Numbers.format(multiplier, 2) + "x</gold>"),
                List.of(resultFrame ? "<gray>Crash point locked.</gray>" : "<yellow>Climbing…</yellow>")));
    }

    private void renderJackpot(Inventory inventory, int frame, boolean resultFrame, boolean win) {
        int[] slots = {20, 21, 22, 23, 24, 25, 26, 27, 28};
        Material[] materials = {Material.DIAMOND, Material.EMERALD, Material.GOLD_BLOCK, Material.AMETHYST_BLOCK,
                Material.NETHER_STAR, Material.DIAMOND, Material.EMERALD, Material.GOLD_BLOCK, Material.AMETHYST_BLOCK};
        for (int index = 0; index < slots.length; index++) {
            int materialIndex = resultFrame ? (win ? 4 : 0) : Math.floorMod(frame + index, materials.length);
            inventory.setItem(slots[index], ItemBuilder.item(materials[materialIndex],
                    resultFrame ? (win ? "<gold>JACKPOT!</gold>" : "<gray>Not this time</gray>") : "<aqua>✦</aqua>",
                    List.of(resultFrame ? "<gray>Jackpot result.</gray>" : "<yellow>Chasing the house pool…</yellow>")));
        }
    }

    private void renderLottery(Inventory inventory, String details, int frame, boolean resultFrame) {
        int[] slots = {20, 21, 22, 23, 24};
        int drawn = resultFrame ? extractInt(details, "drew ", 1) : -1;
        for (int index = 0; index < slots.length; index++) {
            int number = resultFrame ? (index == 2 ? drawn : index + 1)
                    : Math.floorMod(frame + index * 3, 10) + 1;
            inventory.setItem(slots[index], ItemBuilder.item(Material.EMERALD,
                    "<green>" + number + "</green>",
                    List.of(resultFrame ? "<gray>Drawn number.</gray>" : "<yellow>Bouncing ball…</yellow>")));
        }
    }

    private void renderDouble(Inventory inventory, int frame, boolean resultFrame, boolean win) {
        boolean doubleDown = resultFrame ? win : frame % 2 == 0;
        inventory.setItem(22, ItemBuilder.item(doubleDown ? Material.GOLD_BLOCK : Material.CRYING_OBSIDIAN,
                resultFrame ? (win ? "<green>DOUBLED</green>" : "<red>LOST</red>") : "<gold>DOUBLE?</gold>",
                List.of(resultFrame ? "<gray>Risk resolved.</gray>" : "<yellow>Flipping the house coin…</yellow>")));
    }

    private static int extractInt(String text, String marker, int fallback) {
        if (text == null || marker == null) {
            return fallback;
        }
        int start = text.indexOf(marker);
        if (start < 0) {
            return fallback;
        }
        String tail = text.substring(start + marker.length()).trim();
        int end = 0;
        while (end < tail.length() && Character.isDigit(tail.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return fallback;
        }
        try {
            return Integer.parseInt(tail.substring(0, end));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double extractDouble(String text, String marker, double fallback) {
        if (text == null || marker == null) {
            return fallback;
        }
        int start = text.indexOf(marker);
        if (start < 0) {
            return fallback;
        }
        String tail = text.substring(start + marker.length()).trim();
        int end = 0;
        while (end < tail.length()) {
            char character = tail.charAt(end);
            if (!Character.isDigit(character) && character != '.' && character != '-') {
                break;
            }
            end++;
        }
        if (end == 0) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(tail.substring(0, end));
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public void openHistory(Player player, int page) {
        if (player != null) {
            openHistory(player, player.getUniqueId(), player.getName(), page, false);
        }
    }

    public void openHistory(Player viewer, UUID target, String targetName, int page, boolean admin) {
        if (viewer == null || target == null) {
            return;
        }
        List<Transaction> transactions = store.history(target, targetName, settings.historySize());
        openHistoryInventory(viewer, target, targetName, page, admin, transactions);
    }

    private void openHistoryInventory(Player viewer, UUID target, String targetName, int page, boolean admin,
                                      List<Transaction> transactions) {
        int pageSize = 34;
        int maxPage = Math.max(0, (transactions.size() - 1) / pageSize);
        int safePage = Math.max(0, Math.min(page, maxPage));
        GamblingHolder holder = new GamblingHolder(GamblingHolder.Kind.HISTORY, viewer.getUniqueId(), target, null,
                null, null, safePage, admin, Map.of());
        String title = settings.guiHistoryTitle().replace("{page}", String.valueOf(safePage + 1));
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.parse(title));
        holder.inventory(inventory);
        fill(inventory, ItemBuilder.pane(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>"));
        int start = safePage * pageSize;
        for (int offset = 0; offset < pageSize && start + offset < transactions.size(); offset++) {
            Transaction transaction = transactions.get(start + offset);
            String result = transaction.win() ? "<green>WIN</green>" : "<red>LOSS</red>";
            inventory.setItem(10 + offset, ItemBuilder.item(transaction.win() ? Material.EMERALD : Material.REDSTONE,
                    result + " <white>" + transaction.game().displayName() + "</white>", List.of(
                            "<gray>Stake: <white>" + economy.format(transaction.stake()) + "</white>",
                            "<gray>Payout: <white>" + economy.format(transaction.payout()) + "</white>",
                            "<gray>Net: <white>" + economy.format(transaction.net()) + "</white>",
                            "<gray>When: <white>" + TIME_FORMAT.format(transaction.timestamp()) + "</white>",
                            "<dark_gray>Click for details in chat.</dark_gray>")));
        }
        if (safePage > 0) {
            inventory.setItem(45, ItemBuilder.item(Material.ARROW, "<yellow>Previous page</yellow>", List.of()));
        }
        if (safePage < maxPage) {
            inventory.setItem(53, ItemBuilder.item(Material.ARROW, "<yellow>Next page</yellow>", List.of()));
        }
        inventory.setItem(49, ItemBuilder.item(Material.BARRIER, "<red>Close</red>", List.of()));
        viewer.openInventory(inventory);
    }

    public void openMines(Player player, MinesSession session) {
        if (player == null || session == null) {
            return;
        }
        GamblingHolder holder = new GamblingHolder(GamblingHolder.Kind.MINES, player.getUniqueId(), null,
                GameType.MINES, session.risk(), "", 0, false, Map.of());
        String title = settings.guiMinesTitle().replace("{risk}", session.risk().displayName());
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.parse(title));
        holder.inventory(inventory);
        fill(inventory, ItemBuilder.pane(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>"));
        inventory.setItem(4, ItemBuilder.item(Material.TNT, "<red>Mines</red>", List.of(
                "<gray>Stake: <white>" + economy.format(session.stake()) + "</white>",
                "<gray>Safe tiles: <white>" + session.safeRevealed() + "</white>",
                "<gray>Current multiplier: <white>" + Numbers.format(games.currentMinesMultiplier(session), 2) + "x</white>",
                "<yellow>Reveal tiles, then cash out.</yellow>")));
        inventory.setItem(52, ItemBuilder.item(Material.GOLD_INGOT, "<gold>Cash out</gold>", List.of(
                "<gray>Take the current multiplier.</gray>", "<yellow>Click at any time.</yellow>")));
        player.openInventory(inventory);
        updateMines(player, session);
    }

    public void updateMines(Player player, MinesSession session) {
        if (player == null || session == null || !(player.getOpenInventory().getTopInventory().getHolder() instanceof GamblingHolder holder)
                || holder.kind() != GamblingHolder.Kind.MINES) {
            return;
        }
        Inventory inventory = player.getOpenInventory().getTopInventory();
        for (int index = 0; index < MINE_SLOTS.length; index++) {
            if (session.isRevealed(index)) {
                inventory.setItem(MINE_SLOTS[index], ItemBuilder.item(Material.LIME_STAINED_GLASS_PANE,
                        "<green>Safe</green>", List.of("<gray>Tile cleared.</gray>")));
            } else {
                inventory.setItem(MINE_SLOTS[index], ItemBuilder.item(Material.GRAY_STAINED_GLASS_PANE,
                        "<gray>Hidden tile</gray>", List.of("<yellow>Click to reveal.</yellow>")));
            }
        }
        inventory.setItem(4, ItemBuilder.item(Material.TNT, "<red>Mines</red>", List.of(
                "<gray>Stake: <white>" + economy.format(session.stake()) + "</white>",
                "<gray>Safe tiles: <white>" + session.safeRevealed() + "</white>",
                "<gray>Current multiplier: <white>" + Numbers.format(games.currentMinesMultiplier(session), 2) + "x</white>")));
    }

    public void closeAfterResult(Player player) {
        if (player != null && player.isOnline()) {
            player.closeInventory();
        }
    }

    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GamblingHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        switch (holder.kind()) {
            case MAIN -> mainClick(player, holder, slot);
            case GAME -> gameClick(player, holder, slot);
            case CHOICE -> choiceClick(player, holder, slot);
            case ANVIL -> anvilClick(player, holder, slot);
            case HISTORY -> historyClick(player, holder, slot);
            case MINES -> minesClick(player, holder, slot);
            case ANIMATION, AMOUNT -> {
                // Animation inventories are intentionally non-interactive.
            }
        }
    }

    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getInventory().getHolder() instanceof GamblingHolder holder) {
            if (holder.kind() == GamblingHolder.Kind.MINES) {
                games.closeMines(player);
            } else if (holder.kind() == GamblingHolder.Kind.ANIMATION) {
                // Closing skips the visuals but still settles the already chosen
                // result, so the player cannot cancel or duplicate a wager.
                games.finishAnimated(player);
            }
        }
    }

    private void mainClick(Player player, GamblingHolder holder, int slot) {
        if (slot == 49) {
            openHistory(player, 0);
        } else if (slot == 53) {
            player.closeInventory();
        } else {
            GameType game = holder.gameSlots().get(slot);
            if (game != null) {
                if (!settings.isGameEnabled(game)) {
                    messages.send(player, "game-disabled", Map.of("game", game.displayName()));
                } else {
                    openGame(player, game);
                }
            }
        }
    }

    private void gameClick(Player player, GamblingHolder holder, int slot) {
        if (slot == 44) {
            openMain(player);
            return;
        }
        if (slot == 13 && holder.game().needsChoice()) {
            openChoice(player, holder.game(), holder.risk());
            return;
        }
        for (int index = 0; index < RISK_SLOTS.length; index++) {
            if (slot == RISK_SLOTS[index]) {
                openGame(player, holder.game(), RiskTier.values()[index], holder.option());
                return;
            }
        }
        for (int index = 0; index < AMOUNT_SLOTS.length; index++) {
            if (slot == AMOUNT_SLOTS[index] && index < settings.amountPresets().size()) {
                playFromGui(player, holder, settings.amountPresets().get(index));
                return;
            }
        }
        if (slot == 37) {
            openAnvil(player, holder.game(), holder.risk(), holder.option());
        } else if (slot == 39) {
            if (holder.game().needsChoice() && holder.option().isBlank()) {
                openChoice(player, holder.game(), holder.risk());
            } else {
                input.beginAmount(player, holder.game(), holder.risk(), holder.option());
            }
        }
    }

    private void choiceClick(Player player, GamblingHolder holder, int slot) {
        if (slot == 49) {
            openGame(player, holder.game(), holder.risk(), "");
            return;
        }
        int index = slot - 10;
        String[] choices = holder.game().choices();
        if (index >= 0 && index < choices.length) {
            openGame(player, holder.game(), holder.risk(), choices[index]);
        }
    }

    private void anvilClick(Player player, GamblingHolder holder, int slot) {
        if (slot != 2) {
            return;
        }
        ItemStack result = eventResult(player);
        String entered = null;
        if (result != null && result.hasItemMeta()) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                entered = ChatColor.stripColor(meta.getDisplayName());
            }
        }
        if (entered == null || entered.isBlank()) {
            messages.send(player, "invalid-amount");
            return;
        }
        var parsed = Numbers.parseAmount(entered, economy.balance(player));
        if (parsed.isEmpty()) {
            messages.send(player, "invalid-amount");
            return;
        }
        double amount = parsed.get();
        PlatformScheduler.runEntity(player, () -> {
            player.closeInventory();
            games.play(player, holder.game(), amount, holder.risk(), holder.option());
        });
    }

    private void historyClick(Player player, GamblingHolder holder, int slot) {
        if (slot == 45 && holder.page() > 0) {
            reopenHistory(player, holder, holder.page() - 1);
            return;
        }
        if (slot == 53) {
            List<Transaction> transactions = store.history(holder.target(), null, settings.historySize());
            int maxPage = Math.max(0, (transactions.size() - 1) / 34);
            if (holder.page() < maxPage) {
                reopenHistory(player, holder, holder.page() + 1);
            }
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot < 10 || slot > 43) {
            return;
        }
        List<Transaction> transactions = store.history(holder.target(), null, settings.historySize());
        int index = holder.page() * 34 + (slot - 10);
        if (index >= 0 && index < transactions.size()) {
            Transaction transaction = transactions.get(index);
            player.sendMessage(Text.parse("<gray>" + transaction.game().displayName() + " • "
                    + TIME_FORMAT.format(transaction.timestamp()) + " • " + (transaction.win() ? "<green>WIN" : "<red>LOSS")
                    + "</gray>"));
            player.sendMessage(Text.parse("<gray>" + transaction.details() + " | stake "
                    + economy.format(transaction.stake()) + " | payout " + economy.format(transaction.payout()) + "</gray>"));
        }
    }

    private void reopenHistory(Player player, GamblingHolder holder, int page) {
        String name = holder.target() == null ? player.getName() : Bukkit.getOfflinePlayer(holder.target()).getName();
        openHistory(player, holder.target(), name, page, holder.admin());
    }

    private void minesClick(Player player, GamblingHolder holder, int slot) {
        if (slot == 52) {
            games.cashOut(player);
            return;
        }
        for (int index = 0; index < MINE_SLOTS.length; index++) {
            if (slot == MINE_SLOTS[index]) {
                games.revealMine(player, index);
                return;
            }
        }
    }

    private void playFromGui(Player player, GamblingHolder holder, double amount) {
        if (holder.game().needsChoice() && holder.option().isBlank()) {
            openChoice(player, holder.game(), holder.risk());
            return;
        }
        games.play(player, holder.game(), amount, holder.risk(), holder.option());
    }

    private ItemStack eventResult(Player player) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        return inventory.getItem(2);
    }

    private static Material choiceMaterial(GameType game, String choice) {
        return switch (game) {
            case ROULETTE -> switch (choice.toLowerCase(Locale.ROOT)) {
                case "red" -> Material.RED_WOOL;
                case "black" -> Material.BLACK_WOOL;
                default -> Material.LIME_WOOL;
            };
            case HIGHLOW -> choice.equalsIgnoreCase("high") ? Material.SPECTRAL_ARROW : Material.ARROW;
            case CRASH -> Material.GOLD_INGOT;
            case LOTTERY -> Material.PAPER;
            default -> Material.PAPER;
        };
    }

    private static void fill(Inventory inventory, ItemStack filler) {
        for (int index = 0; index < inventory.getSize(); index++) {
            if (inventory.getItem(index) == null) {
                inventory.setItem(index, filler.clone());
            }
        }
    }
}
