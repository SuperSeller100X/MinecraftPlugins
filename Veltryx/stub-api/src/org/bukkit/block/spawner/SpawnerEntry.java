package org.bukkit.block.spawner;
import org.bukkit.entity.EntitySnapshot;
public class SpawnerEntry {
    private final EntitySnapshot snapshot; private final int weight; private final SpawnRule rule;
    public SpawnerEntry(EntitySnapshot snapshot, int weight, SpawnRule rule){this.snapshot=snapshot;this.weight=weight;this.rule=rule;}
    public EntitySnapshot getSnapshot(){return snapshot;} public int getSpawnWeight(){return weight;} public SpawnRule getSpawnRule(){return rule;}
}
