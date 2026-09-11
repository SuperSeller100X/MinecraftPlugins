package dev.superseller.minecraftwiki.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import dev.superseller.minecraftwiki.MinecraftWikiPlugin;
import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.config.WikiCategory;
import dev.superseller.minecraftwiki.util.Text;

/**
 * Tab completion for both wiki commands.
 *
 * <p>Completions are permission filtered and capped, so a catalogue of thousands of articles
 * cannot flood a client's suggestion list.</p>
 */
public final class WikiTabCompleter implements TabCompleter {

    /** Hard cap on suggestions sent to the client. */
    private static final int MAX_SUGGESTIONS = 60;

    private final MinecraftWikiPlugin plugin;

    public WikiTabCompleter(MinecraftWikiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.startsWith("wikiadmin") || name.equals("wikia") || name.equals("mwikiadmin")) {
            return admin(sender, args);
        }
        return wiki(sender, args);
    }

    private List<String> wiki(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("help");
            if (sender.hasPermission(plugin.permissions().search())) {
                subs.add("search");
            }
            subs.add("category");
            subs.add("article");
            if (plugin.settings().language().perPlayer()) {
                subs.add("lang");
            }
            if (sender.hasPermission(plugin.permissions().reload())) {
                subs.add("reload");
            }
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "category", "categories", "cat", "c" -> categories(sender, args[1]);
                case "article", "page", "a" -> articles(sender, args[1]);
                case "lang", "language", "l" -> filter(new ArrayList<>(plugin.messages().languages()), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && (sub.equals("search") || sub.equals("find") || sub.equals("s"))
                && plugin.settings().search().categoryFilter()) {
            return categories(sender, args[2]);
        }
        return List.of();
    }

    private List<String> admin(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("info", "reload", "rebuild", "debug", "article", "provider"));
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("article") || sub.equals("a")) {
                return articles(sender, args[1]);
            }
            if (sub.equals("debug") || sub.equals("d")) {
                return filter(new ArrayList<>(List.of("on", "off")), args[1]);
            }
        }
        return List.of();
    }

    private List<String> categories(CommandSender sender, String prefix) {
        List<String> out = new ArrayList<>();
        for (WikiCategory category : plugin.categories().enabled()) {
            if (sender.hasPermission(plugin.permissions().category(category.id()))) {
                out.add(category.id());
            }
        }
        return filter(out, prefix);
    }

    private List<String> articles(CommandSender sender, String prefix) {
        String needle = Text.fold(prefix);
        List<String> out = new ArrayList<>();
        for (Article article : plugin.repository().all()) {
            if (out.size() >= MAX_SUGGESTIONS) {
                break;
            }
            if (!article.visible()) {
                continue;
            }
            WikiCategory category = plugin.categories().get(article.categoryId());
            if (category == null || category.hidden()) {
                continue;
            }
            if (!sender.hasPermission(plugin.permissions().category(category.id()))) {
                continue;
            }
            if (article.hasOwnPermission() && !sender.hasPermission(article.permission())) {
                continue;
            }
            String id = article.id().value();
            if (needle.isEmpty() || id.startsWith(needle) || Text.fold(article.title()).startsWith(needle)) {
                out.add(id);
            }
        }
        return out;
    }

    private List<String> filter(List<String> values, String prefix) {
        String needle = Text.fold(prefix);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (out.size() >= MAX_SUGGESTIONS) {
                break;
            }
            if (needle.isEmpty() || Text.fold(value).startsWith(needle)) {
                out.add(value);
            }
        }
        return out;
    }

    /** Unused, but keeps the player reference available for future permission-aware sorting. */
    @SuppressWarnings("unused")
    private static boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }
}
