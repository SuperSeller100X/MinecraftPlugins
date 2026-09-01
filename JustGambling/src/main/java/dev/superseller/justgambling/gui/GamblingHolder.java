package dev.superseller.justgambling.gui;

import dev.superseller.justgambling.model.GameType;
import dev.superseller.justgambling.model.RiskTier;

import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Metadata attached to every JustGambling inventory. */
public final class GamblingHolder implements InventoryHolder {
    public enum Kind { MAIN, GAME, CHOICE, AMOUNT, ANVIL, HISTORY, MINES, ANIMATION }

    private final Kind kind;
    private final UUID viewer;
    private final UUID target;
    private final GameType game;
    private final RiskTier risk;
    private final String option;
    private final int page;
    private final boolean admin;
    private final Map<Integer, GameType> gameSlots;
    private Inventory inventory;

    public GamblingHolder(Kind kind, UUID viewer, UUID target, GameType game, RiskTier risk, String option,
                          int page, boolean admin, Map<Integer, GameType> gameSlots) {
        this.kind = kind;
        this.viewer = viewer;
        this.target = target;
        this.game = game;
        this.risk = risk;
        this.option = option;
        this.page = page;
        this.admin = admin;
        this.gameSlots = gameSlots == null ? Map.of() : Map.copyOf(gameSlots);
    }

    public Kind kind() {
        return kind;
    }

    public UUID viewer() {
        return viewer;
    }

    public UUID target() {
        return target;
    }

    public GameType game() {
        return game;
    }

    public RiskTier risk() {
        return risk;
    }

    public String option() {
        return option;
    }

    public int page() {
        return page;
    }

    public boolean admin() {
        return admin;
    }

    public Map<Integer, GameType> gameSlots() {
        return gameSlots;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
