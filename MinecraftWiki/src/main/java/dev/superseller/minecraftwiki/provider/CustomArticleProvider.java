package dev.superseller.minecraftwiki.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleBuilder;
import dev.superseller.minecraftwiki.article.ArticleId;
import dev.superseller.minecraftwiki.article.ArticleKind;
import dev.superseller.minecraftwiki.article.ArticlePage;
import dev.superseller.minecraftwiki.article.ArticleRef;
import dev.superseller.minecraftwiki.article.ArticleSection;
import dev.superseller.minecraftwiki.config.ConfigIssue;
import dev.superseller.minecraftwiki.config.ConfigIssues;
import dev.superseller.minecraftwiki.config.CategoryConfig;
import dev.superseller.minecraftwiki.config.YamlReader;
import dev.superseller.minecraftwiki.util.Text;

/**
 * Articles written by the server owner in {@code articles.yml}.
 *
 * <p>This is the extension point for content the API cannot supply: guides, mechanics
 * write-ups, server specific pages. The file ships empty on purpose - the wiki never
 * invents articles, it only publishes what the server actually contains or what an admin
 * has written.</p>
 *
 * <p>Parsing is strict. An unknown category, an unusable id, a bad icon or a malformed
 * section is reported with its full path and skipped, never guessed.</p>
 */
public final class CustomArticleProvider implements ArticleProvider {

    private static final int MAX_SUBSECTION_DEPTH = 4;

    private final List<Article> articles;
    private final Set<String> categories;

    private CustomArticleProvider(List<Article> articles, Set<String> categories) {
        this.articles = List.copyOf(articles);
        this.categories = Set.copyOf(categories);
    }

    @Override
    public String id() {
        return ProviderIds.CUSTOM_ARTICLES;
    }

    @Override
    public Set<String> categories() {
        return categories;
    }

    @Override
    public void contribute(ArticleSink sink) {
        for (Article article : articles) {
            sink.accept(article);
        }
    }

    public List<Article> articles() {
        return articles;
    }

    /** Parses {@code articles.yml}. */
    public static CustomArticleProvider load(YamlConfiguration config, CategoryConfig categoryConfig,
                                             String version, ConfigIssues issues) {
        String file = "articles.yml";
        List<Article> out = new ArrayList<>();
        Set<String> usedCategories = new java.util.LinkedHashSet<>();
        ConfigurationSection root = config.getConfigurationSection("articles");
        if (root == null) {
            // An empty articles.yml is the shipped default and is not a problem.
            if (config.contains("articles")) {
                issues.add(ConfigIssue.warning(file, "articles", "is not a list of articles - ignored"));
            }
            return new CustomArticleProvider(out, usedCategories);
        }
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                issues.add(ConfigIssue.warning(file, "articles." + rawId, "is not a section - ignored"));
                continue;
            }
            String path = "articles." + rawId;
            YamlReader reader = new YamlReader(sectionAsConfig(section), file, issues);

            ArticleId id = ArticleId.of(rawId);
            if (id == null) {
                issues.add(ConfigIssue.error(file, path, "is not a usable article id - ignored"));
                continue;
            }
            String categoryId = Text.fold(reader.string("category", "").trim());
            if (categoryId.isEmpty()) {
                issues.add(ConfigIssue.error(file, path + ".category",
                        "is required - the article was skipped"));
                continue;
            }
            if (!categoryConfig.exists(categoryId)) {
                issues.add(ConfigIssue.error(file, path + ".category",
                        "'" + categoryId + "' is not defined in categories.yml - the article was skipped"));
                continue;
            }
            ArticleKind kind = reader.enumValue("kind", ArticleKind.class, ArticleKind.CUSTOM);
            Material icon = reader.material("icon", Material.BOOK);

            ArticleBuilder builder = ArticleBuilder.create(id, categoryId, kind)
                    .title(reader.string("title", Text.prettify(id.key())))
                    .icon(icon)
                    .summary(reader.string("summary", ""))
                    .keywords(reader.stringList("keywords"))
                    .tags(reader.stringList("tags"))
                    .permission(reader.string("permission", null))
                    .visible(reader.bool("visible", true))
                    .order(reader.integer("order", 100, 0, Integer.MAX_VALUE))
                    .version(reader.string("version", version));

            for (String related : reader.stringList("related")) {
                ArticleId relatedId = ArticleId.of(related);
                if (relatedId == null) {
                    issues.add(ConfigIssue.warning(file, path + ".related",
                            "'" + related + "' is not a usable article id - ignored"));
                    continue;
                }
                builder.related(relatedId);
            }
            addRefs(builder, reader, file, path + ".refs");

            ConfigurationSection pagesSection = section.getConfigurationSection("pages");
            if (pagesSection != null) {
                for (String pageKey : pagesSection.getKeys(false)) {
                    ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageKey);
                    if (pageSection == null) {
                        issues.add(ConfigIssue.warning(file, path + ".pages." + pageKey,
                                "is not a section - ignored"));
                        continue;
                    }
                    YamlReader pageReader = new YamlReader(sectionAsConfig(pageSection), file, issues);
                    List<ArticleSection> sections = readSections(
                            pageSection.getConfigurationSection("sections"),
                            file, path + ".pages." + pageKey + ".sections", 0, issues);
                    builder.page(ArticlePage.of(pageReader.string("title", Text.prettify(pageKey)), sections));
                }
            }

            usedCategories.add(categoryId);
            out.add(builder.build());
        }
        return new CustomArticleProvider(out, usedCategories);
    }

    private static List<ArticleSection> readSections(ConfigurationSection section, String file, String path,
                                                     int depth, ConfigIssues issues) {
        if (section == null) {
            return List.of();
        }
        if (depth >= MAX_SUBSECTION_DEPTH) {
            issues.add(ConfigIssue.warning(file, path,
                    "is nested deeper than " + MAX_SUBSECTION_DEPTH + " levels - the rest was ignored"));
            return List.of();
        }
        List<ArticleSection> out = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                issues.add(ConfigIssue.warning(file, path + "." + key, "is not a section - ignored"));
                continue;
            }
            YamlReader reader = new YamlReader(sectionAsConfig(entry), file, issues);
            List<ArticleRef> refs = new ArrayList<>();
            ArticleBuilder refBuilder = new ArticleBuilder();
            addRefs(refBuilder, reader, file, path + "." + key + ".refs");
            Article temporary = refBuilder.id(ArticleId.of("refs:scratch")).build();
            refs.addAll(temporary.refs());
            out.add(new ArticleSection(
                    reader.string("heading", Text.prettify(key)),
                    reader.stringList("lines"),
                    refs,
                    readSections(entry.getConfigurationSection("subsections"),
                            file, path + "." + key + ".subsections", depth + 1, issues)));
        }
        return out;
    }

    private static void addRefs(ArticleBuilder builder, YamlReader reader, String file, String path) {
        if (reader.section("refs") == null && !reader.contains("refs")) {
            return;
        }
        for (String value : reader.stringList("refs.items")) {
            builder.ref(ArticleRef.item(value));
        }
        for (String value : reader.stringList("refs.blocks")) {
            builder.ref(ArticleRef.block(value));
        }
        for (String value : reader.stringList("refs.entities")) {
            builder.ref(ArticleRef.entity(value));
        }
        for (String value : reader.stringList("refs.commands")) {
            builder.ref(ArticleRef.command(value));
        }
        for (String value : reader.stringList("refs.recipes")) {
            builder.ref(ArticleRef.recipe(value));
        }
        for (String value : reader.stringList("refs.tags")) {
            builder.ref(ArticleRef.tag(value));
        }
        for (String value : reader.stringList("refs.sounds")) {
            builder.ref(ArticleRef.sound(value));
        }
    }

    private static YamlConfiguration sectionAsConfig(ConfigurationSection section) {
        YamlConfiguration copy = new YamlConfiguration();
        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) {
                copy.set(key.toLowerCase(Locale.ROOT), section.get(key));
            }
        }
        return copy;
    }
}
