package dev.superseller.minecraftwiki.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.superseller.minecraftwiki.provider.ProviderIds;

/**
 * Typed view of {@code config.yml}.
 */
public record PluginSettings(
        LanguageSettings language,
        SearchSettings search,
        InputSettings input,
        ContentSettings content,
        NavigationSettings navigation,
        BehaviourSettings behaviour,
        LogSettings logging,
        boolean debug
) {

    /** Localisation behaviour. */
    public record LanguageSettings(String defaultLanguage, boolean perPlayer, String messageFile,
                                   boolean fallbackToDefault) {
    }

    /** Relevance weights used by the search index. */
    public record ScoreWeights(int exactTitle, int prefixTitle, int containsTitle, int exactKeyword,
                               int prefixKeyword, int containsKeyword, int category, int summary) {
    }

    /** Search behaviour. */
    public record SearchSettings(int maxResults, long cooldownMillis, int minQueryLength, int maxQueryLength,
                                 boolean categoryFilter, ScoreWeights score) {
    }

    /** How the plugin asks a player for a search string in chat. */
    public record InputSettings(String cancelWord, int timeoutSeconds, boolean rejectCommands) {
    }

    /** Caching, indexing and which content providers run. */
    public record ContentSettings(boolean cacheEnabled, int cacheSize, boolean rebuildIndexAsync,
                                  Set<String> enabledProviders) {
        public boolean providerEnabled(String id) {
            return enabledProviders.contains(id);
        }
    }

    /** Menu history and reload behaviour. */
    public record NavigationSettings(int historySize, boolean reopenAfterReload, boolean keepSessionOpen) {
    }

    /** Command level behaviour. */
    public record BehaviourSettings(boolean openOnEmptyArguments, boolean showVersionOnArticles,
                                    boolean unknownCommandFeedback) {
    }

    /** Console output. */
    public record LogSettings(boolean startupSummary, String prefix) {
    }

    /** Parses and validates {@code config.yml}. */
    public static PluginSettings load(YamlConfiguration config, ConfigIssues issues) {
        String file = "config.yml";
        YamlReader reader = new YamlReader(config, file, issues);

        String defaultLanguage = reader.string("language.default", "en").trim();
        if (defaultLanguage.isEmpty()) {
            issues.add(ConfigIssue.warning(file, "language.default", "is empty - using en"));
            defaultLanguage = "en";
        }
        LanguageSettings language = new LanguageSettings(
                defaultLanguage,
                reader.bool("language.per-player", true),
                reader.string("language.message-file", "messages.yml").trim(),
                reader.bool("language.fallback-to-default", true));

        double cooldownSeconds = reader.decimal("search.cooldown-seconds", 2.0d, 0d, 3600d);
        int minQuery = reader.integer("search.minimum-query-length", 1, 1, 32);
        int maxQuery = reader.integer("search.maximum-query-length", 64, 1, 256);
        if (maxQuery < minQuery) {
            issues.add(ConfigIssue.warning(file, "search.maximum-query-length",
                    maxQuery + " is smaller than the minimum of " + minQuery + " - raised to " + minQuery));
            maxQuery = minQuery;
        }
        SearchSettings search = new SearchSettings(
                reader.integer("search.max-results", 45, 1, 500),
                Math.round(cooldownSeconds * 1000d),
                minQuery,
                maxQuery,
                reader.bool("search.category-filter", true),
                new ScoreWeights(
                        reader.integer("search.score.exact-title", 1000, 0, 100000),
                        reader.integer("search.score.prefix-title", 600, 0, 100000),
                        reader.integer("search.score.contains-title", 300, 0, 100000),
                        reader.integer("search.score.exact-keyword", 250, 0, 100000),
                        reader.integer("search.score.prefix-keyword", 150, 0, 100000),
                        reader.integer("search.score.contains-keyword", 75, 0, 100000),
                        reader.integer("search.score.category", 40, 0, 100000),
                        reader.integer("search.score.summary", 20, 0, 100000)));

        String cancelWord = reader.string("search.input.cancel-word", "cancel").trim();
        if (cancelWord.isEmpty()) {
            issues.add(ConfigIssue.warning(file, "search.input.cancel-word",
                    "is empty - using cancel"));
            cancelWord = "cancel";
        }
        InputSettings input = new InputSettings(
                cancelWord,
                reader.integer("search.input.timeout-seconds", 30, 5, 600),
                reader.bool("search.input.reject-commands", true));

        Set<String> enabled = new LinkedHashSet<>();
        ConfigurationSection providerSection = reader.section("content.providers");
        if (providerSection == null) {
            issues.add(ConfigIssue.warning(file, "content.providers",
                    "is missing - every content provider is enabled"));
            enabled.addAll(ProviderIds.ALL);
        } else {
            for (String key : providerSection.getKeys(false)) {
                if (!ProviderIds.known(key)) {
                    issues.add(ConfigIssue.warning(file, "content.providers." + key,
                            "is not a known provider id and was ignored"));
                    continue;
                }
                if (providerSection.getBoolean(key, false)) {
                    enabled.add(key);
                }
            }
            for (String id : ProviderIds.ALL) {
                if (!providerSection.contains(id)) {
                    issues.add(ConfigIssue.warning(file, "content.providers." + id,
                            "is missing - that provider is disabled"));
                }
            }
        }
        int cacheSize = reader.integer("content.cache.size", 1024, 16, 100000);
        ContentSettings content = new ContentSettings(
                reader.bool("content.cache.enabled", true),
                cacheSize,
                reader.bool("content.index.rebuild-async", true),
                Set.copyOf(enabled));

        NavigationSettings navigation = new NavigationSettings(
                reader.integer("navigation.history-size", 12, 0, 100),
                reader.bool("navigation.reopen-after-reload", true),
                reader.bool("navigation.keep-session-open", false));

        BehaviourSettings behaviour = new BehaviourSettings(
                reader.bool("behaviour.open-on-empty-arguments", true),
                reader.bool("behaviour.show-version-on-articles", true),
                reader.bool("behaviour.unknown-command-feedback", true));

        LogSettings logging = new LogSettings(
                reader.bool("logging.startup-summary", true),
                reader.string("logging.prefix", "[Wiki]"));

        return new PluginSettings(language, search, input, content, navigation, behaviour, logging,
                reader.bool("debug", false));
    }
}
