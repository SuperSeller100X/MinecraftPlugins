package dev.superseller.minecraftwiki.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.superseller.minecraftwiki.MinecraftWikiPlugin;
import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleId;
import dev.superseller.minecraftwiki.config.WikiCategory;
import dev.superseller.minecraftwiki.message.Messages;
import dev.superseller.minecraftwiki.search.SearchService;
import dev.superseller.minecraftwiki.session.WikiSession;
import dev.superseller.minecraftwiki.util.Text;

/**
 * The player facing {@code /wiki} command.
 *
 * <p>With no arguments it opens the GUI. Every subcommand is permission checked independently,
 * and the console gets a text answer for the subcommands that make sense without a screen
 * instead of a "players only" dead end.</p>
 */
public final class WikiCommand implements CommandExecutor {

    private final MinecraftWikiPlugin plugin;

    public WikiCommand(MinecraftWikiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        String language = languageOf(sender);
        if (args.length == 0) {
            if (plugin.settings().behaviour().openOnEmptyArguments()) {
                openHome(sender, messages, language);
                return true;
            }
            messages.sendList(sender, language, "help-lines", Map.of());
            return true;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        return switch (sub) {
            case "search", "find", "s" -> search(sender, args, messages, language);
            case "category", "categories", "cat", "c" -> category(sender, args, messages, language);
            case "article", "page", "a" -> article(sender, args, messages, language);
            case "lang", "language", "l" -> language(sender, args, messages, language);
            case "reload", "rl" -> reload(sender, messages, language);
            case "help", "?" , "h" -> help(sender, messages, language);
            default -> {
                if (plugin.settings().behaviour().unknownCommandFeedback()) {
                    messages.send(sender, language, "unknown-subcommand", Map.of());
                }
                yield true;
            }
        };
    }

    private void openHome(CommandSender sender, Messages messages, String language) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, language, "players-only", Map.of());
            return;
        }
        if (!plugin.isReady()) {
            messages.send(player, language, "gui-disabled", Map.of());
            return;
        }
        if (plugin.repository().size() == 0) {
            messages.send(player, language, "gui-empty", Map.of());
            return;
        }
        plugin.openHome(player);
    }

    private boolean search(CommandSender sender, String[] args, Messages messages, String language) {
        if (!sender.hasPermission(plugin.permissions().search())) {
            messages.send(sender, language, "no-permission", Map.of());
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, language, "search-empty", Map.of());
            return true;
        }
        String categoryFilter = null;
        int queryEnd = args.length;
        if (plugin.settings().search().categoryFilter() && args.length >= 3) {
            WikiCategory category = plugin.categories().match(args[args.length - 1]);
            if (category != null) {
                categoryFilter = category.id();
                queryEnd = args.length - 1;
            } else {
                // A trailing word that is not a category stays part of the query, but say so
                // instead of letting a typo silently change what is searched.
                messages.send(sender, language, "search-category-unknown",
                        Map.of("input", Text.sanitize(args[args.length - 1])));
            }
        }
        String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, queryEnd));
        SearchService.Outcome outcome = plugin.search().search(
                sender instanceof Player player ? player.getUniqueId() : null,
                query, categoryFilter,
                sender.hasPermission(plugin.permissions().bypassCooldown()),
                System.currentTimeMillis());
        switch (outcome.status()) {
            case EMPTY -> messages.send(sender, language, "search-empty", Map.of());
            case TOO_SHORT -> messages.send(sender, language, "search-too-short",
                    Map.of("min", Integer.toString(plugin.settings().search().minQueryLength())));
            case TOO_LONG -> messages.send(sender, language, "search-too-long",
                    Map.of("max", Integer.toString(plugin.settings().search().maxQueryLength())));
            case COOLDOWN -> messages.send(sender, language, "search-cooldown",
                    Map.of("seconds", Long.toString((outcome.retryAfterMillis() + 999L) / 1000L)));
            case NO_RESULTS -> messages.send(sender, language, "search-no-results",
                    Map.of("query", Text.sanitize(outcome.query().raw())));
            case OK -> {
                if (sender instanceof Player player) {
                    plugin.openSearchResults(player, outcome.query(), outcome.results());
                } else {
                    messages.send(sender, language, "search-results-console", Map.of(
                            "count", Integer.toString(outcome.results().size()),
                            "query", Text.sanitize(outcome.query().raw())));
                    for (var result : outcome.results()) {
                        messages.send(sender, language, "search-result-line", Map.of(
                                "title", result.article().title(),
                                "category", result.article().categoryId(),
                                "id", result.article().id().value()));
                    }
                }
            }
        }
        return true;
    }

    private boolean category(CommandSender sender, String[] args, Messages messages, String language) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            listCategories(sender, messages, language);
            return true;
        }
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        WikiCategory category = plugin.categories().match(name);
        if (category == null) {
            messages.send(sender, language, "category-unknown", Map.of("input", Text.sanitize(name)));
            return true;
        }
        if (category.hidden()) {
            messages.send(sender, language, "category-disabled",
                    Map.of("name", Text.plain(Text.mini(category.title()))));
            return true;
        }
        if (!sender.hasPermission(plugin.permissions().category(category.id()))) {
            messages.send(sender, language, "category-no-permission",
                    Map.of("name", Text.plain(Text.mini(category.title()))));
            return true;
        }
        if (plugin.repository().sizeOf(category.id()) == 0) {
            messages.send(sender, language, "category-empty",
                    Map.of("name", Text.plain(Text.mini(category.title()))));
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, language, "players-only", Map.of());
            return true;
        }
        plugin.openCategory(player, category);
        return true;
    }

    private void listCategories(CommandSender sender, Messages messages, String language) {
        messages.send(sender, language, "category-list-header", Map.of());
        for (WikiCategory category : plugin.categories().enabled()) {
            if (!sender.hasPermission(plugin.permissions().category(category.id()))) {
                continue;
            }
            messages.send(sender, language, "category-list-line", Map.of(
                    "title", Text.plain(Text.mini(category.title())),
                    "count", Integer.toString(plugin.repository().sizeOf(category.id())),
                    "id", category.id()));
        }
    }

    private boolean article(CommandSender sender, String[] args, Messages messages, String language) {
        if (args.length < 2) {
            messages.send(sender, language, "article-unknown", Map.of("input", ""));
            return true;
        }
        String rawId = String.join(":", java.util.Arrays.copyOfRange(args, 1, args.length));
        Article found = plugin.repository().get(ArticleId.of(rawId));
        if (found == null) {
            found = plugin.findArticleByTitle(rawId);
        }
        if (found == null) {
            messages.send(sender, language, "article-unknown", Map.of("input", Text.sanitize(rawId)));
            return true;
        }
        WikiCategory category = plugin.categories().get(found.categoryId());
        if (category != null && !sender.hasPermission(plugin.permissions().category(category.id()))) {
            messages.send(sender, language, "article-no-permission", Map.of("title", found.title()));
            return true;
        }
        if (found.hasOwnPermission() && !sender.hasPermission(found.permission())) {
            messages.send(sender, language, "article-no-permission", Map.of("title", found.title()));
            return true;
        }
        if (!found.visible() && !sender.hasPermission(plugin.permissions().adminArticle())) {
            messages.send(sender, language, "article-hidden", Map.of());
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, language, "players-only", Map.of());
            return true;
        }
        plugin.openArticle(player, found);
        return true;
    }

    private boolean language(CommandSender sender, String[] args, Messages messages, String language) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, language, "players-only", Map.of());
            return true;
        }
        if (!plugin.settings().language().perPlayer()) {
            messages.send(player, language, "lang-disabled", Map.of());
            return true;
        }
        WikiSession session = plugin.navigation().session(player);
        if (args.length < 2) {
            messages.send(player, language, "lang-current", Map.of(
                    "language", messages.resolve(session.language())));
            messages.send(player, language, "lang-available-header", Map.of());
            for (String code : messages.languages()) {
                messages.send(player, language, "lang-available-line", Map.of(
                        "code", code,
                        "current", code.equals(messages.resolve(session.language())) ? "\u25C0" : ""));
            }
            return true;
        }
        if (!sender.hasPermission(plugin.permissions().lang())) {
            messages.send(player, language, "no-permission", Map.of());
            return true;
        }
        String requested = args[1].toLowerCase(java.util.Locale.ROOT);
        if (!messages.has(requested)) {
            messages.send(player, language, "lang-unknown", Map.of("language", Text.sanitize(requested)));
            return true;
        }
        session.language(requested);
        messages.send(player, requested, "lang-set", Map.of("language", requested));
        return true;
    }

    private boolean reload(CommandSender sender, Messages messages, String language) {
        if (!sender.hasPermission(plugin.permissions().reload())) {
            messages.send(sender, language, "no-permission", Map.of());
            return true;
        }
        messages.send(sender, language, "reload-start", Map.of());
        MinecraftWikiPlugin.ReloadResult result = plugin.reloadAll();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("categories", Integer.toString(result.categories()));
        placeholders.put("articles", Integer.toString(result.articles()));
        placeholders.put("ms", Long.toString(result.millis()));
        placeholders.put("errors", Long.toString(result.errors()));
        if (result.errors() > 0) {
            messages.send(sender, language, "reload-failed", placeholders);
        } else {
            messages.send(sender, language, "reload-done", placeholders);
        }
        return true;
    }

    private boolean help(CommandSender sender, Messages messages, String language) {
        messages.send(sender, language, "help-header", Map.of());
        List<String> keys = new ArrayList<>(List.of("help-lines"));
        for (String key : keys) {
            messages.sendList(sender, language, key, Map.of());
        }
        if (sender.hasPermission(plugin.permissions().reload())) {
            messages.sendList(sender, language, "help-admin-line", Map.of());
        }
        return true;
    }

    private String languageOf(CommandSender sender) {
        if (sender instanceof Player player) {
            return plugin.messages().resolve(plugin.navigation().session(player).language());
        }
        return plugin.messages().defaultLanguage();
    }
}
