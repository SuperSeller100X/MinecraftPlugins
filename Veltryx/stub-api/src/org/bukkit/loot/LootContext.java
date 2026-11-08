package org.bukkit.loot;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
public final class LootContext {
    public Location location; public Entity looted;
    public static class Builder {
        private final LootContext context = new LootContext();
        public Builder(Location location){context.location=location;}
        public Builder lootedEntity(Entity entity){context.looted=entity;return this;}
        public LootContext build(){return context;}
    }
}
