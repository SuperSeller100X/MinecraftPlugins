package dev.superseller.minecraftwiki.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.config.GuiSettings;
import dev.superseller.minecraftwiki.config.GuiItemKey;
import dev.superseller.minecraftwiki.config.WikiCategory;
import dev.superseller.minecraftwiki.util.Text;

/**
 * One category's articles, paginated and permission filtered.
 *
 * <p>An article that requires a permission the player does not have is not listed at all, so
 * the listing never advertises something the player cannot open.</p>
 */
public final class CategoryMenu extends WikiMenu {

    private final WikiCategory category;
    private final List<Article> entries;
    private int page;

    public CategoryMenu(MenuContext ctx, Player player, WikiCategory category, int page) {
        super(ctx, player);
        this.category = category;
        this.entries = resolveEntries(ctx, player, category);
        this.page = page;
    }

    @Override
    public MenuKind menuKind() {
        return MenuKind.CATEGORY;
    }

    @Override
    protected Component title() {
        return Text.mini(ctx.gui().titles().category(), Map.of(
                "category", plain(category.title()),
                "id", category.id()));
    }

    @Override
    protected int size() {
        return ctx.gui().sizeOf(GuiSettings.MenuKind.CATEGORY);
    }

    @Override
    protected Pagination pagination() {
        return new Pagination(entries.size(), ctx.gui().pageSize());
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
        return new Crumb(new dev.superseller.minecraftwiki.config.ItemSpec(
                category.icon(), ctx.gui().categoryTile().name(), List.of(), category.glowing()),
                Map.of("title", plain(category.title())), null);
    }

    @Override
    protected void build() {
        List<Integer> slots = ctx.gui().contentSlots();
        if (slots.isEmpty()) {
            return;
        }
        List<Article> pageEntries = pagination().slice(entries, page);
        if (pageEntries.isEmpty()) {
            put(slots.get(0), GuiButton.display(
                    ItemFactory.create(ctx.gui().item(GuiItemKey.ARTICLE_EMPTY), Map.of())));
            return;
        }
        String categoryTitle = plain(category.title());
        int index = 0;
        for (Article article : pageEntries) {
            if (index >= slots.size()) {
                break;
            }
            put(slots.get(index++), EntryRenderer.articleEntry(ctx, article, categoryTitle,
                    () -> ctx.navigation().open(player, ctx.menus().article(player, article))));
        }
    }

    private static List<Article> resolveEntries(MenuContext ctx, Player player, WikiCategory category) {
        List<Article> out = new ArrayList<>();
        for (Article article : ctx.search().repository().inCategory(category.id())) {
            if (!article.visible()) {
                continue;
            }
            if (article.hasOwnPermission() && !player.hasPermission(article.permission())) {
                continue;
            }
            out.add(article);
        }
        return List.copyOf(out);
    }

    public WikiCategory category() {
        return category;
    }

    public int entryCount() {
        return entries.size();
    }
}
