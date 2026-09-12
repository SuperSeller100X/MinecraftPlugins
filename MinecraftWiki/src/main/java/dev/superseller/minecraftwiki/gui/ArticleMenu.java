package dev.superseller.minecraftwiki.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleId;
import dev.superseller.minecraftwiki.article.ArticlePage;
import dev.superseller.minecraftwiki.article.ArticleRef;
import dev.superseller.minecraftwiki.article.ArticleSection;
import dev.superseller.minecraftwiki.config.GuiSettings;
import dev.superseller.minecraftwiki.config.GuiItemKey;
import dev.superseller.minecraftwiki.config.ItemSpec;
import dev.superseller.minecraftwiki.util.Sounds;
import dev.superseller.minecraftwiki.util.Text;

/**
 * The article reader.
 *
 * <p>An article body is flattened into a stream of entries - headings, text blocks, item and
 * entity references, commands, recipes, tags, sounds and related articles - and that stream is
 * paginated like any other list. Multi page articles get a header entry per page so a long
 * article stays navigable instead of becoming one endless scroll.</p>
 */
public final class ArticleMenu extends WikiMenu {

    /** Lore lines per text entry before the text continues in a second entry. */
    private static final int MAX_LORE_LINES = 12;

    /** One flattened, clickable piece of an article. */
    private record Entry(Material material, ItemSpec spec, Map<String, String> placeholders,
                         Runnable action, boolean glowing) {
    }

    private final Article article;
    private final List<Entry> entries;
    private int page;

    public ArticleMenu(MenuContext ctx, Player player, Article article) {
        super(ctx, player);
        this.article = article;
        this.entries = flatten(ctx, player, article);
        this.page = 1;
    }

    @Override
    public MenuKind menuKind() {
        return MenuKind.ARTICLE;
    }

    @Override
    protected Component title() {
        return Text.mini(ctx.gui().titles().article(), Map.of(
                "title", article.title(),
                "id", article.id().value()));
    }

    @Override
    protected int size() {
        return ctx.gui().sizeOf(GuiSettings.MenuKind.ARTICLE);
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
        var category = ctx.categories().get(article.categoryId());
        List<Crumb> crumbs = new ArrayList<>();
        crumbs.add(new Crumb(ctx.gui().item(GuiItemKey.HOME), Map.of("title", "Home"),
                () -> ctx.navigation().open(player, ctx.menus().home(player))));
        if (category != null) {
            crumbs.add(new Crumb(ctx.gui().item(GuiItemKey.CATEGORIES),
                    Map.of("title", Text.plain(Text.mini(category.title()))),
                    () -> ctx.navigation().open(player, ctx.menus().category(player, category, 1))));
        }
        return crumbs;
    }

    @Override
    protected Crumb currentCrumb() {
        ItemSpec spec = new ItemSpec(article.icon().material(), "<white><title>", List.of(),
                article.icon().glowing());
        return new Crumb(spec, Map.of("title", article.title()), null);
    }

    @Override
    protected GuiButton relatedButton() {
        List<Article> related = relatedArticles();
        ItemSpec spec = ctx.gui().item(GuiItemKey.RELATED);
        return GuiButton.display(ItemFactory.create(spec, Map.of(
                "count", Integer.toString(related.size()))));
    }

    @Override
    protected void build() {
        List<Integer> slots = ctx.gui().contentSlots();
        if (slots.isEmpty()) {
            return;
        }
        List<Entry> pageEntries = pagination().slice(entries, page);
        if (pageEntries.isEmpty()) {
            put(slots.get(0), GuiButton.display(
                    ItemFactory.create(ctx.gui().item(GuiItemKey.ARTICLE_EMPTY), Map.of())));
            return;
        }
        int index = 0;
        for (Entry entry : pageEntries) {
            if (index >= slots.size()) {
                break;
            }
            Runnable action = entry.action();
            ItemStack icon = ItemFactory.create(entry.spec().withMaterial(entry.material()),
                    entry.placeholders());
            put(slots.get(index++), action == null
                    ? GuiButton.display(icon)
                    : new GuiButton(icon, click -> action.run()));
        }
    }

    // ---------------------------------------------------------------- flattening

    private List<Article> relatedArticles() {
        List<Article> out = new ArrayList<>();
        for (ArticleId id : article.related()) {
            Article related = ctx.search().repository().get(id);
            if (related != null && related.visible()
                    && (!related.hasOwnPermission() || player.hasPermission(related.permission()))) {
                out.add(related);
            }
        }
        return out;
    }

    private static List<Entry> flatten(MenuContext ctx, Player player, Article article) {
        List<Entry> out = new ArrayList<>();
        out.add(titleEntry(ctx, article));

        List<ArticlePage> pages = ctx.content().pages(article);
        boolean multiPage = pages.size() > 1;
        int pageNumber = 0;
        for (ArticlePage articlePage : pages) {
            if (multiPage) {
                pageNumber++;
                ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_PAGE_HEADER);
                out.add(new Entry(spec.material(), spec, Map.of(
                        "page", Integer.toString(pageNumber),
                        "title", articlePage.title() == null ? "" : articlePage.title()), null, false));
            }
            for (ArticleSection section : articlePage.sections()) {
                flattenSection(ctx, player, article, section, out);
            }
        }
        if (pages.isEmpty() && !article.hasInlineContent()) {
            ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_EMPTY);
            out.add(new Entry(spec.material(), spec, Map.of(), null, false));
        }
        for (Article ref : related(ctx, player, article)) {
            ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_ARTICLE_REF);
            out.add(new Entry(spec.material(), spec, Map.of("label", ref.title()),
                    () -> ctx.navigation().open(player, ctx.menus().article(player, ref)), false));
        }
        return List.copyOf(out);
    }

    private static List<Article> related(MenuContext ctx, Player player, Article article) {
        List<Article> out = new ArrayList<>();
        for (ArticleId id : article.related()) {
            Article related = ctx.search().repository().get(id);
            if (related != null && related.visible()
                    && (!related.hasOwnPermission() || player.hasPermission(related.permission()))) {
                out.add(related);
            }
        }
        return out;
    }

    private static Entry titleEntry(MenuContext ctx, Article article) {
        ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_TITLE);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("title", article.title());
        placeholders.put("summary", article.summary().isEmpty() ? "" : Text.plain(Text.mini(article.summary())));
        placeholders.put("kind", article.kind().label());
        placeholders.put("category", article.categoryId());
        placeholders.put("version", article.version().isEmpty() ? "-" : article.version());
        placeholders.put("id", article.id().value());
        return new Entry(article.icon().material(), spec, placeholders, null, article.icon().glowing());
    }

    private static void flattenSection(MenuContext ctx, Player player, Article article,
                                       ArticleSection section, List<Entry> out) {
        List<String> lines = section.lines();
        if (!lines.isEmpty()) {
            ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_TEXT);
            for (int start = 0; start < lines.size(); start += MAX_LORE_LINES) {
                List<String> chunk = lines.subList(start, Math.min(lines.size(), start + MAX_LORE_LINES));
                String heading = section.heading() == null || section.heading().isBlank()
                        ? article.title()
                        : section.heading();
                if (start > 0) {
                    heading = heading + " \u2026";
                }
                Map<String, String> placeholders = new LinkedHashMap<>();
                placeholders.put("heading", heading);
                placeholders.put("lines", String.join("\n", chunk));
                out.add(new Entry(spec.material(), spec, placeholders, null, false));
            }
        } else if (section.heading() != null && !section.heading().isBlank()) {
            ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_SECTION);
            out.add(new Entry(spec.material(), spec,
                    Map.of("heading", section.heading(), "lines", ""), null, false));
        }
        for (ArticleRef ref : section.refs()) {
            Entry entry = refEntry(ctx, player, ref);
            if (entry != null) {
                out.add(entry);
            }
        }
        for (ArticleSection child : section.subsections()) {
            flattenSection(ctx, player, article, child, out);
        }
    }

    /** Turns a reference into a clickable entry, or null when it points at nothing usable. */
    private static Entry refEntry(MenuContext ctx, Player player, ArticleRef ref) {
        String key = Text.stripNamespace(Text.fold(ref.id()));
        String label = ref.label() == null || ref.label().isBlank() ? Text.prettify(key) : ref.label();
        return switch (ref.type()) {
            case ITEM, BLOCK -> {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    yield null;
                }
                Article target = findByKey(ctx, key, "blocks", "items", "materials");
                ItemSpec spec = target == null ? null : ctx.gui().item(GuiItemKey.ARTICLE_ITEM_REF);
                if (spec == null) {
                    yield null;
                }
                yield new Entry(material, spec, Map.of("label", label),
                        () -> ctx.navigation().open(player, ctx.menus().article(player, target)), false);
            }
            case ENTITY -> {
                Article target = findByKey(ctx, key, "mobs", "entities");
                if (target == null) {
                    yield null;
                }
                ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_ENTITY_REF);
                Material icon = target.icon() == null ? Material.CREEPER_HEAD : target.icon().material();
                yield new Entry(icon, spec, Map.of("label", label),
                        () -> ctx.navigation().open(player, ctx.menus().article(player, target)), false);
            }
            case RECIPE -> {
                ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_RECIPE_REF);
                yield new Entry(spec.material(), spec, Map.of("label", label),
                        () -> ctx.navigation().open(player, ctx.menus().recipe(player, key, 0)), false);
            }
            case TAG -> {
                Article target = findByKey(ctx, key, "tags");
                ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_TAG_REF);
                if (target == null) {
                    yield new Entry(spec.material(), spec, Map.of("label", label), null, false);
                }
                yield new Entry(spec.material(), spec, Map.of("label", label),
                        () -> ctx.navigation().open(player, ctx.menus().article(player, target)), false);
            }
            case SOUND -> {
                ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_SOUND_REF);
                yield new Entry(spec.material(), spec, Map.of("label", label),
                        () -> Sounds.play(player, ref.id(), 1f, 1f), false);
            }
            case COMMAND -> {
                ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_COMMAND_REF);
                String command = ref.id().startsWith("/") ? ref.id() : "/" + ref.id();
                yield new Entry(spec.material(), spec, Map.of("label", label), () -> {
                    // Suggesting is safe; dispatching would run a command the player only read about.
                    player.sendMessage(Component.text(command, NamedTextColor.WHITE)
                            .clickEvent(ClickEvent.suggestCommand(command)));
                    ctx.gui().sounds().click().play(player);
                }, false);
            }
            case ARTICLE -> {
                Article target = ctx.search().repository().get(ref.id());
                if (target == null) {
                    yield null;
                }
                ItemSpec spec = ctx.gui().item(GuiItemKey.ARTICLE_ARTICLE_REF);
                yield new Entry(spec.material(), spec, Map.of("label", target.title()),
                        () -> ctx.navigation().open(player, ctx.menus().article(player, target)), false);
            }
        };
    }

    /** Finds an article for a registry key across the categories that could hold it. */
    private static Article findByKey(MenuContext ctx, String key, String... categories) {
        for (String category : categories) {
            Article article = ctx.search().repository().get(ArticleId.ofCategory(category, key));
            if (article != null && article.visible()) {
                return article;
            }
        }
        return null;
    }

    public Article article() {
        return article;
    }
}
