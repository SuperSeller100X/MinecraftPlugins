package dev.superseller.subscriptions.model;

import java.util.UUID;

public final class InboxEntry {

    private final long id;
    private final UUID ownerId;
    private final String itemBase64;
    private final String source;
    private final long createdAt;

    public InboxEntry(long id, UUID ownerId, String itemBase64, String source, long createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.itemBase64 = itemBase64;
        this.source = source == null ? "" : source;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String itemBase64() {
        return itemBase64;
    }

    public String source() {
        return source;
    }

    public long createdAt() {
        return createdAt;
    }
}
