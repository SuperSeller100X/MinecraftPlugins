package dev.superseller.minecraftwiki.provider;

import java.util.Set;

import org.bukkit.Material;

import dev.superseller.minecraftwiki.article.ArticleKind;
import dev.superseller.minecraftwiki.article.ArticleRef;

/**
 * Articles for the entity type registry.
 *
 * <p>Only facts the API actually exposes are used: whether the entity is alive, spawnable,
 * its spawn category, its implementation class and the default attribute values the server
 * reports for that type. No statistic is invented.</p>
 */
public final class EntityProvider extends MinecraftRegistryProvider {

    private final String id;
    private final String categoryId;
    private final boolean livingOnly;

    private EntityProvider(String id, String categoryId, boolean livingOnly,
                           RegistrySnapshot snapshot, String version) {
        super(snapshot, version);
        this.id = id;
        this.categoryId = categoryId;
        this.livingOnly = livingOnly;
    }

    public static EntityProvider mobs(RegistrySnapshot snapshot, String version) {
        return new EntityProvider(ProviderIds.ENTITIES, "mobs", true, snapshot, version);
    }

    public static EntityProvider all(RegistrySnapshot snapshot, String version) {
        return new EntityProvider("entity-types", "entities", false, snapshot, version);
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
        for (RegistrySnapshot.EntityInfo info : snapshot.entities().values()) {
            if (livingOnly && !info.alive()) {
                continue;
            }
            Material icon = spawnEgg(info.key());
            if (icon == null) {
                icon = defaultIcon(ArticleKind.ENTITY);
            }
            var builder = newArticle(categoryId, ArticleKind.ENTITY, info.key(), icon)
                    .title(info.label())
                    .summary(info.label());
            for (RegistrySnapshot.AttributeValue attribute : info.attributes()) {
                builder.keyword(attribute.key());
            }
            builder.ref(ArticleRef.entity(info.key()));
            sink.accept(builder.build());
        }
    }
}
