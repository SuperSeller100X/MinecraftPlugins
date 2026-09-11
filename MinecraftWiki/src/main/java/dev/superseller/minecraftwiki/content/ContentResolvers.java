package dev.superseller.minecraftwiki.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleKind;
import dev.superseller.minecraftwiki.article.ArticlePage;
import dev.superseller.minecraftwiki.article.ArticleRef;
import dev.superseller.minecraftwiki.article.ArticleSection;
import dev.superseller.minecraftwiki.provider.RegistryEntry;
import dev.superseller.minecraftwiki.provider.RegistrySnapshot;
import dev.superseller.minecraftwiki.util.Text;

/**
 * The built-in content resolvers.
 *
 * <p>Every body is derived from the {@link RegistrySnapshot} captured from the running
 * server, so an article always describes the version it is being read on. Where the API
 * exposes no fact, the article simply has no line for it rather than a made-up one.</p>
 */
public final class ContentResolvers {

    /** Cap on how many tag values are listed inline; the rest are summarised. */
    private static final int MAX_INLINE_TAG_VALUES = 40;

    private ContentResolvers() {
    }

    /** All resolvers for one snapshot. */
    public static List<ContentResolver> all(RegistrySnapshot snapshot, Labels labels) {
        return List.of(
                new MaterialResolver(snapshot, labels),
                new EntityResolver(snapshot, labels),
                new RegistryResolver(snapshot, labels, ArticleKind.ENCHANTMENT, snapshot.enchantments()),
                new RegistryResolver(snapshot, labels, ArticleKind.EFFECT, snapshot.effects()),
                new RegistryResolver(snapshot, labels, ArticleKind.POTION, snapshot.potions()),
                new RegistryResolver(snapshot, labels, ArticleKind.BIOME, snapshot.biomes()),
                new RegistryResolver(snapshot, labels, ArticleKind.STRUCTURE, snapshot.structures()),
                new RegistryResolver(snapshot, labels, ArticleKind.SOUND, snapshot.sounds()),
                new RegistryResolver(snapshot, labels, ArticleKind.PARTICLE, snapshot.particles()),
                new RegistryResolver(snapshot, labels, ArticleKind.ATTRIBUTE, snapshot.attributes()),
                new RegistryResolver(snapshot, labels, ArticleKind.DAMAGE_TYPE, snapshot.damageTypes()),
                new RegistryResolver(snapshot, labels, ArticleKind.GAME_EVENT, snapshot.gameEvents()),
                new RegistryResolver(snapshot, labels, ArticleKind.VILLAGER_PROFESSION,
                        snapshot.villagerProfessions()),
                new RegistryResolver(snapshot, labels, ArticleKind.ADVANCEMENT, snapshot.advancements()),
                new RecipeResolver(snapshot, labels),
                new CommandResolver(snapshot, labels),
                new TagResolver(snapshot, labels),
                new GameRuleResolver(snapshot, labels),
                new DimensionResolver(snapshot, labels));
    }

    /** Shared helpers for turning a fact sheet into sections. */
    static List<String> factLines(Map<String, String> properties, Labels labels) {
        List<String> lines = new ArrayList<>(properties.size());
        for (Map.Entry<String, String> property : properties.entrySet()) {
            lines.add(labels.line(property.getKey(), property.getValue()));
        }
        return lines;
    }

    static ArticlePage singlePage(String title, List<ArticleSection> sections) {
        return ArticlePage.of(title, sections);
    }

    /** Blocks and items: the facts the material exposes, its tags and its recipes. */
    public static final class MaterialResolver implements ContentResolver {

        private final RegistrySnapshot snapshot;
        private final Labels labels;

        public MaterialResolver(RegistrySnapshot snapshot, Labels labels) {
            this.snapshot = snapshot;
            this.labels = labels;
        }

        @Override
        public boolean supports(Article article) {
            return article.kind() == ArticleKind.BLOCK || article.kind() == ArticleKind.ITEM;
        }

        @Override
        public List<ArticlePage> resolve(Article article) {
            RegistrySnapshot.MaterialInfo info = snapshot.material(article.id().key());
            if (info == null) {
                return List.of();
            }
            List<ArticleSection> sections = new ArrayList<>();
            sections.add(ArticleSection.of(labels.label("properties"), factLines(info.properties(), labels)));

            List<ArticleRef> tags = new ArrayList<>();
            for (String tag : info.tags()) {
                tags.add(ArticleRef.tag(tag));
            }
            if (!tags.isEmpty()) {
                sections.add(ArticleSection.of(labels.label("tags"),
                        List.of(labels.line("count", Integer.toString(tags.size()))), tags));
            }

            List<RegistrySnapshot.RecipeInfo> recipes = snapshot.recipesFor(info.key());
            if (!recipes.isEmpty()) {
                List<String> lines = new ArrayList<>();
                lines.add(labels.line("count", Integer.toString(recipes.size())));
                List<ArticleRef> refs = new ArrayList<>();
                refs.add(ArticleRef.recipe(info.key()));
                sections.add(ArticleSection.of(labels.label("recipes"), lines, refs));
            }
            return List.of(singlePage(article.title(), sections));
        }
    }

    /** Entities: the type facts plus the default attributes the server reports. */
    public static final class EntityResolver implements ContentResolver {

        private final RegistrySnapshot snapshot;
        private final Labels labels;

        public EntityResolver(RegistrySnapshot snapshot, Labels labels) {
            this.snapshot = snapshot;
            this.labels = labels;
        }

        @Override
        public boolean supports(Article article) {
            return article.kind() == ArticleKind.ENTITY;
        }

        @Override
        public List<ArticlePage> resolve(Article article) {
            RegistrySnapshot.EntityInfo info = snapshot.entity(article.id().key());
            if (info == null) {
                return List.of();
            }
            List<ArticleSection> sections = new ArrayList<>();
            sections.add(ArticleSection.of(labels.label("properties"), factLines(info.properties(), labels)));
            if (!info.attributes().isEmpty()) {
                List<String> lines = new ArrayList<>(info.attributes().size());
                for (RegistrySnapshot.AttributeValue attribute : info.attributes()) {
                    lines.add(labels.line(attribute.key(), RegistryEntry.Facts.trim(attribute.value())));
                }
                sections.add(ArticleSection.of(labels.label("default_attributes"), lines));
            }
            return List.of(singlePage(article.title(), sections));
        }
    }

    /** Any registry whose entries are described by a flat fact sheet. */
    public static final class RegistryResolver implements ContentResolver {

        private final RegistrySnapshot snapshot;
        private final Labels labels;
        private final ArticleKind kind;
        private final Map<String, RegistryEntry> entries;

        public RegistryResolver(RegistrySnapshot snapshot, Labels labels, ArticleKind kind,
                                Map<String, RegistryEntry> entries) {
            this.snapshot = snapshot;
            this.labels = labels;
            this.kind = kind;
            this.entries = entries;
        }

        @Override
        public boolean supports(Article article) {
            return article.kind() == kind;
        }

        @Override
        public List<ArticlePage> resolve(Article article) {
            RegistryEntry entry = entries.get(Text.stripNamespace(Text.fold(article.id().key())));
            if (entry == null) {
                return List.of();
            }
            List<ArticleSection> sections = new ArrayList<>();
            sections.add(new ArticleSection(labels.label("properties"),
                    List.of(labels.line("registry_key", entry.key())), List.of(), List.of()));
            if (!entry.properties().isEmpty()) {
                sections.add(ArticleSection.of(labels.label("details"), factLines(entry.properties(), labels)));
            }
            return List.of(singlePage(article.title(), sections));
        }
    }

    /** Every recipe the server has for one result. */
    public static final class RecipeResolver implements ContentResolver {

        private final RegistrySnapshot snapshot;
        private final Labels labels;

        public RecipeResolver(RegistrySnapshot snapshot, Labels labels) {
            this.snapshot = snapshot;
            this.labels = labels;
        }

        @Override
        public boolean supports(Article article) {
            return article.kind() == ArticleKind.RECIPE;
        }

        @Override
        public List<ArticlePage> resolve(Article article) {
            List<RegistrySnapshot.RecipeInfo> recipes = snapshot.recipesFor(article.id().key());
            if (recipes.isEmpty()) {
                return List.of();
            }
            List<ArticleSection> sections = new ArrayList<>(recipes.size());
            int index = 1;
            for (RegistrySnapshot.RecipeInfo recipe : recipes) {
                List<String> lines = new ArrayList<>();
                lines.add(labels.line("recipe_type", Text.prettify(recipe.typeKey())));
                lines.add(labels.line("result", Text.prettify(recipe.resultKey())));
                if (!recipe.properties().isEmpty()) {
                    lines.addAll(factLines(recipe.properties(), labels));
                }
                List<String> ingredients = new ArrayList<>();
                for (List<String> row : recipe.grid()) {
                    for (String cell : row) {
                        if (!cell.isEmpty()) {
                            ingredients.add(Text.prettify(cell));
                        }
                    }
                }
                if (!ingredients.isEmpty()) {
                    lines.add(labels.line("ingredients", Text.join(ingredients, ", ")));
                }
                List<ArticleRef> refs = new ArrayList<>();
                for (List<String> row : recipe.grid()) {
                    for (String cell : row) {
                        if (!cell.isEmpty()) {
                            refs.add(ArticleRef.item(cell));
                        }
                    }
                }
                sections.add(new ArticleSection(
                        labels.label("recipe") + " " + index++ + " \u2014 " + Text.prettify(recipe.typeKey()),
                        lines, refs, List.of()));
            }
            return List.of(singlePage(article.title(), sections));
        }
    }

    /** Commands, straight from the live command map. */
    public static final class CommandResolver implements ContentResolver {

        private final RegistrySnapshot snapshot;
        private final Labels labels;

        public CommandResolver(RegistrySnapshot snapshot, Labels labels) {
            this.snapshot = snapshot;
            this.labels = labels;
        }

        @Override
        public boolean supports(Article article) {
            return article.kind() == ArticleKind.COMMAND;
        }

        @Override
        public List<ArticlePage> resolve(Article article) {
            String name = Text.stripNamespace(article.id().key());
            RegistrySnapshot.CommandInfo info = snapshot.commands().stream()
                    .filter(command -> command.name().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (info == null) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            if (!info.description().isEmpty()) {
                lines.add(labels.line("description", info.description()));
            }
            if (!info.usage().isEmpty() && !"/<command>".equals(info.usage())) {
                lines.add(labels.line("usage", info.usage()));
            }
            if (!info.aliases().isEmpty()) {
                lines.add(labels.line("aliases", Text.join(info.aliases(), ", ")));
            }
            if (!info.permission().isEmpty()) {
                lines.add(labels.line("permission", info.permission()));
            }
            List<ArticleSection> sections = new ArrayList<>();
            sections.add(new ArticleSection(labels.label("properties"), lines,
                    List.of(ArticleRef.command("/" + info.name())), List.of()));
            return List.of(singlePage(article.title(), sections));
        }
    }

    /** Registry tags and the values they contain. */
    public static final class TagResolver implements ContentResolver {

        private final RegistrySnapshot snapshot;
        private final Labels labels;

        public TagResolver(RegistrySnapshot snapshot, Labels labels) {
            this.snapshot = snapshot;
            this.labels = labels;
        }

        @Override
        public boolean supports(Article article) {
            return article.kind() == ArticleKind.TAG;
        }

        @Override
        public List<ArticlePage> resolve(Article article) {
            String key = Text.stripNamespace(Text.fold(article.id().key()));
            RegistrySnapshot.TagInfo info = snapshot.tags().stream()
                    .filter(tag -> tag.key().equalsIgnoreCase(key))
                    .findFirst()
                    .orElse(null);
            if (info == null) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            lines.add(labels.line("registry", info.registry()));
            lines.add(labels.line("count", Integer.toString(info.values().size())));
            List<String> values = info.values().stream().limit(MAX_INLINE_TAG_VALUES)
                    .map(Text::prettify).toList();
            if (!values.isEmpty()) {
                lines.add(labels.line("values", Text.join(values, ", ")));
            }
            if (info.values().size() > MAX_INLINE_TAG_VALUES) {
                lines.add(labels.line("more", Integer.toString(info.values().size() - MAX_INLINE_TAG_VALUES)));
            }
            return List.of(singlePage(article.title(),
                    List.of(ArticleSection.of(labels.label("details"), lines))));
        }
    }

    /** Game rules. */
    public static final class GameRuleResolver implements ContentResolver {

        private final RegistrySnapshot snapshot;
        private final Labels labels;

        public GameRuleResolver(RegistrySnapshot snapshot, Labels labels) {
            this.snapshot = snapshot;
            this.labels = labels;
        }

        @Override
        public boolean supports(Article article) {
            return article.kind() == ArticleKind.GAMERULE;
        }

        @Override
        public List<ArticlePage> resolve(Article article) {
            RegistryEntry entry = snapshot.gamerules().get(Text.stripNamespace(Text.fold(article.id().key())));
            if (entry == null) {
                return List.of();
            }
            return List.of(singlePage(article.title(), List.of(new ArticleSection(
                    labels.label("details"), factLines(entry.properties(), labels),
                    List.of(ArticleRef.command("/gamerule " + entry.key())), List.of()))));
        }
    }

    /** World dimensions. */
    public static final class DimensionResolver implements ContentResolver {

        private final RegistrySnapshot snapshot;
        private final Labels labels;

        public DimensionResolver(RegistrySnapshot snapshot, Labels labels) {
            this.snapshot = snapshot;
            this.labels = labels;
        }

        @Override
        public boolean supports(Article article) {
            return article.kind() == ArticleKind.DIMENSION;
        }

        @Override
        public List<ArticlePage> resolve(Article article) {
            String key = Text.stripNamespace(Text.fold(article.id().key()));
            RegistrySnapshot.DimensionInfo info = snapshot.dimensions().stream()
                    .filter(dimension -> dimension.key().equalsIgnoreCase(key))
                    .findFirst()
                    .orElse(null);
            if (info == null) {
                return List.of();
            }
            return List.of(singlePage(article.title(), List.of(
                    ArticleSection.of(labels.label("details"), factLines(info.properties(), labels)))));
        }
    }
}
