package dev.superseller.justgambling.model;

import java.util.UUID;

/** Mutable state for one player's active Mines board. Access is serialized by the game service. */
public final class MinesSession {
    private final UUID playerId;
    private final double stake;
    private final RiskTier risk;
    private final boolean[] mines;
    private final boolean[] revealed;
    private int safeRevealed;
    private boolean resolved;

    public MinesSession(UUID playerId, double stake, RiskTier risk, boolean[] mines) {
        this.playerId = playerId;
        this.stake = stake;
        this.risk = risk;
        this.mines = mines.clone();
        this.revealed = new boolean[mines.length];
    }

    public UUID playerId() {
        return playerId;
    }

    public double stake() {
        return stake;
    }

    public RiskTier risk() {
        return risk;
    }

    public int size() {
        return mines.length;
    }

    public boolean isMine(int index) {
        return mines[index];
    }

    public boolean isRevealed(int index) {
        return revealed[index];
    }

    public void reveal(int index) {
        if (!revealed[index]) {
            revealed[index] = true;
            if (!mines[index]) {
                safeRevealed++;
            }
        }
    }

    public int safeRevealed() {
        return safeRevealed;
    }

    public int mineCount() {
        int count = 0;
        for (boolean mine : mines) {
            if (mine) {
                count++;
            }
        }
        return count;
    }

    public boolean allSafeRevealed() {
        return safeRevealed >= mines.length - mineCount();
    }

    public boolean resolved() {
        return resolved;
    }

    public void markResolved() {
        resolved = true;
    }
}
