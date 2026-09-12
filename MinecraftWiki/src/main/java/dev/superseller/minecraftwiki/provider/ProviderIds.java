package dev.superseller.minecraftwiki.provider;

import java.util.List;

/**
 * Canonical content provider ids, matching the {@code content.providers} keys in
 * {@code config.yml}.
 *
 * <p>Keeping them here means an unknown key in the configuration can be reported instead
 * of silently doing nothing.</p>
 */
public final class ProviderIds {

    public static final String BLOCKS = "blocks";
    public static final String ITEMS = "items";
    public static final String ENTITIES = "entities";
    public static final String ENCHANTMENTS = "enchantments";
    public static final String EFFECTS = "effects";
    public static final String POTIONS = "potions";
    public static final String BIOMES = "biomes";
    public static final String DIMENSIONS = "dimensions";
    public static final String STRUCTURES = "structures";
    public static final String SOUNDS = "sounds";
    public static final String PARTICLES = "particles";
    public static final String ATTRIBUTES = "attributes";
    public static final String DAMAGE_TYPES = "damage-types";
    public static final String GAME_EVENTS = "game-events";
    public static final String GAMERULES = "gamerules";
    public static final String COMMANDS = "commands";
    public static final String TAGS = "tags";
    public static final String VILLAGER_PROFESSIONS = "villager-professions";
    public static final String RECIPES = "recipes";
    public static final String CUSTOM_ARTICLES = "custom-articles";
    public static final String ADVANCEMENTS = "advancements";

    /** Every id that {@code config.yml} may enable, in declaration order. */
    public static final List<String> ALL = List.of(
            BLOCKS, ITEMS, ENTITIES, ENCHANTMENTS, EFFECTS, POTIONS, BIOMES, DIMENSIONS, STRUCTURES,
            SOUNDS, PARTICLES, ATTRIBUTES, DAMAGE_TYPES, GAME_EVENTS, GAMERULES, COMMANDS, TAGS,
            VILLAGER_PROFESSIONS, RECIPES, CUSTOM_ARTICLES, ADVANCEMENTS);

    private ProviderIds() {
    }

    public static boolean known(String id) {
        return ALL.contains(id);
    }
}
