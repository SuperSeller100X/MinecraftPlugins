package dev.superseller.minecraftwiki.provider;

import java.util.List;
import java.util.Set;

import org.bukkit.Material;

import dev.superseller.minecraftwiki.article.ArticleKind;
import dev.superseller.minecraftwiki.article.ArticleRef;
import dev.superseller.minecraftwiki.util.Text;

/**
 * One article per item that the server has at least one recipe for.
 *
 * <p>The article body is rendered from the real recipe book, and every recipe for the same
 * result is reachable through the recipe viewer.</p>
 */
public final class RecipeProvider extends MinecraftRegistryProvider {

    private final String categoryId;

    public RecipeProvider(String categoryId, RegistrySnapshot snapshot, String version) {
        super(snapshot, version);
        this.categoryId = categoryId;
    }

    @Override
    public String id() {
        return ProviderIds.RECIPES;
    }

    @Override
    public Set<String> categories() {
        return Set.of(categoryId);
    }

    @Override
    public void contribute(ArticleSink sink) {
        for (String resultKey : snapshot.recipeResults()) {
            List<RegistrySnapshot.RecipeInfo> recipes = snapshot.recipesFor(resultKey);
            if (recipes.isEmpty()) {
                continue;
            }
            Material material = Material.matchMaterial(resultKey);
            Material icon = material != null && material.isItem() ? material : defaultIcon(ArticleKind.RECIPE);
            var builder = newArticle(categoryId, ArticleKind.RECIPE, resultKey, icon)
                    .title(Text.prettify(resultKey))
                    .summary(Text.prettify(resultKey))
                    .ref(ArticleRef.recipe(resultKey))
                    .ref(ArticleRef.item(resultKey));
            for (RegistrySnapshot.RecipeInfo recipe : recipes) {
                builder.keyword(recipe.typeKey());
                for (List<String> row : recipe.grid()) {
                    for (String ingredient : row) {
                        if (!ingredient.isEmpty()) {
                            builder.keyword(ingredient);
                        }
                    }
                }
            }
            sink.accept(builder.build());
        }
    }
}
