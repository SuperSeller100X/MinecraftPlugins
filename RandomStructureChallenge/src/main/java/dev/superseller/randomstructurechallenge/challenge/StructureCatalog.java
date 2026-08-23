package dev.superseller.randomstructurechallenge.challenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import dev.superseller.randomstructurechallenge.config.PluginSettings;
import dev.superseller.randomstructurechallenge.util.StructureKeys;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.generator.structure.Structure;

/**
 * Discovers every registered Minecraft structure (vanilla + datapacks)
 * the same way {@code /place structure} does.
 */
public final class StructureCatalog {

    /**
     * Paper 26.2 built-in structures — used only if the live registry is empty.
     */
    static final List<String> VANILLA_26_2 = List.of(
            "minecraft:ancient_city",
            "minecraft:bastion_remnant",
            "minecraft:buried_treasure",
            "minecraft:desert_pyramid",
            "minecraft:end_city",
            "minecraft:fortress",
            "minecraft:igloo",
            "minecraft:jungle_pyramid",
            "minecraft:mansion",
            "minecraft:mineshaft",
            "minecraft:mineshaft_mesa",
            "minecraft:monument",
            "minecraft:nether_fossil",
            "minecraft:ocean_ruin_cold",
            "minecraft:ocean_ruin_warm",
            "minecraft:pillager_outpost",
            "minecraft:ruined_portal",
            "minecraft:ruined_portal_desert",
            "minecraft:ruined_portal_jungle",
            "minecraft:ruined_portal_mountain",
            "minecraft:ruined_portal_nether",
            "minecraft:ruined_portal_ocean",
            "minecraft:ruined_portal_swamp",
            "minecraft:shipwreck",
            "minecraft:shipwreck_beached",
            "minecraft:stronghold",
            "minecraft:swamp_hut",
            "minecraft:trail_ruins",
            "minecraft:trial_chambers",
            "minecraft:village_desert",
            "minecraft:village_plains",
            "minecraft:village_savanna",
            "minecraft:village_snowy",
            "minecraft:village_taiga"
    );

    private final PluginSettings settings;
    private final Logger logger;
    private List<String> cache = List.of();

    public StructureCatalog(PluginSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    public void refresh() {
        List<String> discovered = discover();
        List<String> filtered = filter(discovered);
        if (filtered.isEmpty()) {
            logger.warning("Structure catalog is empty after filters — falling back to the vanilla 26.2 list.");
            filtered = filter(VANILLA_26_2);
        }
        cache = List.copyOf(filtered);
        logger.info("Loaded " + cache.size() + " placeable structure(s).");
    }

    public List<String> all() {
        return cache;
    }

    public String randomOne() {
        if (cache.isEmpty()) {
            return "minecraft:pillager_outpost";
        }
        return cache.get(ThreadLocalRandom.current().nextInt(cache.size()));
    }

    public List<String> shuffled() {
        List<String> copy = new ArrayList<>(cache);
        Collections.shuffle(copy, ThreadLocalRandom.current());
        return copy;
    }

    private List<String> discover() {
        List<String> keys = new ArrayList<>();
        try {
            Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
            for (Structure structure : registry) {
                NamespacedKey key = registry.getKey(structure);
                if (key == null) {
                    continue;
                }
                String asString = StructureKeys.normalize(key.getNamespace() + ":" + key.getKey());
                if (StructureKeys.safe(asString)) {
                    keys.add(asString);
                }
            }
        } catch (Throwable t) {
            logger.warning("Could not read RegistryKey.STRUCTURE (" + t.getMessage() + "); using the built-in list.");
        }
        if (keys.isEmpty()) {
            keys.addAll(VANILLA_26_2);
        }
        return keys;
    }

    private List<String> filter(List<String> incoming) {
        List<String> whitelist = settings.whitelist();
        List<String> blacklist = settings.blacklist();
        List<String> out = new ArrayList<>();
        for (String key : incoming) {
            String normalized = StructureKeys.normalize(key);
            if (!StructureKeys.safe(normalized)) {
                continue;
            }
            if (!whitelist.isEmpty() && !whitelist.contains(normalized)) {
                continue;
            }
            if (blacklist.contains(normalized)) {
                continue;
            }
            out.add(normalized);
        }
        return out;
    }
}
