package dev.superseller.minecraftwiki.provider;

import java.util.Map;
import java.util.Set;

import org.bukkit.Material;

import dev.superseller.minecraftwiki.article.ArticleKind;

/**
 * One provider for any registry whose entries need nothing but their key and facts.
 *
 * <p>Enchantments, effects, potions, biomes, structures, particles, sounds, attributes,
 * damage types, game events, villager professions and advancements all share this shape,
 * so they share one implementation instead of eleven near-identical classes.</p>
 */
public final class RegistryArticleProvider extends MinecraftRegistryProvider {

    private final String id;
    private final String categoryId;
    private final ArticleKind kind;
    private final Material icon;
    private final Map<String, RegistryEntry> entries;

    public RegistryArticleProvider(String id, String categoryId, ArticleKind kind, Material icon,
                                   Map<String, RegistryEntry> entries, RegistrySnapshot snapshot, String version) {
        super(snapshot, version);
        this.id = id;
        this.categoryId = categoryId;
        this.kind = kind;
        this.icon = icon == null ? defaultIcon(kind) : icon;
        this.entries = entries == null ? Map.of() : entries;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<String> categories() {
        return Set.of(categoryId);
    }

    @Override
    public void contribute(ArticleSink sink) {
        for (RegistryEntry entry : entries.values()) {
            sink.accept(newArticle(categoryId, kind, entry.key(), icon)
                    .title(entry.label())
                    .tags(entry.properties().values().stream().limit(MAX_DISPLAYED_TAGS).toList())
                    .build());
        }
    }
}
