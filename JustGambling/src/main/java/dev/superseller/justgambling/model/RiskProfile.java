package dev.superseller.justgambling.model;

/** A validated chance and payout pair loaded from configuration. */
public record RiskProfile(double chance, double multiplier) {
    public RiskProfile {
        if (!Double.isFinite(chance) || chance <= 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("chance must be within (0, 1]");
        }
        if (!Double.isFinite(multiplier) || multiplier <= 0.0) {
            throw new IllegalArgumentException("multiplier must be positive and finite");
        }
    }
}
