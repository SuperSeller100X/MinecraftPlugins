package dev.superseller.justgambling.model;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit record for one resolved wager. */
public record Transaction(
        UUID id,
        UUID playerId,
        String playerName,
        GameType game,
        double stake,
        double payout,
        boolean win,
        String details,
        Instant timestamp
) {
    public double net() {
        return payout - stake;
    }
}
