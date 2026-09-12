package dev.superseller.minecraftwiki.provider;

import java.util.Map;
import java.util.Set;

import org.bukkit.Material;

import dev.superseller.minecraftwiki.article.ArticleKind;
import dev.superseller.minecraftwiki.article.ArticleRef;
import dev.superseller.minecraftwiki.util.Text;

/**
 * Articles for the material registry, scoped to blocks, to items, or to both.
 *
 * <p>Every fact shown comes from {@link Material} itself: hardness, blast resistance, stack
 * size, fuel, edibility, tags and so on. Nothing is estimated.</p>
 */
public final class MaterialProvider extends MinecraftRegistryProvider {

    /** Which slice of the material registry this provider covers. */
    public enum Mode {
        BLOCKS, ITEMS, ALL
    }

    private final String id;
    private final String categoryId;
    private final Mode mode;

    private MaterialProvider(String id, String categoryId, Mode mode, RegistrySnapshot snapshot, String version) {
        super(snapshot, version);
        this.id = id;
        this.categoryId = categoryId;
        this.mode = mode;
    }

    public static MaterialProvider blocks(RegistrySnapshot snapshot, String version) {
        return new MaterialProvider(ProviderIds.BLOCKS, "blocks", Mode.BLOCKS, snapshot, version);
    }

    public static MaterialProvider items(RegistrySnapshot snapshot, String version) {
        return new MaterialProvider(ProviderIds.ITEMS, "items", Mode.ITEMS, snapshot, version);
    }

    public static MaterialProvider combined(RegistrySnapshot snapshot, String version) {
        return new MaterialProvider("materials", "materials", Mode.ALL, snapshot, version);
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
        for (Map.Entry<String, RegistrySnapshot.MaterialInfo> entry : snapshot.materials().entrySet()) {
            RegistrySnapshot.MaterialInfo info = entry.getValue();
            boolean include = switch (mode) {
                case BLOCKS -> info.block();
                case ITEMS -> info.item() && !info.block();
                case ALL -> true;
            };
            if (!include) {
                continue;
            }
            Material material = info.material();
            ArticleKind kind = switch (mode) {
                case BLOCKS -> ArticleKind.BLOCK;
                case ITEMS -> ArticleKind.ITEM;
                case ALL -> info.block() ? ArticleKind.BLOCK : ArticleKind.ITEM;
            };
            var builder = newArticle(categoryId, kind, info.key(), material)
                    .summary(Text.prettify(info.key()))
                    .order(0);
            for (String tag : info.tags()) {
                builder.keyword(tag).tag(tag);
            }
            if (!snapshot.recipesFor(info.key()).isEmpty()) {
                builder.ref(ArticleRef.recipe(info.key()));
            }
            sink.accept(builder.build());
        }
    }
}
