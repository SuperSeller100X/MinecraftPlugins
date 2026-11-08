package dev.superseller.veltryx.spawner;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.spawner.SpawnRule;
import org.bukkit.block.spawner.SpawnerEntry;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import dev.superseller.veltryx.util.Txt;

/** Tags permanent custom spawners, both as items and placed tile states. */
public final class SpawnerItems {
    public record Identity(UUID id, SpawnerType type) { }

    private final NamespacedKey idKey;
    private final NamespacedKey typeKey;
    private final Logger logger;

    public SpawnerItems(Plugin plugin) {
        idKey = new NamespacedKey(plugin, "custom_spawner_id");
        typeKey = new NamespacedKey(plugin, "custom_spawner_type");
        logger = plugin.getLogger();
    }

    public ItemStack create(Identity identity) {
        ItemStack item = new ItemStack(Material.SPAWNER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) throw new IllegalStateException("Spawner item has no metadata");
        meta.displayName(Txt.legacy("§b" + identity.type().displayName() + " Spawner"));
        meta.lore(Txt.legacy(List.of("§7Permanent custom spawner",
                "§7Right-click after placing to view loot and change mode.",
                "§7Mine with Silk Touch to pick it up with its storage.")));
        write(meta.getPersistentDataContainer(), identity);
        item.setItemMeta(meta);
        return item;
    }

    /** A non-purchasable GUI icon, without the identity tag. */
    public ItemStack icon(SpawnerType type) {
        ItemStack item = new ItemStack(Material.SPAWNER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Txt.legacy("§b" + type.displayName() + " Spawner"));
            meta.lore(Txt.legacy(List.of("§6Price: §b1,000 shards", "§7Permanent; works at any light level.",
                    "§7Switch between mob and inventory modes.", "§eClick to buy")));
            item.setItemMeta(meta);
        }
        return item;
    }

    public Identity identity(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) return null;
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : read(meta.getPersistentDataContainer());
    }

    public Identity identity(Block block) {
        if (block == null || block.getType() != Material.SPAWNER) return null;
        return block.getState() instanceof CreatureSpawner spawner ? identity(spawner) : null;
    }

    public Identity identity(CreatureSpawner spawner) {
        return spawner == null ? null : read(spawner.getPersistentDataContainer());
    }

    /** Configure both the current spawn and the potential list to ignore light. */
    public boolean configure(Block block, Identity identity) {
        if (!(block.getState() instanceof CreatureSpawner spawner)) return false;
        try {
            spawner.setSpawnedType(identity.type().entityType());
            EntitySnapshot snapshot = spawner.getSpawnedEntity();
            if (snapshot == null) return false;
            SpawnerEntry entry = new SpawnerEntry(snapshot, 1, new SpawnRule(0, 15, 0, 15));
            spawner.setSpawnedEntity(entry);
            // Vanilla picks from potential spawns when resetting its timer.
            spawner.setPotentialSpawns(List.of(entry));
            write(spawner.getPersistentDataContainer(), identity);
            return spawner.update(true, false);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not configure custom spawner " + identity.id(), e);
            return false;
        }
    }

    private void write(PersistentDataContainer pdc, Identity identity) {
        pdc.set(idKey, PersistentDataType.STRING, identity.id().toString());
        pdc.set(typeKey, PersistentDataType.STRING, identity.type().name());
    }

    private Identity read(PersistentDataContainer pdc) {
        if (pdc == null) return null;
        String raw = pdc.get(idKey, PersistentDataType.STRING);
        SpawnerType type = SpawnerType.parse(pdc.get(typeKey, PersistentDataType.STRING));
        if (raw == null || type == null) return null;
        try { return new Identity(UUID.fromString(raw), type); }
        catch (IllegalArgumentException e) { return null; }
    }
}
