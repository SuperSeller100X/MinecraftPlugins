package dev.superseller.veltryx.model;

import java.util.UUID;

/** Persistent player identity and playtime tracking. */
public record Profile(UUID uuid, String name, long firstSeen, long lastSeen, long playtimeMs, int joins) {
}
