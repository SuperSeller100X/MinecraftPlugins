package dev.superseller.veltryx.model;

import java.util.UUID;

/** A staff warning attached to a player. */
public record WarnEntry(String id, UUID uuid, String name, String reason, String staff,
                        long issued, boolean active) {
}
