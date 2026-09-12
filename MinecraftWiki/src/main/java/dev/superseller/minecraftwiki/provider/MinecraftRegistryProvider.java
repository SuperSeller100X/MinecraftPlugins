package dev.superseller.minecraftwiki.provider;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;

import dev.superseller.minecraftwiki.article.ArticleBuilder;
import dev.superseller.minecraftwiki.article.ArticleId;
import dev.superseller.minecraftwiki.article.ArticleKind;
import dev.superseller.minecraftwiki.util.Text;

/**
 * Base class for providers that turn {@link RegistrySnapshot} data into articles.
 *
 * <p>Providers read only the immutable snapshot, so they are safe to run while the search
 * index is rebuilt off the main thread.</p>
 */
public abstract class MinecraftRegistryProvider implements ArticleProvider {

    /** Cap on how many informational tags an article lists, to keep entries readable. */
    protected static final int MAX_DISPLAYED_TAGS = 8;

    protected final RegistrySnapshot snapshot;
    protected final String version;

    protected MinecraftRegistryProvider(RegistrySnapshot snapshot, String version) {
        this.snapshot = snapshot;
        this.version = version == null ? "" : version;
    }

    @Override
    public abstract Set<String> categories();

    /** Starts an article with the conventional id, title and version metadata. */
    protected ArticleBuilder newArticle(String categoryId, ArticleKind kind, String key, Material icon) {
        ArticleId id = ArticleId.ofCategory(categoryId, key);
        return ArticleBuilder.create(id, categoryId, kind)
                .title(Text.prettify(key))
                .icon(icon)
                .version(version)
                .keyword(Text.stripNamespace(key))
                .keyword(Text.fold(key).replace("_", "").replace("-", ""));
    }

    /** Sensible icon when a registry entry has no obvious item representation. */
    protected static Material defaultIcon(ArticleKind kind) {
        return switch (kind) {
            case BLOCK, ITEM -> Material.PAPER;
            case ENTITY -> Material.CREEPER_HEAD;
            case ENCHANTMENT -> Material.ENCHANTED_BOOK;
            case EFFECT, POTION -> Material.POTION;
            case BIOME -> Material.OAK_SAPLING;
            case DIMENSION -> Material.END_PORTAL_FRAME;
            case STRUCTURE -> Material.CHISELED_STONE_BRICKS;
            case SOUND -> Material.NOTE_BLOCK;
            case PARTICLE -> Material.GLOWSTONE_DUST;
            case ATTRIBUTE -> Material.GOLDEN_CARROT;
            case DAMAGE_TYPE -> Material.IRON_SWORD;
            case GAME_EVENT -> Material.SCULK_SENSOR;
            case GAMERULE -> Material.COMPARATOR;
            case COMMAND -> Material.COMMAND_BLOCK;
            case TAG -> Material.STRING;
            case VILLAGER_PROFESSION -> Material.EMERALD;
            case RECIPE -> Material.KNOWLEDGE_BOOK;
            case ADVANCEMENT -> Material.GOLD_BLOCK;
            case CUSTOM -> Material.BOOK;
        };
    }

    /** The spawn egg for an entity type, or null when it has none. */
    protected static Material spawnEgg(String entityKey) {
        if (entityKey == null || entityKey.isBlank()) {
            return null;
        }
        Material egg = Material.matchMaterial(entityKey.toLowerCase(Locale.ROOT) + "_spawn_egg");
        return egg != null && egg.isItem() && !egg.isLegacy() ? egg : null;
    }

    /** Lower-cased, namespace-stripped key used for ids and lookups. */
    protected static String normalise(String key) {
        return Text.fold(Text.stripNamespace(key));
    }
}
