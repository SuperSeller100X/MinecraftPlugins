package dev.superseller.justgambling.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import org.bukkit.Material;

/** The games shipped with JustGambling. Every game is resolved by the house. */
public enum GameType {
    COINFLIP("coinflip", "Coin Flip", Material.GOLD_INGOT, "Flip against the house."),
    DICE("dice", "Dice", Material.BONE, "Roll under your selected chance."),
    ROULETTE("roulette", "Roulette", Material.COMPASS, "Choose a colour and spin 0–36."),
    WHEEL("wheel", "Lucky Wheel", Material.NETHER_STAR, "Spin a weighted house wheel."),
    HIGHLOW("highlow", "High / Low", Material.CLOCK, "Predict whether the roll lands high or low."),
    SLOTS("slots", "Slots", Material.JUKEBOX, "Spin three reels for a matching payout."),
    SCRATCH("scratch", "Scratch Card", Material.PAPER, "Reveal a hidden instant prize."),
    MINES("mines", "Mines", Material.TNT, "Reveal safe tiles, then cash out."),
    CRASH("crash", "Crash", Material.FIREWORK_ROCKET, "Choose a multiplier before the house crashes."),
    JACKPOT("jackpot", "Jackpot", Material.DIAMOND, "Chase the rare house jackpot."),
    LOTTERY("lottery", "Lucky Number", Material.EMERALD, "Pick one number from 1 to 10."),
    DOUBLE_OR_NOTHING("double", "Double or Nothing", Material.GOLD_BLOCK, "Risk the selected chance for a big multiplier.");

    private final String id;
    private final String displayName;
    private final Material icon;
    private final String description;

    GameType(String id, String displayName, Material icon, String description) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    public boolean needsChoice() {
        return this == ROULETTE || this == HIGHLOW || this == CRASH || this == LOTTERY;
    }

    public String[] choices() {
        return switch (this) {
            case ROULETTE -> new String[]{"red", "black", "green"};
            case HIGHLOW -> new String[]{"high", "low"};
            case CRASH -> new String[]{"1.25", "1.50", "2.00", "3.00", "5.00", "10.00"};
            case LOTTERY -> new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
            default -> new String[0];
        };
    }

    public static Optional<GameType> fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.toLowerCase(Locale.ROOT).replace('_', '-');
        return Arrays.stream(values())
                .filter(game -> game.id.equals(value)
                        || game.name().toLowerCase(Locale.ROOT).equals(value)
                        || (game == HIGHLOW && value.equals("high-low"))
                        || (game == SCRATCH && value.equals("scratchcard"))
                        || (game == DOUBLE_OR_NOTHING && value.equals("double-or-nothing")))
                .findFirst();
    }
}
