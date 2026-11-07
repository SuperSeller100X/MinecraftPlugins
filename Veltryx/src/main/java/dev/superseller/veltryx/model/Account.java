package dev.superseller.veltryx.model;

import java.util.UUID;

/** A persisted economy account. {@code balance} is stored in cents. */
public record Account(UUID uuid, String name, long balance) {
}
