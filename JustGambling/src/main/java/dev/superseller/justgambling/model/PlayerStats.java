package dev.superseller.justgambling.model;

/** Snapshot of a player's gambling statistics. */
public record PlayerStats(
        long games,
        long wins,
        long losses,
        double wagered,
        double paidOut,
        double bestWin
) {
    public double winRate() {
        return games <= 0 ? 0.0 : (wins * 100.0) / games;
    }
}
