package dev.superseller.veltryx.model;

import java.util.UUID;

/** A chat mute. {@code expires} is epoch millis, or {@code -1} for permanent. */
public record MuteEntry(String id, UUID uuid, String name, String reason, String staff,
                        long issued, long expires, boolean active) {

    public boolean expiredAt(long nowMillis) {
        return expires != -1L && expires <= nowMillis;
    }

    public boolean isActiveAt(long nowMillis) {
        return active && !expiredAt(nowMillis);
    }
}
