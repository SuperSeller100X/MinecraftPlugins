package dev.superseller.chestlock.model;

/** Player-owned convenience defaults; no security secrets are stored here. */
public record PlayerSettings(
        int defaultDurationSeconds,
        boolean defaultCreateKey,
        boolean autoOpen,
        boolean sounds,
        boolean particles,
        boolean actionBar,
        boolean breakConfirmation,
        boolean attemptNotifications
) {
}
