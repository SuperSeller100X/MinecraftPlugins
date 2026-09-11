package dev.superseller.minecraftwiki.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.Attributable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.potion.PotionEffectType;

import dev.superseller.minecraftwiki.config.ConfigIssue;
import dev.superseller.minecraftwiki.config.ConfigIssues;
import dev.superseller.minecraftwiki.provider.RegistryEntry.Facts;
import dev.superseller.minecraftwiki.util.Text;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

/**
 * An immutable snapshot of everything the wiki knows about the running server.
 *
 * <p>Captured once on the main thread while the plugin enables, and again on reload. After
 * that it is a plain object graph with no Bukkit handles in it, which is what makes the
 * search index and the article bodies safe to build off the main thread: no thread ever
 * touches a live registry, world or entity from a task.</p>
 *
 * <p>Every section is captured defensively. A registry that does not exist on this server,
 * or a single entry that throws, is reported under {@code [Wiki/Config]} and skipped rather
 * than taking the whole wiki down.</p>
 */
public final class RegistrySnapshot {

    /** A material with the facts the wiki can reliably determine about it. */
    public record MaterialInfo(String key, Material material, boolean block, boolean item,
                               Map<String, String> properties, List<String> tags) {
    }

    /** A real attribute default read from the entity type. */
    public record AttributeValue(String key, String label, double value) {
    }

    /** An entity type plus the facts and attribute defaults read from the API. */
    public record EntityInfo(String key, String label, boolean alive, boolean spawnable,
                             String spawnCategory, Map<String, String> properties,
                             List<AttributeValue> attributes) {
    }

    /** A registry tag and the keys of the values it contains. */
    public record TagInfo(String registry, String key, List<String> values) {
    }

    /** One recipe: its result, a display type and an ingredient grid of material keys. */
    public record RecipeInfo(String resultKey, String typeKey, List<List<String>> grid,
                             Map<String, String> properties) {
    }

    /** One command registered on the server. */
    public record CommandInfo(String name, List<String> aliases, String permission,
                              String description, String usage) {
    }

    /** One world dimension. */
    public record DimensionInfo(String key, String label, Map<String, String> properties) {
    }

    private final String minecraftVersion;
    private final Map<String, MaterialInfo> materials;
    private final Map<String, EntityInfo> entities;
    private final Map<String, RegistryEntry> enchantments;
    private final Map<String, RegistryEntry> effects;
    private final Map<String, RegistryEntry> potions;
    private final Map<String, RegistryEntry> biomes;
    private final Map<String, RegistryEntry> structures;
    private final Map<String, RegistryEntry> particles;
    private final Map<String, RegistryEntry> sounds;
    private final Map<String, RegistryEntry> attributes;
    private final Map<String, RegistryEntry> damageTypes;
    private final Map<String, RegistryEntry> gameEvents;
    private final Map<String, RegistryEntry> villagerProfessions;
    private final Map<String, RegistryEntry> advancements;
    private final Map<String, RegistryEntry> gamerules;
    private final List<TagInfo> tags;
    private final List<CommandInfo> commands;
    private final List<DimensionInfo> dimensions;
    private final Map<String, List<RecipeInfo>> recipesByResult;
    private final long captureMillis;

    private RegistrySnapshot(Builder builder) {
        this.minecraftVersion = builder.minecraftVersion;
        this.materials = Map.copyOf(builder.materials);
        this.entities = Map.copyOf(builder.entities);
        this.enchantments = Map.copyOf(builder.enchantments);
        this.effects = Map.copyOf(builder.effects);
        this.potions = Map.copyOf(builder.potions);
        this.biomes = Map.copyOf(builder.biomes);
        this.structures = Map.copyOf(builder.structures);
        this.particles = Map.copyOf(builder.particles);
        this.sounds = Map.copyOf(builder.sounds);
        this.attributes = Map.copyOf(builder.attributes);
        this.damageTypes = Map.copyOf(builder.damageTypes);
        this.gameEvents = Map.copyOf(builder.gameEvents);
        this.villagerProfessions = Map.copyOf(builder.villagerProfessions);
        this.advancements = Map.copyOf(builder.advancements);
        this.gamerules = Map.copyOf(builder.gamerules);
        this.tags = List.copyOf(builder.tags);
        this.commands = List.copyOf(builder.commands);
        this.dimensions = List.copyOf(builder.dimensions);
        Map<String, List<RecipeInfo>> recipes = new LinkedHashMap<>();
        builder.recipesByResult.forEach((key, list) -> recipes.put(key, List.copyOf(list)));
        this.recipesByResult = Collections.unmodifiableMap(recipes);
        this.captureMillis = builder.captureMillis;
    }

    /**
     * Reads every registry the wiki uses. Must be called on the main thread.
     *
     * @param enabledProviders only providers enabled in config.yml are captured
     */
    public static RegistrySnapshot capture(java.util.Set<String> enabledProviders, ConfigIssues issues) {
        Builder builder = new Builder();
        builder.minecraftVersion = safeVersion();
        if (enabledProviders.contains(ProviderIds.BLOCKS) || enabledProviders.contains(ProviderIds.ITEMS)
                || enabledProviders.contains("materials") || enabledProviders.contains(ProviderIds.RECIPES)) {
            builder.captureMaterials(issues);
        }
        if (enabledProviders.contains(ProviderIds.TAGS)) {
            builder.captureTags(issues);
        }
        if (enabledProviders.contains(ProviderIds.ENTITIES)) {
            builder.captureEntities(issues);
        }
        if (enabledProviders.contains(ProviderIds.ENCHANTMENTS)) {
            builder.enchantments = captureEnchantments(issues);
        }
        if (enabledProviders.contains(ProviderIds.EFFECTS)) {
            builder.effects = captureEffects(issues);
        }
        if (enabledProviders.contains(ProviderIds.POTIONS)) {
            builder.potions = captureKeyed(RegistryKey.POTION, "potion", issues);
        }
        if (enabledProviders.contains(ProviderIds.BIOMES)) {
            builder.biomes = captureKeyed(RegistryKey.BIOME, "biome", issues);
        }
        if (enabledProviders.contains(ProviderIds.STRUCTURES)) {
            builder.structures = captureKeyed(RegistryKey.STRUCTURE, "structure", issues);
        }
        if (enabledProviders.contains(ProviderIds.PARTICLES)) {
            builder.particles = captureKeyed(RegistryKey.PARTICLE_TYPE, "particle", issues);
        }
        if (enabledProviders.contains(ProviderIds.SOUNDS)) {
            builder.sounds = captureSounds(issues);
        }
        if (enabledProviders.contains(ProviderIds.ATTRIBUTES)) {
            builder.attributes = captureAttributes(issues);
        }
        if (enabledProviders.contains(ProviderIds.DAMAGE_TYPES)) {
            builder.damageTypes = captureKeyed(RegistryKey.DAMAGE_TYPE, "damage type", issues);
        }
        if (enabledProviders.contains(ProviderIds.GAME_EVENTS)) {
            builder.gameEvents = captureKeyed(RegistryKey.GAME_EVENT, "game event", issues);
        }
        if (enabledProviders.contains(ProviderIds.VILLAGER_PROFESSIONS)) {
            builder.villagerProfessions = captureKeyed(RegistryKey.VILLAGER_PROFESSION, "villager profession", issues);
        }
        if (enabledProviders.contains(ProviderIds.GAMERULES)) {
            builder.gamerules = captureGameRules(issues);
        }
        if (enabledProviders.contains(ProviderIds.COMMANDS)) {
            builder.captureCommands(issues);
        }
        if (enabledProviders.contains(ProviderIds.DIMENSIONS)) {
            builder.captureDimensions(issues);
        }
        if (enabledProviders.contains(ProviderIds.RECIPES)) {
            builder.captureRecipes(issues);
        }
        if (enabledProviders.contains(ProviderIds.ADVANCEMENTS)) {
            builder.captureAdvancements(issues);
        }
        return new RegistrySnapshot(builder);
    }

    private static String safeVersion() {
        try {
            String version = Bukkit.getMinecraftVersion();
            return version == null ? "" : version;
        } catch (Throwable error) {
            return "";
        }
    }

    // ---------------------------------------------------------------- materials

    private static final class Builder {

        private String minecraftVersion = "";
        private final Map<String, MaterialInfo> materials = new TreeMap<>();
        private final Map<String, EntityInfo> entities = new TreeMap<>();
        private Map<String, RegistryEntry> enchantments = Map.of();
        private Map<String, RegistryEntry> effects = Map.of();
        private Map<String, RegistryEntry> potions = Map.of();
        private Map<String, RegistryEntry> biomes = Map.of();
        private Map<String, RegistryEntry> structures = Map.of();
        private Map<String, RegistryEntry> particles = Map.of();
        private Map<String, RegistryEntry> sounds = Map.of();
        private Map<String, RegistryEntry> attributes = Map.of();
        private Map<String, RegistryEntry> damageTypes = Map.of();
        private Map<String, RegistryEntry> gameEvents = Map.of();
        private Map<String, RegistryEntry> villagerProfessions = Map.of();
        private Map<String, RegistryEntry> advancements = Map.of();
        private Map<String, RegistryEntry> gamerules = Map.of();
        private final List<TagInfo> tags = new ArrayList<>();
        private final List<CommandInfo> commands = new ArrayList<>();
        private final List<DimensionInfo> dimensions = new ArrayList<>();
        private final Map<String, List<RecipeInfo>> recipesByResult = new LinkedHashMap<>();
        private long captureMillis;

        private void captureMaterials(ConfigIssues issues) {
            Map<String, List<String>> tagsByMaterial = new LinkedHashMap<>();
            long start = System.nanoTime();
            try {
                for (Tag<Material> tag : materialTags()) {
                    String tagKey = keyOf(tag.getKey());
                    try {
                        for (Material value : tag.getValues()) {
                            if (value != null && !value.isLegacy()) {
                                tagsByMaterial.computeIfAbsent(value.getKey().getKey(),
                                        k -> new ArrayList<>()).add(tagKey);
                            }
                        }
                    } catch (Throwable error) {
                        issues.add(ConfigIssue.warning("registry", "material tags",
                                "tag '" + tagKey + "' could not be read: " + error));
                    }
                }
            } catch (Throwable error) {
                issues.add(ConfigIssue.warning("registry", "material tags",
                        "could not enumerate material tags: " + error));
            }

            for (Material material : Material.values()) {
                if (material.isLegacy() || material.isAir()) {
                    continue;
                }
                boolean block = safe(() -> material.isBlock(), false);
                boolean item = safe(() -> material.isItem(), false);
                if (!block && !item) {
                    continue;
                }
                String key = material.getKey().getKey();
                List<String> materialTags = tagsByMaterial.getOrDefault(key, List.of());
                Collections.sort(materialTags);
                materials.put(key, new MaterialInfo(key, material, block, item,
                        materialFacts(material, block, item), List.copyOf(materialTags)));
            }
            captureMillis += (System.nanoTime() - start) / 1_000_000L;
        }

        @SuppressWarnings("unchecked")
        private Iterable<Tag<Material>> materialTags() {
            List<Tag<Material>> out = new ArrayList<>();
            for (String registry : List.of(Tag.REGISTRY_BLOCKS, Tag.REGISTRY_ITEMS)) {
                try {
                    for (Tag<Material> tag : Bukkit.getTags(registry, Material.class)) {
                        out.add(tag);
                    }
                } catch (Throwable ignored) {
                    // a server without that tag registry simply contributes nothing
                }
            }
            return out;
        }

        private Map<String, String> materialFacts(Material material, boolean block, boolean item) {
            Facts facts = new Facts();
            if (block && item) {
                facts.put("kind", "Block and item");
            } else if (block) {
                facts.put("kind", "Block");
            } else {
                facts.put("kind", "Item");
            }
            int stack = safe(material::getMaxStackSize, 0);
            if (stack > 0) {
                facts.put("stack_size", Integer.toString(stack));
            }
            short durability = safe(material::getMaxDurability, (short) 0);
            if (durability > 0) {
                facts.put("durability", Integer.toString(durability));
            }
            if (block) {
                float hardness = safe(material::getHardness, Float.NaN);
                if (!Float.isNaN(hardness)) {
                    facts.put("hardness", Facts.trim(hardness));
                }
                float blast = safe(material::getBlastResistance, Float.NaN);
                if (!Float.isNaN(blast)) {
                    facts.put("blast_resistance", Facts.trim(blast));
                }
                float slipperiness = safe(material::getSlipperiness, Float.NaN);
                if (!Float.isNaN(slipperiness)) {
                    facts.put("slipperiness", Facts.trim(slipperiness));
                }
            }
            facts.when("edible", "Yes", safe(material::isEdible, false));
            facts.when("fuel", "Yes", safe(material::isFuel, false));
            facts.when("flammable", "Yes", safe(material::isFlammable, false));
            facts.when("burnable", "Yes", safe(material::isBurnable, false));
            facts.when("solid", "Yes", safe(material::isSolid, false));
            facts.when("transparent", "Yes", safe(material::isTransparent, false));
            facts.when("occluding", "Yes", safe(material::isOccluding, false));
            facts.when("gravity", "Yes", safe(material::hasGravity, false));
            facts.when("interactable", "Yes", safe(material::isInteractable, false));
            facts.when("music_disc", "Yes", safe(material::isRecord, false));
            facts.when("compostable", "Yes", safe(material::isCompostable, false));
            Material remaining = safe(material::getCraftingRemainingItem, null);
            if (remaining != null && remaining != Material.AIR) {
                facts.put("crafting_remaining", Text.prettify(remaining.getKey().getKey()));
            }
            if (item) {
                org.bukkit.inventory.EquipmentSlot slot = safe(material::getEquipmentSlot, null);
                if (slot != null) {
                    facts.put("equipment_slot", Text.prettify(slot.name().toLowerCase(Locale.ROOT)));
                }
            }
            String translationKey = block
                    ? safe(material::getBlockTranslationKey, null)
                    : safe(material::getItemTranslationKey, null);
            facts.put("translation_key", translationKey);
            return facts.build();
        }

        private void captureEntities(ConfigIssues issues) {
            Map<String, RegistryEntry> attributeRegistry = captureAttributes(issues);
            for (EntityType type : iterate(Registry.ENTITY_TYPE, issues, "entity type")) {
                String key = keyOf(type.getKey());
                Facts facts = new Facts();
                facts.put("type_id", Integer.toString(safe(() -> (int) type.getTypeId(), 0)));
                facts.when("alive", "Yes", safe(type::isAlive, false));
                facts.when("spawnable", "Yes", safe(type::isSpawnable, false));
                facts.put("spawn_category", Text.prettify(safe(() -> type.getSpawnCategory().name(), "")));
                Class<?> entityClass = safe(type::getEntityClass, null);
                if (entityClass != null) {
                    facts.put("entity_class", entityClass.getSimpleName());
                }
                facts.put("translation_key", safe(type::getTranslationKey, null));
                List<AttributeValue> defaults = new ArrayList<>();
                try {
                    if (safe(type::hasDefaultAttributes, false)) {
                        Attributable attributable = type.getDefaultAttributes();
                        for (RegistryEntry attribute : attributeRegistry.values()) {
                            Attribute real = lookupAttribute(attribute.key());
                            if (real == null) {
                                continue;
                            }
                            AttributeInstance instance = attributable.getAttribute(real);
                            if (instance != null) {
                                defaults.add(new AttributeValue(attribute.key(), attribute.label(),
                                        instance.getBaseValue()));
                            }
                        }
                    }
                } catch (Throwable error) {
                    issues.add(ConfigIssue.warning("registry", "entity type " + key,
                            "default attributes could not be read: " + error));
                }
                entities.put(key, new EntityInfo(key, Text.prettify(key),
                        safe(type::isAlive, false), safe(type::isSpawnable, false),
                        safe(() -> type.getSpawnCategory().name(), ""), facts.build(), List.copyOf(defaults)));
            }
        }

        private void captureTags(ConfigIssues issues) {
            Map<String, String> registries = new LinkedHashMap<>();
            registries.put(Tag.REGISTRY_BLOCKS, "blocks");
            registries.put(Tag.REGISTRY_ITEMS, "items");
            registries.put(Tag.REGISTRY_ENTITY_TYPES, "entity types");
            registries.put(Tag.REGISTRY_FLUIDS, "fluids");
            registries.put(Tag.REGISTRY_GAME_EVENTS, "game events");
            for (Map.Entry<String, String> entry : registries.entrySet()) {
                try {
                    for (Tag<org.bukkit.Keyed> tag : Bukkit.getTags(entry.getKey(), org.bukkit.Keyed.class)) {
                        List<String> values = new ArrayList<>();
                        try {
                            for (org.bukkit.Keyed value : tag.getValues()) {
                                values.add(keyOf(value.getKey()));
                            }
                        } catch (Throwable error) {
                            issues.add(ConfigIssue.warning("registry", "tag " + entry.getValue(),
                                    "values could not be read: " + error));
                        }
                        Collections.sort(values);
                        tags.add(new TagInfo(entry.getValue(), keyOf(tag.getKey()), List.copyOf(values)));
                    }
                } catch (Throwable error) {
                    issues.add(ConfigIssue.warning("registry", "tags",
                            entry.getValue() + " tags could not be enumerated: " + error));
                }
            }
            tags.sort((a, b) -> a.key().compareTo(b.key()));
        }

        private void captureCommands(ConfigIssues issues) {
            try {
                CommandMap map = Bukkit.getCommandMap();
                Map<String, Command> known = map.getKnownCommands();
                Map<String, CommandInfo> byName = new TreeMap<>();
                for (Command command : known.values()) {
                    if (command == null) {
                        continue;
                    }
                    String name = command.getName();
                    if (name == null || name.isEmpty() || byName.containsKey(name)) {
                        continue;
                    }
                    List<String> aliases = new ArrayList<>(command.getAliases());
                    Collections.sort(aliases);
                    String permission = command.getPermission();
                    byName.put(name, new CommandInfo(name, List.copyOf(aliases),
                            permission == null ? "" : permission,
                            command.getDescription() == null ? "" : command.getDescription(),
                            command.getUsage() == null ? "" : command.getUsage()));
                }
                commands.addAll(byName.values());
            } catch (Throwable error) {
                issues.add(ConfigIssue.warning("registry", "commands",
                        "the command map could not be read: " + error));
            }
        }

        private void captureDimensions(ConfigIssues issues) {
            for (World.Environment environment : World.Environment.values()) {
                String key = environment.name().toLowerCase(Locale.ROOT);
                Facts facts = new Facts();
                facts.put("environment", Text.prettify(key));
                dimensions.add(new DimensionInfo(key, Text.prettify(key), facts.build()));
            }
        }

        private void captureRecipes(ConfigIssues issues) {
            long start = System.nanoTime();
            int total;
            int skipped;
            try {
                Iterator<Recipe> iterator = Bukkit.recipeIterator();
                total = 0;
                skipped = 0;
                while (iterator.hasNext()) {
                    Recipe recipe = iterator.next();
                    total++;
                    RecipeInfo info = describe(recipe);
                    if (info == null) {
                        skipped++;
                        continue;
                    }
                    recipesByResult.computeIfAbsent(info.resultKey(), k -> new ArrayList<>()).add(info);
                }
            } catch (Throwable error) {
                issues.add(ConfigIssue.warning("registry", "recipes",
                        "the recipe book could not be read: " + error));
                return;
            }
            recipesByResult.values().forEach(list -> list.sort((a, b) -> a.typeKey().compareTo(b.typeKey())));
            captureMillis += (System.nanoTime() - start) / 1_000_000L;
        }

        private void captureAdvancements(ConfigIssues issues) {
            Map<String, RegistryEntry> out = new TreeMap<>();
            try {
                Iterator<org.bukkit.advancement.Advancement> iterator = Bukkit.advancementIterator();
                while (iterator.hasNext()) {
                    org.bukkit.advancement.Advancement advancement = iterator.next();
                    if (advancement == null || advancement.getKey() == null) {
                        continue;
                    }
                    String key = keyOf(advancement.getKey());
                    Facts facts = new Facts();
                    facts.put("namespace", advancement.getKey().getNamespace());
                    out.put(key, new RegistryEntry(key, Text.prettify(advancement.getKey().getKey()), facts.build()));
                }
            } catch (Throwable error) {
                issues.add(ConfigIssue.warning("registry", "advancements",
                        "advancements could not be read (datapacks may not be loaded yet): " + error));
            }
            advancements = out;
        }

        private RecipeInfo describe(Recipe recipe) {
            ItemStack result = safe(recipe::getResult, null);
            if (result == null || result.getType().isAir()) {
                return null;
            }
            String resultKey = result.getType().getKey().getKey();
            List<List<String>> grid = new ArrayList<>();
            Facts facts = new Facts();
            if (recipe instanceof ShapedRecipe shaped) {
                String[] shape = shaped.getShape();
                Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
                for (String row : shape) {
                    List<String> cells = new ArrayList<>();
                    for (int i = 0; i < row.length(); i++) {
                        RecipeChoice choice = choices.get(row.charAt(i));
                        List<String> options = materialsOf(choice);
                        cells.add(options.isEmpty() ? "" : options.get(0));
                    }
                    grid.add(List.copyOf(cells));
                }
                facts.put("recipe_shape", String.join(" / ", shape));
                return new RecipeInfo(resultKey, "shaped", List.copyOf(grid), facts.build());
            }
            if (recipe instanceof ShapelessRecipe shapeless) {
                List<String> cells = new ArrayList<>();
                for (RecipeChoice choice : shapeless.getChoiceList()) {
                    List<String> options = materialsOf(choice);
                    cells.add(options.isEmpty() ? "" : options.get(0));
                }
                grid.add(List.copyOf(cells));
                return new RecipeInfo(resultKey, "shapeless", List.copyOf(grid), facts.build());
            }
            if (recipe instanceof CookingRecipe<?> cooking) {
                grid.add(List.copyOf(materialsOf(cooking.getInputChoice())));
                facts.put("cook_time_ticks", Integer.toString(cooking.getCookingTime()));
                facts.put("recipe_group", cooking.getGroup());
                facts.number("experience", cooking.getExperience());
                return new RecipeInfo(resultKey, cookingKind(recipe), List.copyOf(grid), facts.build());
            }
            if (recipe instanceof StonecuttingRecipe stonecutting) {
                grid.add(List.copyOf(materialsOf(stonecutting.getInputChoice())));
                return new RecipeInfo(resultKey, "stonecutting", List.copyOf(grid), facts.build());
            }
            if (recipe instanceof SmithingRecipe smithing) {
                List<String> cells = new ArrayList<>();
                cells.addAll(materialsOf(templateOf(smithing)));
                cells.addAll(materialsOf(safe(() -> smithing.getBase(), (RecipeChoice) null)));
                cells.addAll(materialsOf(safe(() -> smithing.getAddition(), (RecipeChoice) null)));
                grid.add(List.copyOf(cells));
                return new RecipeInfo(resultKey, "smithing", List.copyOf(grid), facts.build());
            }
            return null;
        }

        /** Only the transform and trim variants of a smithing recipe have a template. */
        private RecipeChoice templateOf(SmithingRecipe recipe) {
            try {
                if (recipe instanceof org.bukkit.inventory.SmithingTransformRecipe transform) {
                    return transform.getTemplate();
                }
                if (recipe instanceof org.bukkit.inventory.SmithingTrimRecipe trim) {
                    return trim.getTemplate();
                }
            } catch (Throwable ignored) {
                // a smithing variant without a template simply has none
            }
            return null;
        }

        private String cookingKind(Recipe recipe) {
            String name = recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            return name.replace("recipe", "");
        }

        private List<String> materialsOf(RecipeChoice choice) {
            if (choice == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            try {
                if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
                    for (Material material : materialChoice.getChoices()) {
                        out.add(material.getKey().getKey());
                    }
                } else if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
                    for (ItemStack stack : exactChoice.getChoices()) {
                        out.add(stack.getType().getKey().getKey());
                    }
                }
            } catch (Throwable ignored) {
                // a choice type we cannot inspect contributes no ingredients
            }
            return out;
        }
    }

    // ---------------------------------------------------------------- registries

    private static Map<String, RegistryEntry> captureEnchantments(ConfigIssues issues) {
        Map<String, RegistryEntry> out = new TreeMap<>();
        for (Enchantment enchantment : iterate(Registry.ENCHANTMENT, issues, "enchantment")) {
            String key = keyOf(enchantment.getKey());
            Facts facts = new Facts();
            facts.put("max_level", Integer.toString(safe(enchantment::getMaxLevel, 0)));
            facts.put("start_level", Integer.toString(safe(enchantment::getStartLevel, 0)));
            EnchantmentTarget target = safe(enchantment::getItemTarget, null);
            if (target != null) {
                facts.put("item_target", Text.prettify(target.name().toLowerCase(Locale.ROOT)));
            }
            facts.when("treasure", "Yes", safe(enchantment::isTreasure, false));
            facts.when("cursed", "Yes", safe(enchantment::isCursed, false));
            facts.when("tradeable", "Yes", safe(enchantment::isTradeable, false));
            facts.when("discoverable", "Yes", safe(enchantment::isDiscoverable, false));
            out.put(key, new RegistryEntry(key, Text.prettify(key), facts.build()));
        }
        return out;
    }

    private static Map<String, RegistryEntry> captureEffects(ConfigIssues issues) {
        Map<String, RegistryEntry> out = new TreeMap<>();
        for (PotionEffectType effect : iterate(Registry.MOB_EFFECT, issues, "mob effect")) {
            String key = keyOf(effect.getKey());
            Facts facts = new Facts();
            facts.when("instant", "Yes", safe(effect::isInstant, false));
            facts.put("category", Text.prettify(safe(() -> effect.getCategory().name(), "")));
            facts.number("duration_modifier", safe(effect::getDurationModifier, 0d));
            org.bukkit.Color color = safe(effect::getColor, null);
            if (color != null) {
                facts.put("colour", String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
            }
            out.put(key, new RegistryEntry(key, Text.prettify(key), facts.build()));
        }
        return out;
    }

    private static Map<String, RegistryEntry> captureAttributes(ConfigIssues issues) {
        Map<String, RegistryEntry> out = new TreeMap<>();
        for (Attribute attribute : iterate(Registry.ATTRIBUTE, issues, "attribute")) {
            String key = keyOf(attribute.getKey());
            Facts facts = new Facts();
            facts.number("default_value", safe(attribute::getDefaultValue, 0d));
            facts.put("sentiment", Text.prettify(safe(() -> attribute.getSentiment().name(), "")));
            out.put(key, new RegistryEntry(key, Text.prettify(key), facts.build()));
        }
        return out;
    }

    private static Map<String, RegistryEntry> captureSounds(ConfigIssues issues) {
        Map<String, RegistryEntry> out = new TreeMap<>();
        for (org.bukkit.Sound sound : iterate(Registry.SOUNDS, issues, "sound")) {
            String key = keyOf(sound.getKey());
            out.put(key, new RegistryEntry(key, Text.prettify(key), new Facts().build()));
        }
        return out;
    }

    private static <T extends org.bukkit.Keyed> Map<String, RegistryEntry> captureKeyed(
            RegistryKey<T> registryKey, String what, ConfigIssues issues) {
        Map<String, RegistryEntry> out = new TreeMap<>();
        try {
            Registry<T> registry = RegistryAccess.registryAccess().getRegistry(registryKey);
            if (registry == null) {
                issues.add(ConfigIssue.warning("registry", what, "the registry is not present on this server"));
                return out;
            }
            for (T value : registry) {
                if (value == null || value.getKey() == null) {
                    continue;
                }
                String key = keyOf(value.getKey());
                out.put(key, new RegistryEntry(key, Text.prettify(value.getKey().getKey()), Map.of()));
            }
        } catch (Throwable error) {
            issues.add(ConfigIssue.warning("registry", what, "could not be read: " + error));
        }
        return out;
    }

    /** Enumerates the game rules by reflecting over the public {@code GameRules} constants. */
    private static Map<String, RegistryEntry> captureGameRules(ConfigIssues issues) {
        Map<String, RegistryEntry> out = new TreeMap<>();
        try {
            Class<?> rulesClass = Class.forName("org.bukkit.GameRules");
            for (java.lang.reflect.Field field : rulesClass.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!"org.bukkit.GameRule".equals(field.getType().getName())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object rule = field.get(null);
                    if (!(rule instanceof org.bukkit.Keyed keyed) || keyed.getKey() == null) {
                        continue;
                    }
                    String key = keyOf(keyed.getKey());
                    Facts facts = new Facts();
                    String generic = field.getGenericType().getTypeName();
                    int open = generic.indexOf('<');
                    int close = generic.lastIndexOf('>');
                    if (open >= 0 && close > open) {
                        String type = generic.substring(open + 1, close);
                        int dot = type.lastIndexOf('.');
                        facts.put("value_type", dot < 0 ? type : type.substring(dot + 1));
                    }
                    if (rule instanceof net.kyori.adventure.translation.Translatable translatable) {
                        facts.put("translation_key", translatable.translationKey());
                    }
                    out.put(key, new RegistryEntry(key, Text.prettify(keyed.getKey().getKey()), facts.build()));
                } catch (Throwable error) {
                    issues.add(ConfigIssue.warning("registry", "game rule " + field.getName(),
                            "could not be read: " + error));
                }
            }
        } catch (Throwable error) {
            issues.add(ConfigIssue.warning("registry", "gamerules",
                    "the game rule list could not be read: " + error));
        }
        return out;
    }

    private static Attribute lookupAttribute(String key) {
        try {
            Registry<Attribute> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE);
            return registry == null ? null : registry.get(NamespacedKey.minecraft(key));
        } catch (Throwable error) {
            return null;
        }
    }

    private static <T extends org.bukkit.Keyed> List<T> iterate(Registry<T> registry, ConfigIssues issues, String what) {
        List<T> out = new ArrayList<>();
        if (registry == null) {
            issues.add(ConfigIssue.warning("registry", what, "the registry is not present on this server"));
            return out;
        }
        try {
            for (T value : registry) {
                if (value != null && value.getKey() != null) {
                    out.add(value);
                }
            }
        } catch (Throwable error) {
            issues.add(ConfigIssue.warning("registry", what, "could not be read: " + error));
        }
        return out;
    }

    private static String keyOf(NamespacedKey key) {
        if (key == null) {
            return "";
        }
        return "minecraft".equals(key.getNamespace()) ? key.getKey() : key.getNamespace() + ":" + key.getKey();
    }

    private static <T> T safe(java.util.function.Supplier<T> supplier, T fallback) {
        try {
            T value = supplier.get();
            return value == null ? fallback : value;
        } catch (Throwable error) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- accessors

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public Map<String, MaterialInfo> materials() {
        return materials;
    }

    public MaterialInfo material(String key) {
        return key == null ? null : materials.get(Text.stripNamespace(Text.fold(key)));
    }

    public Map<String, EntityInfo> entities() {
        return entities;
    }

    public EntityInfo entity(String key) {
        return key == null ? null : entities.get(Text.stripNamespace(Text.fold(key)));
    }

    public Map<String, RegistryEntry> enchantments() {
        return enchantments;
    }

    public Map<String, RegistryEntry> effects() {
        return effects;
    }

    public Map<String, RegistryEntry> potions() {
        return potions;
    }

    public Map<String, RegistryEntry> biomes() {
        return biomes;
    }

    public Map<String, RegistryEntry> structures() {
        return structures;
    }

    public Map<String, RegistryEntry> particles() {
        return particles;
    }

    public Map<String, RegistryEntry> sounds() {
        return sounds;
    }

    public Map<String, RegistryEntry> attributes() {
        return attributes;
    }

    public Map<String, RegistryEntry> damageTypes() {
        return damageTypes;
    }

    public Map<String, RegistryEntry> gameEvents() {
        return gameEvents;
    }

    public Map<String, RegistryEntry> villagerProfessions() {
        return villagerProfessions;
    }

    public Map<String, RegistryEntry> advancements() {
        return advancements;
    }

    public Map<String, RegistryEntry> gamerules() {
        return gamerules;
    }

    public List<TagInfo> tags() {
        return tags;
    }

    public List<CommandInfo> commands() {
        return commands;
    }

    public List<DimensionInfo> dimensions() {
        return dimensions;
    }

    public List<RecipeInfo> recipesFor(String resultKey) {
        return recipesByResult.getOrDefault(resultKey == null ? "" : Text.fold(resultKey), List.of());
    }

    /** Every result material that has at least one recipe, sorted. */
    public java.util.Set<String> recipeResults() {
        return recipesByResult.keySet();
    }

    public int recipeCount() {
        int count = 0;
        for (List<RecipeInfo> list : recipesByResult.values()) {
            count += list.size();
        }
        return count;
    }

    /** Wall clock milliseconds spent capturing, for the startup summary. */
    public long captureMillis() {
        return captureMillis;
    }
}
