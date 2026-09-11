package dev.superseller.minecraftwiki.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.config.GuiSettings;
import dev.superseller.minecraftwiki.config.GuiItemKey;
import dev.superseller.minecraftwiki.config.ItemSpec;
import dev.superseller.minecraftwiki.config.TextSpec;
import dev.superseller.minecraftwiki.search.SearchQuery;
import dev.superseller.minecraftwiki.search.SearchResult;
import dev.superseller.minecraftwiki.util.Text;

/**
 * Search results.
 *
 * <p>The matched characters are highlighted inside the entry title, the score and the matched
 * text are shown so a player can see why something ranked where it did, and an empty result set
 * gets a proper "nothing found" entry instead of a blank grid.</p>
 */
public final class SearchResultsMenu extends WikiMenu {

    private final SearchQuery query;
    private final List<SearchResult> results;
    private int page;

    public SearchResultsMenu(MenuContext ctx, Player player, SearchQuery query,
                             List<SearchResult> results, int page) {
        super(ctx, player);
        this.query = query;
        this.results = results == null ? List.of() : List.copyOf(results);
        this.page = page;
    }

    @Override
    public MenuKind menuKind() {
        return MenuKind.SEARCH_RESULTS;
    }

    @Override
    protected Component title() {
        // The query is player input, so it goes in through an escaping placeholder.
        return Text.mini(ctx.gui().titles().searchResults(), Map.of(
                "query", shorten(query.raw(), 24)));
    }

    @Override
    protected int size() {
        return ctx.gui().sizeOf(GuiSettings.MenuKind.SEARCH_RESULTS);
    }

    @Override
    protected Pagination pagination() {
        return new Pagination(results.size(), ctx.gui().pageSize());
    }

    @Override
    protected int page() {
        return page;
    }

    @Override
    protected void goToPage(int target) {
        this.page = pagination().clamp(target);
        refresh();
    }

    @Override
    protected List<Crumb> crumbs() {
        return List.of(new Crumb(ctx.gui().item(GuiItemKey.HOME), Map.of("title", "Home"),
                () -> ctx.navigation().open(player, ctx.menus().home(player))));
    }

    @Override
    protected Crumb currentCrumb() {
        return new Crumb(ctx.gui().item(GuiItemKey.SEARCH),
                Map.of("query", shorten(query.raw(), 24)), null);
    }

    @Override
    protected void build() {
        List<Integer> slots = ctx.gui().contentSlots();
        if (slots.isEmpty()) {
            return;
        }
        if (results.isEmpty()) {
            ItemSpec spec = ctx.gui().item(GuiItemKey.SEARCH_NO_RESULTS);
            put(slots.get(0), GuiButton.display(ItemFactory.create(spec,
                    Map.of("query", Text.sanitize(query.raw())))));
            return;
        }
        int index = 0;
        for (SearchResult result : pagination().slice(results, page)) {
            if (index >= slots.size()) {
                break;
            }
            Article article = result.article();
            Material material = ctx.gui().searchResultUsesArticleIcon() && article.icon() != null
                    ? article.icon().material()
                    : ctx.gui().item(GuiItemKey.SEARCH_RESULT).material();
            TextSpec spec = new TextSpec(
                    highlightedTitle(article.title(), result),
                    ctx.gui().item(GuiItemKey.SEARCH_RESULT).lore());
            Map<String, String> placeholders = Map.of(
                    "title", article.title(),
                    "summary", article.summary().isEmpty() ? "" : Text.plain(Text.mini(article.summary())),
                    "match", describe(result),
                    "score", Integer.toString(result.score()),
                    "category", EntryRenderer.categoryTitle(ctx.categories(), article));
            put(slots.get(index++), new GuiButton(
                    ItemFactory.create(material, spec, placeholders, false),
                    click -> ctx.navigation().open(player, ctx.menus().article(player, article))));
        }
    }

    /**
     * Wraps the matched characters in the configured highlight tags.
     *
     * <p>The match comes from player input, so it is sanitised before being substituted into a
     * template that MiniMessage will parse.</p>
     */
    private String highlightedTitle(String title, SearchResult result) {
        String template = ctx.gui().item(GuiItemKey.SEARCH_RESULT).name();
        String matched = Text.sanitize(result.matchedOn());
        if (matched.isEmpty()) {
            return template.replace("<title>", title);
        }
        int at = Text.fold(title).indexOf(matched.toLowerCase(Locale.ROOT));
        String plain = title;
        if (at >= 0 && at + matched.length() <= plain.length()) {
            plain = plain.substring(0, at)
                    + ctx.gui().highlight()
                    + plain.substring(at, at + matched.length())
                    + ctx.gui().highlightEnd()
                    + plain.substring(at + matched.length());
        }
        return template.replace("<title>", plain);
    }

    private String describe(SearchResult result) {
        return switch (result.match()) {
            case EXACT_TITLE, EXACT_KEYWORD, EXACT_CATEGORY -> "exact";
            case PREFIX_TITLE, PREFIX_KEYWORD -> "starts with";
            case CONTAINS_TITLE, CONTAINS_KEYWORD, CONTAINS_SUMMARY -> "contains";
            case NONE -> "-";
        };
    }

    public List<SearchResult> results() {
        return results;
    }

    public SearchQuery query() {
        return query;
    }

    /** Convenience for tests and for the admin command. */
    public List<String> titles() {
        List<String> out = new ArrayList<>(results.size());
        for (SearchResult result : results) {
            out.add(result.article().title());
        }
        return out;
    }
}
