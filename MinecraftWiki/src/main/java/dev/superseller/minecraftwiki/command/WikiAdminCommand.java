package dev.superseller.minecraftwiki.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.superseller.minecraftwiki.MinecraftWikiPlugin;
import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleId;
import dev.superseller.minecraftwiki.message.Messages;
import dev.superseller.minecraftwiki.provider.ArticleProvider;
import dev.superseller.minecraftwiki.scheduler.PlatformScheduler;
import dev.superseller.minecraftwiki.util.Text;

/**
 * The {@code /wikiadmin} command: diagnostics and maintenance that must not clutter the
 * player facing wiki.
 */
public final class WikiAdminCommand implements CommandExecutor {

    private final MinecraftWikiPlugin plugin;

    public WikiAdminCommand(MinecraftWikiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        String language = plugin.messages().defaultLanguage();
        if (!(sender instanceof Player) || !plugin.settings().language().perPlayer()) {
            language = plugin.messages().defaultLanguage();
        } else {
            language = plugin.messages().resolve(plugin.navigation().session((Player) sender).language());
        }
        if (!sender.hasPermission(plugin.permissions().admin())) {
            messages.send(sender, language, "no-permission", Map.of());
            return true;
        }
        if (args.length == 0) {
            return info(sender, messages, language);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload", "rl" -> reload(sender, messages, language);
            case "info", "i" -> info(sender, messages, language);
            case "rebuild", "rb" -> rebuild(sender, messages, language);
            case "debug", "d" -> debug(sender, args, messages, language);
            case "article", "a" -> article(sender, args, messages, language);
            case "provider", "providers", "p" -> providers(sender, messages, language);
            case "stats", "s" -> info(sender, messages, language);
            default -> {
                messages.send(sender, language, "unknown-subcommand", Map.of());
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender, Messages messages, String language) {
        if (!sender.hasPermission(plugin.permissions().adminReload())) {
            messages.send(sender, language, "no-permission", Map.of());
            return true;
        }
        MinecraftWikiPlugin.ReloadResult result = plugin.reloadAll();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("categories", Integer.toString(result.categories()));
        placeholders.put("articles", Integer.toString(result.articles()));
        placeholders.put("ms", Long.toString(result.millis()));
        placeholders.put("errors", Long.toString(result.errors()));
        messages.send(sender, language, result.errors() > 0 ? "reload-failed" : "reload-done", placeholders);
        return true;
    }

    private boolean info(CommandSender sender, Messages messages, String language) {
        if (!sender.hasPermission(plugin.permissions().adminInfo())) {
            messages.send(sender, language, "no-permission", Map.of());
            return true;
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("version", plugin.getDescription().getVersion());
        placeholders.put("platform", PlatformScheduler.platformName()
                + (PlatformScheduler.isFolia() ? " (regionised)" : ""));
        placeholders.put("minecraft", plugin.snapshot().minecraftVersion());
        placeholders.put("articles", Integer.toString(plugin.repository().size()));
        placeholders.put("categories", Integer.toString(plugin.categories().size()));
        placeholders.put("tokens", Integer.toString(plugin.search().index().tokenCount()));
        placeholders.put("index-ms", Long.toString(plugin.search().index().buildMillis()));
        placeholders.put("providers", Integer.toString(plugin.providers().size()));
        placeholders.put("cache", Integer.toString(plugin.content().cacheSize()));
        placeholders.put("sessions", Integer.toString(plugin.navigation().size()));
        placeholders.put("debug", plugin.debug() ? "on" : "off");
        messages.send(sender, language, "admin-header", Map.of());
        for (var line : messages.getList(language, "admin-lines", placeholders)) {
            sender.sendMessage(line);
        }
        return true;
    }

    private boolean rebuild(CommandSender sender, Messages messages, String language) {
        if (!sender.hasPermission(plugin.permissions().adminRebuild())) {
            messages.send(sender, language, "no-permission", Map.of());
            return true;
        }
        long millis = plugin.rebuildContent();
        messages.send(sender, language, "admin-rebuild", Map.of(
                "ms", Long.toString(millis),
                "articles", Integer.toString(plugin.repository().size())));
        return true;
    }

    private boolean debug(CommandSender sender, String[] args, Messages messages, String language) {
        if (!sender.hasPermission(plugin.permissions().adminDebug())) {
            messages.send(sender, language, "no-permission", Map.of());
            return true;
        }
        boolean enable;
        if (args.length >= 2) {
            enable = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
        } else {
            enable = !plugin.debug();
        }
        plugin.debug(enable);
        messages.send(sender, language, enable ? "admin-debug-on" : "admin-debug-off", Map.of());
        return true;
    }

    private boolean providers(CommandSender sender, Messages messages, String language) {
        if (!sender.hasPermission(plugin.permissions().adminProvider())) {
            messages.send(sender, language, "no-permission", Map.of());
            return true;
        }
        messages.send(sender, language, "admin-provider-header", Map.of());
        for (var provider : plugin.providers()) {
            int count = 0;
            if (provider instanceof ArticleProvider articleProvider) {
                for (String category : articleProvider.categories()) {
                    count += plugin.repository().sizeOf(category);
                }
            }
            boolean enabled = plugin.settings().content().providerEnabled(provider.id());
            messages.send(sender, language, "admin-provider-line", Map.of(
                    "id", provider.id(),
                    "articles", Integer.toString(count),
                    "state", enabled ? "enabled" : "disabled"));
        }
        return true;
    }

    private boolean article(CommandSender sender, String[] args, Messages messages, String language) {
        if (!sender.hasPermission(plugin.permissions().adminArticle())) {
            messages.send(sender, language, "no-permission", Map.of());
            return true;
        }
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
        if (sender instanceof Player player) {
            plugin.openArticle(player, found);
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", found.id().value());
        placeholders.put("title", found.title());
        placeholders.put("category", found.categoryId());
        placeholders.put("kind", found.kind().label());
        placeholders.put("icon", found.icon().material().name());
        placeholders.put("order", Integer.toString(found.order()));
        placeholders.put("visible", Boolean.toString(found.visible()));
        placeholders.put("permission", found.permission() == null ? "-" : found.permission());
        placeholders.put("keywords", found.keywords().isEmpty() ? "-" : Text.join(found.keywords(), ", "));
        List<String> related = found.related().stream().map(ArticleId::value).toList();
        placeholders.put("related", related.isEmpty() ? "-" : Text.join(related, ", "));
        placeholders.put("content", found.hasInlineContent()
                ? "inline (" + found.pages().size() + " page(s))"
                : "resolved from " + found.kind().name().toLowerCase(Locale.ROOT));
        messages.send(sender, language, "admin-article-header", placeholders);
        for (var line : messages.getList(language, "admin-article-lines", placeholders)) {
            sender.sendMessage(line);
        }
        return true;
    }
}
