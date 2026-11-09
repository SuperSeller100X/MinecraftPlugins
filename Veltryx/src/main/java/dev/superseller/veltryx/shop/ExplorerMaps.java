package dev.superseller.veltryx.shop;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.MapDecorations;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;
import org.bukkit.util.StructureSearchResult;

/** Real, persistent explorer-map destinations; never sell a misleading map without one. */
public final class ExplorerMaps {
    public record Target(List<String> structures, String decoration) { }
    private static final Map<String, Target> TARGETS = Map.ofEntries(
            Map.entry("ABANDONED_CAMP_MAP", new Target(List.of(
                    "abandoned_camp_forest", "abandoned_camp_taiga", "abandoned_camp_birch_forest",
                    "abandoned_camp_savanna", "abandoned_camp_meadow", "abandoned_camp_bamboo_jungle",
                    "abandoned_camp_cherry_grove", "abandoned_camp_dappled_forest", "abandoned_camp_flower_forest",
                    "abandoned_camp_old_growth_birch_forest", "abandoned_camp_old_growth_pine_taiga",
                    "abandoned_camp_old_growth_spruce_taiga", "abandoned_camp_pale_garden",
                    "abandoned_camp_snowy_taiga", "abandoned_camp_sparse_jungle", "abandoned_camp_swamp",
                    "abandoned_camp_windswept_forest", "abandoned_camp_wooded_badlands"), "abandoned_camp")),
            target("BURIED_ANCIENT_CITY_MAP", "ancient_city", "ancient_city"),
            target("BURIED_MINESHAFT_MAP", "mineshaft", "mineshaft"),
            target("BURIED_TREASURE_MAP", "buried_treasure", "red_x"),
            target("BURIED_TRIAL_CHAMBERS_MAP", "trial_chambers", "trial_chambers"),
            target("DESERT_PYRAMID_MAP", "desert_pyramid", "desert_pyramid"),
            target("DESERT_VILLAGE_MAP", "village_desert", "village_desert"),
            target("JUNGLE_PYRAMID_MAP", "jungle_pyramid", "jungle_temple"),
            target("OCEAN_MONUMENT_MAP", "monument", "monument"),
            target("PLAINS_VILLAGE_MAP", "village_plains", "village_plains"),
            target("SAVANNA_VILLAGE_MAP", "village_savanna", "village_savanna"),
            target("SNOWY_VILLAGE_MAP", "village_snowy", "village_snowy"),
            target("SWAMP_HUT_MAP", "swamp_hut", "swamp_hut"),
            target("TAIGA_VILLAGE_MAP", "village_taiga", "village_taiga"),
            target("WARM_OCEAN_RUINS_MAP", "ocean_ruin_warm", "ocean_ruin_warm"),
            target("WOODLAND_MANSION_MAP", "mansion", "mansion"));

    private ExplorerMaps() { }
    private static Map.Entry<String, Target> target(String item, String structure, String decoration) {
        return Map.entry(item, new Target(List.of(structure), decoration));
    }
    public static Target target(String material) { return TARGETS.get(material); }
    public static boolean namedMap(String material) { return TARGETS.containsKey(material); }

    /**
     * Search within vanilla's usual 100-chunk exploration radius, allowing
     * already explored structures. A camp has many biome variants; the first
     * available variant is used. Structure lookup is synchronous and can be
     * expensive. Returns false when this world cannot locate the destination.
     */
    public static boolean prepare(Player player, ItemStack stack, MapMeta meta) {
        Target target = target(stack.getType().name());
        if (target == null) return false;
        World world = player.getWorld();
        Location origin = player.getLocation();
        var access = RegistryAccess.registryAccess();
        var structures = access.getRegistry(RegistryKey.STRUCTURE);
        Location destination = null;
        for (String key : target.structures()) {
            Structure structure = structures.get(NamespacedKey.minecraft(key));
            if (structure == null) continue;
            StructureSearchResult found = world.locateNearestStructure(origin, structure, 100, false);
            if (found != null) { destination = found.getLocation(); break; }
        }
        MapCursor.Type decoration = access.getRegistry(RegistryKey.MAP_DECORATION_TYPE)
                .get(NamespacedKey.minecraft(target.decoration()));
        if (destination == null || decoration == null) return false;
        MapView view = Bukkit.createMap(world);
        if (view == null) return false;
        view.setScale(MapView.Scale.NORMAL);
        view.setCenterX(destination.getBlockX());
        view.setCenterZ(destination.getBlockZ());
        meta.setMapView(view);
        if (!stack.setItemMeta(meta)) return false;
        // Store a vanilla data-component marker, rather than an ephemeral renderer,
        // so the destination survives restarts and works outside the plugin GUI.
        stack.setData(DataComponentTypes.MAP_DECORATIONS, MapDecorations.mapDecorations(Map.of(
                "+", MapDecorations.decorationEntry(decoration, destination.getX(), destination.getZ(), 180f))));
        return true;
    }
}
