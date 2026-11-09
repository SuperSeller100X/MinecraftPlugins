package dev.superseller.veltryx.model;

import java.util.UUID;

/**
 * One entry of the custom ban system.
 *
 * @param id      short human-readable ban id (shown on the ban screen)
 * @param expires epoch millis, or {@code -1} for permanent
 * @param details free-form staff note (often the recent anti-cheat flags)
 * @param auto    true when the anti-cheat issued this ban automatically
 */
public record BanEntry(String id, UUID uuid, String name, String category, String reason,
                       String details, String staff, long issued, long expires, boolean active, boolean auto) {

    /** Whether the ban has expired at the given time (inactive bans never count). */
    public boolean expiredAt(long nowMillis) {
        return expires != -1L && expires <= nowMillis;
    }

    /** Whether the ban is in force at the given time. */
    public boolean isActiveAt(long nowMillis) {
        return active && !expiredAt(nowMillis);
    }
}
