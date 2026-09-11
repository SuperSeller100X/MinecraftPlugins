package dev.superseller.minecraftwiki.provider;

import java.util.List;
import java.util.Set;

import org.bukkit.Material;

import dev.superseller.minecraftwiki.article.ArticleKind;
import dev.superseller.minecraftwiki.article.ArticleRef;
import dev.superseller.minecraftwiki.util.Text;

/**
 * Providers for the smaller, flat data sets: game rules, commands, tags and dimensions.
 *
 * <p>They are grouped here because each is a handful of lines over one snapshot list, and
 * keeping them together avoids four near-empty classes.</p>
 */
public final class DetailProviders {

    private DetailProviders() {
    }

    /** Articles for the game rules this server exposes. */
    public static final class GameRuleProvider extends MinecraftRegistryProvider {

        private final String categoryId;

        public GameRuleProvider(String categoryId, RegistrySnapshot snapshot, String version) {
            super(snapshot, version);
            this.categoryId = categoryId;
        }

        @Override
        public String id() {
            return ProviderIds.GAMERULES;
        }

        @Override
        public Set<String> categories() {
            return Set.of(categoryId);
        }

        @Override
        public void contribute(ArticleSink sink) {
            for (RegistryEntry entry : snapshot.gamerules().values()) {
                sink.accept(newArticle(categoryId, ArticleKind.GAMERULE, entry.key(),
                        defaultIcon(ArticleKind.GAMERULE))
                        .title(entry.label())
                        .summary(entry.label())
                        .ref(ArticleRef.command("/gamerule " + entry.key()))
                        .build());
            }
        }
    }

    /** Articles for every command registered on the server. */
    public static final class CommandProvider extends MinecraftRegistryProvider {

        private final String categoryId;

        public CommandProvider(String categoryId, RegistrySnapshot snapshot, String version) {
            super(snapshot, version);
            this.categoryId = categoryId;
        }

        @Override
        public String id() {
            return ProviderIds.COMMANDS;
        }

        @Override
        public Set<String> categories() {
            return Set.of(categoryId);
        }

        @Override
        public void contribute(ArticleSink sink) {
            for (RegistrySnapshot.CommandInfo command : snapshot.commands()) {
                var builder = newArticle(categoryId, ArticleKind.COMMAND, command.name(),
                        defaultIcon(ArticleKind.COMMAND))
                        .title("/" + command.name())
                        .summary(command.description().isEmpty() ? "/" + command.name() : command.description())
                        .keyword(command.name())
                        .ref(ArticleRef.command("/" + command.name()));
                for (String alias : command.aliases()) {
                    builder.keyword(alias);
                }
                sink.accept(builder.build());
            }
        }
    }

    /** Articles for registry tags. */
    public static final class TagProvider extends MinecraftRegistryProvider {

        private final String categoryId;

        public TagProvider(String categoryId, RegistrySnapshot snapshot, String version) {
            super(snapshot, version);
            this.categoryId = categoryId;
        }

        @Override
        public String id() {
            return ProviderIds.TAGS;
        }

        @Override
        public Set<String> categories() {
            return Set.of(categoryId);
        }

        @Override
        public void contribute(ArticleSink sink) {
            for (RegistrySnapshot.TagInfo tag : snapshot.tags()) {
                var builder = newArticle(categoryId, ArticleKind.TAG, tag.key(), defaultIcon(ArticleKind.TAG))
                        .summary(tag.registry() + " tag")
                        .keyword(tag.registry())
                        .ref(ArticleRef.tag(tag.key()));
                for (String value : tag.values().stream().limit(MAX_DISPLAYED_TAGS).toList()) {
                    builder.keyword(value);
                }
                sink.accept(builder.build());
            }
        }
    }

    /** Articles for the world dimensions. */
    public static final class DimensionProvider extends MinecraftRegistryProvider {

        private final String categoryId;

        public DimensionProvider(String categoryId, RegistrySnapshot snapshot, String version) {
            super(snapshot, version);
            this.categoryId = categoryId;
        }

        @Override
        public String id() {
            return ProviderIds.DIMENSIONS;
        }

        @Override
        public Set<String> categories() {
            return Set.of(categoryId);
        }

        @Override
        public void contribute(ArticleSink sink) {
            for (RegistrySnapshot.DimensionInfo dimension : snapshot.dimensions()) {
                sink.accept(newArticle(categoryId, ArticleKind.DIMENSION, dimension.key(),
                        defaultIcon(ArticleKind.DIMENSION))
                        .title(dimension.label())
                        .summary(dimension.label())
                        .keyword(Text.prettify(dimension.key()))
                        .build());
            }
        }
    }

    /** Convenience list used by the plugin when registering providers. */
    public static List<WikiProvider> all(RegistrySnapshot snapshot, String version) {
        return List.of(
                new GameRuleProvider("gamerules", snapshot, version),
                new CommandProvider("commands", snapshot, version),
                new TagProvider("tags", snapshot, version),
                new DimensionProvider("dimensions", snapshot, version));
    }
}
