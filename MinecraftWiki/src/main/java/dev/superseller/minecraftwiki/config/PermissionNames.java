package dev.superseller.minecraftwiki.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Typed view of {@code permissions.yml}: every permission node name the plugin uses.
 *
 * <p>Node names are configurable so a network can fit the wiki into an existing scheme.
 * Duplicate or blank nodes are reported and fall back to the shipped defaults.</p>
 */
public record PermissionNames(
        String use,
        String search,
        String lang,
        String reload,
        String admin,
        String bypassCooldown,
        String categoryPrefix,
        String articlePrefix,
        String adminReload,
        String adminInfo,
        String adminRebuild,
        String adminDebug,
        String adminArticle,
        String adminProvider
) {

    /** The permission node that guards one category. */
    public String category(String categoryId) {
        return categoryPrefix + categoryId;
    }

    /** The permission node that would guard one article. */
    public String article(String articleId) {
        return articlePrefix + articleId;
    }

    public static PermissionNames load(YamlConfiguration config, ConfigIssues issues) {
        String file = "permissions.yml";
        YamlReader reader = new YamlReader(config, file, issues);
        return new PermissionNames(
                node(reader, issues, file, "nodes.use", "minecraftwiki.use"),
                node(reader, issues, file, "nodes.search", "minecraftwiki.search"),
                node(reader, issues, file, "nodes.lang", "minecraftwiki.lang"),
                node(reader, issues, file, "nodes.reload", "minecraftwiki.reload"),
                node(reader, issues, file, "nodes.admin", "minecraftwiki.admin"),
                node(reader, issues, file, "nodes.bypass-cooldown", "minecraftwiki.bypass.cooldown"),
                prefix(reader, issues, file, "nodes.category-prefix", "minecraftwiki.category."),
                prefix(reader, issues, file, "nodes.article-prefix", "minecraftwiki.article."),
                node(reader, issues, file, "nodes.admin-reload", "minecraftwiki.admin.reload"),
                node(reader, issues, file, "nodes.admin-info", "minecraftwiki.admin.info"),
                node(reader, issues, file, "nodes.admin-rebuild", "minecraftwiki.admin.rebuild"),
                node(reader, issues, file, "nodes.admin-debug", "minecraftwiki.admin.debug"),
                node(reader, issues, file, "nodes.admin-article", "minecraftwiki.admin.article"),
                node(reader, issues, file, "nodes.admin-provider", "minecraftwiki.admin.provider"));
    }

    private static String node(YamlReader reader, ConfigIssues issues, String file, String path, String fallback) {
        String value = reader.string(path, fallback).trim();
        if (value.isEmpty()) {
            issues.add(ConfigIssue.error(file, path, "is empty - using " + fallback));
            return fallback;
        }
        return value;
    }

    private static String prefix(YamlReader reader, ConfigIssues issues, String file, String path, String fallback) {
        String value = node(reader, issues, file, path, fallback);
        if (!value.endsWith(".")) {
            issues.add(ConfigIssue.warning(file, path, "does not end with a dot - appending one"));
            value = value + ".";
        }
        return value;
    }
}
