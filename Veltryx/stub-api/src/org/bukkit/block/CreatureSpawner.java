package org.bukkit.block;
import java.util.Collection;
import org.bukkit.block.spawner.SpawnerEntry;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
public interface CreatureSpawner extends BlockState {
    void setSpawnedType(EntityType type);
    EntityType getSpawnedType();
    EntitySnapshot getSpawnedEntity();
    void setSpawnedEntity(SpawnerEntry entry);
    void setPotentialSpawns(Collection<SpawnerEntry> entries);
    PersistentDataContainer getPersistentDataContainer();
}
