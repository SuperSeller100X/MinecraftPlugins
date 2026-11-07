package dev.superseller.veltryx.model;

import java.util.UUID;

/** One anti-cheat flag (detection event) recorded for a player. */
public record FlagEntry(String id, UUID uuid, String name, String check, double vl, String detail, long issued) {
}
