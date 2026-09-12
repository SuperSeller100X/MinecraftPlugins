package dev.superseller.minecraftwiki.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;

import dev.superseller.minecraftwiki.article.ArticleRepository;
import dev.superseller.minecraftwiki.config.GuiSettings;
import dev.superseller.minecraftwiki.config.GuiItemKey;
import dev.superseller.minecraftwiki.config.WikiCategory;
import dev.superseller.minecraftwiki.util.Text;

/**
 * The wiki home: one tile per enabled category that has something to show.
 *
 * <p>Categories the server owner disabled are never listed, and a category whose provider
 * produced nothing is hidden too unless {@code category.show-empty} is set - an empty tile
 * is clutter, not information.</p>
 */
public final class HomeMenu extends WikiMenu {

    private final List<WikiCategory> visible;
    private int page;

    public HomeMenu(MenuContext ctx, Player player, int page) {
        super(ctx, player);
        this.visible = resolveVisible(ctx, player);
        this.page = page;
    }

    @Override
    public MenuKind menuKind() {
        return MenuKind.HOME;
    }

    @Override
    protected Component title() {
        return Text.mini(ctx.gui().titles().home(), Map.of());
    }

    @Override
    protected int size() {
        return ctx.gui().sizeOf(GuiSettings.MenuKind.HOME);
    }

    @Override
    protected Pagination pagination() {
        return new Pagination(visible.size(), ctx.gui().pageSize());
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
    protected Crumb currentCrumb() {
        return new Crumb(ctx.gui().item(GuiItemKey.HOME), Map.of("title", "Home"), null);
    }

    @Override
    protected void build() {
        Pagination pagination = pagination();
        List<Integer> slots = ctx.gui().contentSlots();
        if (slots.isEmpty()) {
            return;
        }
        List<WikiCategory> pageEntries = pagination.slice(visible, page);
        if (pageEntries.isEmpty()) {
            put(slots.get(0), GuiButton.display(
                    ItemFactory.create(ctx.gui().item(GuiItemKey.SEARCH_NO_RESULTS), Map.of())));
            return;
        }
        ArticleRepository repository = ctx.search().repository();
        int index = 0;
        for (WikiCategory category : pageEntries) {
            if (index >= slots.size()) {
                break;
            }
            int count = repository.sizeOf(category.id());
            put(slots.get(index++), EntryRenderer.categoryTile(ctx, category, count,
                    () -> ctx.navigation().open(player, ctx.menus().category(player, category, 1))));
        }
    }

    /** Enabled categories this player may see that actually have content. */
    private static List<WikiCategory> resolveVisible(MenuContext ctx, Player player) {
        ArticleRepository repository = ctx.search().repository();
        List<WikiCategory> out = new ArrayList<>();
        for (WikiCategory category : ctx.categories().enabled()) {
            if (!player.hasPermission(ctx.permissions().category(category.id()))) {
                continue;
            }
            if (ctx.gui().showEmptyCategories() || repository.sizeOf(category.id()) > 0) {
                out.add(category);
            }
        }
        return List.copyOf(out);
    }
}
