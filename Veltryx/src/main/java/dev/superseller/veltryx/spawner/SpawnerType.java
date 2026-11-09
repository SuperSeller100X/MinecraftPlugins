package dev.superseller.veltryx.spawner;

import org.bukkit.entity.EntityType;

/** The seven permanent, shard-priced spawners offered by Veltryx. */
public enum SpawnerType {
    PIG("Pig", EntityType.PIG, "entities/pig"),
    SHEEP("Sheep", EntityType.SHEEP, "entities/sheep"),
    COW("Cow", EntityType.COW, "entities/cow"),
    IRON_GOLEM("Iron Golem", EntityType.IRON_GOLEM, "entities/iron_golem"),
    SKELETON("Skeleton", EntityType.SKELETON, "entities/skeleton"),
    ZOMBIE("Zombie", EntityType.ZOMBIE, "entities/zombie"),
    CREEPER("Creeper", EntityType.CREEPER, "entities/creeper");

    public static final long PRICE_SHARDS = 1_000L;
    private final String displayName;
    private final EntityType entityType;
    private final String lootTable;

    SpawnerType(String displayName, EntityType entityType, String lootTable) {
        this.displayName = displayName;
        this.entityType = entityType;
        this.lootTable = lootTable;
    }

    public String displayName() { return displayName; }
    public EntityType entityType() { return entityType; }
    public String lootTable() { return lootTable; }

    public static SpawnerType parse(String raw) {
        if (raw == null) return null;
        try { return valueOf(raw); }
        catch (IllegalArgumentException e) { return null; }
    }
}
