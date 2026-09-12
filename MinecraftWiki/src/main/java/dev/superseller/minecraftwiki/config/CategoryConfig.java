package dev.superseller.minecraftwiki.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.superseller.minecraftwiki.article.ArticleId;
import dev.superseller.minecraftwiki.util.Text;

/**
 * Immutable, ordered set of categories loaded from {@code categories.yml}.
 *
 * <p>Category ids are normalised the same way article ids are, so a category id can be
 * used directly in a command argument or a permission node.</p>
 */
public final class CategoryConfig {

    private final Map<String, WikiCategory> byId;
    private final List<WikiCategory> ordered;
    private final List<WikiCategory> enabled;

    private CategoryConfig(Map<String, WikiCategory> byId, List<WikiCategory> ordered) {
        this.byId = Collections.unmodifiableMap(byId);
        this.ordered = List.copyOf(ordered);
        List<WikiCategory> visible = new ArrayList<>();
        for (WikiCategory category : ordered) {
            if (category.enabled()) {
                visible.add(category);
            }
        }
        this.enabled = List.copyOf(visible);
    }

    public static CategoryConfig load(YamlConfiguration config, ConfigIssues issues) {
        String file = "categories.yml";
        Map<String, WikiCategory> byId = new LinkedHashMap<>();
        List<WikiCategory> ordered = new ArrayList<>();
        ConfigurationSection root = config.getConfigurationSection("categories");
        if (root == null) {
            issues.add(ConfigIssue.error(file, "categories",
                    "is missing - the wiki home will have no categories"));
            return new CategoryConfig(byId, ordered);
        }
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                issues.add(ConfigIssue.warning(file, "categories." + rawId,
                        "is not a section - ignored"));
                continue;
            }
            ArticleId normalised = ArticleId.of(rawId);
            if (normalised == null) {
                issues.add(ConfigIssue.error(file, "categories." + rawId,
                        "is not a usable category id - ignored"));
                continue;
            }
            String id = normalised.value();
            if (byId.containsKey(id)) {
                issues.add(ConfigIssue.error(file, "categories." + rawId,
                        "normalises to the duplicate id '" + id + "' - ignored"));
                continue;
            }
            YamlReader reader = new YamlReader(sectionAsConfig(section), file + ":" + rawId, issues);
            Material icon = reader.material("icon", Material.PAPER);
            String permission = reader.string("permission", null);
            WikiCategory category = new WikiCategory(
                    id,
                    reader.string("title", Text.prettify(id)),
                    reader.string("description", ""),
                    icon,
                    reader.bool("enabled", true),
                    reader.integer("order", 1000, 0, Integer.MAX_VALUE),
                    permission,
                    reader.bool("glowing", false));
            byId.put(id, category);
            ordered.add(category);
        }
        ordered.sort((a, b) -> {
            int byOrder = Integer.compare(a.order(), b.order());
            return byOrder != 0 ? byOrder : String.CASE_INSENSITIVE_ORDER.compare(a.title(), b.title());
        });
        return new CategoryConfig(byId, ordered);
    }

    public WikiCategory get(String id) {
        if (id == null) {
            return null;
        }
        ArticleId normalised = ArticleId.of(id);
        return normalised == null ? null : byId.get(normalised.value());
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    /** Every configured category, ordered, enabled or not. */
    public List<WikiCategory> all() {
        return ordered;
    }

    /** Only enabled categories, ordered. */
    public List<WikiCategory> enabled() {
        return enabled;
    }

    public Collection<String> ids() {
        return byId.keySet();
    }

    public int size() {
        return ordered.size();
    }

    /** Case-insensitive lookup used for command arguments. */
    public WikiCategory match(String argument) {
        if (argument == null || argument.isBlank()) {
            return null;
        }
        String needle = Text.fold(argument.trim());
        WikiCategory byId = get(needle);
        if (byId != null) {
            return byId;
        }
        for (WikiCategory category : ordered) {
            if (Text.fold(category.title()).equals(needle)) {
                return category;
            }
        }
        for (WikiCategory category : ordered) {
            if (Text.fold(category.title()).replace(" ", "").equals(needle.replace(" ", ""))
                    || Text.fold(category.id()).replace("_", "").equals(needle.replace("_", "").replace("-", ""))) {
                return category;
            }
        }
        return null;
    }

    /** Lower-cased ids of every category, used to validate articles.yml. */
    public List<String> idList() {
        return List.copyOf(byId.keySet());
    }

    @Override
    public String toString() {
        return "CategoryConfig[" + ordered.size() + " categories, " + enabled.size() + " enabled]";
    }

    private static YamlConfiguration sectionAsConfig(ConfigurationSection section) {
        YamlConfiguration copy = new YamlConfiguration();
        for (String key : section.getKeys(false)) {
            copy.set(key.toLowerCase(Locale.ROOT), section.get(key));
        }
        return copy;
    }
}
