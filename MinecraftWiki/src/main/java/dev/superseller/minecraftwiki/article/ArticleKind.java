package dev.superseller.minecraftwiki.article;

/**
 * What kind of Minecraft object an article documents.
 *
 * <p>The kind selects the {@code content} resolver that produces the article body from
 * the live server data, so no article text is ever hardcoded in this plugin.</p>
 */
public enum ArticleKind {

    /** A placeable block. */
    BLOCK("Block"),
    /** A usable item. */
    ITEM("Item"),
    /** An entity / mob type. */
    ENTITY("Entity"),
    /** An enchantment. */
    ENCHANTMENT("Enchantment"),
    /** A status effect. */
    EFFECT("Status effect"),
    /** A potion type. */
    POTION("Potion"),
    /** A biome. */
    BIOME("Biome"),
    /** A world dimension. */
    DIMENSION("Dimension"),
    /** A generated structure. */
    STRUCTURE("Structure"),
    /** A sound event. */
    SOUND("Sound"),
    /** A particle. */
    PARTICLE("Particle"),
    /** An entity attribute. */
    ATTRIBUTE("Attribute"),
    /** A damage type. */
    DAMAGE_TYPE("Damage type"),
    /** A game event. */
    GAME_EVENT("Game event"),
    /** A game rule. */
    GAMERULE("Game rule"),
    /** A vanilla or plugin command. */
    COMMAND("Command"),
    /** A registry tag. */
    TAG("Tag"),
    /** A villager profession. */
    VILLAGER_PROFESSION("Villager profession"),
    /** A crafting / cooking recipe. */
    RECIPE("Recipe"),
    /** An advancement. */
    ADVANCEMENT("Advancement"),
    /** Content supplied by the server owner through articles.yml. */
    CUSTOM("Article");

    private final String label;

    ArticleKind(String label) {
        this.label = label;
    }

    /** Human readable name used in article headers and search filters. */
    public String label() {
        return label;
    }
}
