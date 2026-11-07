package dev.superseller.veltryx.model;

import java.util.UUID;

/** A free-form staff note attached to a player. */
public record NoteEntry(String id, UUID uuid, String name, String note, String staff, long issued) {
}
