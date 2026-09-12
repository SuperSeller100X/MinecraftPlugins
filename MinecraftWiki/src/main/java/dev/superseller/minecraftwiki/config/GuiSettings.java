package dev.superseller.minecraftwiki.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Typed view of {@code gui.yml}: sizes, titles, layout, every icon, every slot, sounds
 * and animation.
 *
 * <p>Loading is validating. Sizes that are not a multiple of nine, slot numbers outside
 * the inventory, overlapping ranges and unknown materials are all reported through
 * {@link ConfigIssues} and replaced with safe values.</p>
 */
public final class GuiSettings {

    /** Inventory titles, MiniMessage formatted. */
    public record Titles(String home, String category, String article, String searchResults, String recipe) {
    }

    /** Recipe viewer slots. */
    public record RecipeLayout(List<Integer> gridSlots, int singleSlot, int arrowSlot, int resultSlot, int infoSlot) {
    }

    /** Configurable, always-bounded animation. */
    public record Animation(boolean pageTransition, int steps, long intervalTicks,
                            boolean idleBorder, long idleIntervalTicks) {
    }

    /** Every GUI sound. */
    public record SoundSet(SoundSpec open, SoundSpec click, SoundSpec back, SoundSpec page,
                           SoundSpec search, SoundSpec error, SoundSpec close) {
    }

    private static final int MIN_SIZE = 27;
    private static final int MAX_SIZE = 54;
    private static final int MAX_STEPS = 40;

    private final int homeSize;
    private final int categorySize;
    private final int articleSize;
    private final int searchSize;
    private final int recipeSize;

    private final Titles titles;

    private final int headerRow;
    private final int navRow;
    private final List<Integer> contentSlots;
    private final int breadcrumbStart;
    private final int breadcrumbMax;

    private final boolean fillerEnabled;
    private final boolean fillerHeader;
    private final boolean fillerNavigation;
    private final boolean fillerSides;

    private final Map<GuiItemKey, ItemSpec> items = new EnumMap<>(GuiItemKey.class);
    private final Map<GuiSlot, Integer> slots = new EnumMap<>(GuiSlot.class);

    private final TextSpec categoryTile;
    private final TextSpec entry;
    private final TextSpec entryWithoutSummary;

    private final RecipeLayout recipe;
    private final SoundSet sounds;
    private final Animation animation;
    private final String highlight;
    private final String highlightEnd;
    private final boolean searchResultUsesArticleIcon;
    private final boolean showEmptyCategories;

    private GuiSettings(Builder builder) {
        this.homeSize = builder.homeSize;
        this.categorySize = builder.categorySize;
        this.articleSize = builder.articleSize;
        this.searchSize = builder.searchSize;
        this.recipeSize = builder.recipeSize;
        this.titles = builder.titles;
        this.headerRow = builder.headerRow;
        this.navRow = builder.navRow;
        this.contentSlots = List.copyOf(builder.contentSlots);
        this.breadcrumbStart = builder.breadcrumbStart;
        this.breadcrumbMax = builder.breadcrumbMax;
        this.fillerEnabled = builder.fillerEnabled;
        this.fillerHeader = builder.fillerHeader;
        this.fillerNavigation = builder.fillerNavigation;
        this.fillerSides = builder.fillerSides;
        this.items.putAll(builder.items);
        this.slots.putAll(builder.slots);
        this.categoryTile = builder.categoryTile;
        this.entry = builder.entry;
        this.entryWithoutSummary = builder.entryWithoutSummary;
        this.recipe = builder.recipe;
        this.sounds = builder.sounds;
        this.animation = builder.animation;
        this.highlight = builder.highlight;
        this.highlightEnd = builder.highlightEnd;
        this.searchResultUsesArticleIcon = builder.searchResultUsesArticleIcon;
        this.showEmptyCategories = builder.showEmptyCategories;
    }

    /** Parses and validates {@code gui.yml}. */
    public static GuiSettings load(YamlConfiguration config, ConfigIssues issues) {
        String file = "gui.yml";
        YamlReader reader = new YamlReader(config, file, issues);
        Builder builder = new Builder();

        builder.homeSize = size(reader, issues, file, "size.home", 54);
        builder.categorySize = size(reader, issues, file, "size.category", 54);
        builder.articleSize = size(reader, issues, file, "size.article", 54);
        builder.searchSize = size(reader, issues, file, "size.search-results", 54);
        builder.recipeSize = size(reader, issues, file, "size.recipe", 54);

        builder.titles = new Titles(
                reader.string("titles.home", "<gradient:#4f9cf9:#a56bff>Minecraft Wiki</gradient>"),
                reader.string("titles.category", "<dark_aqua>Wiki \u00BB <white><category>"),
                reader.string("titles.article", "<dark_aqua>Wiki \u00BB <white><title>"),
                reader.string("titles.search-results", "<dark_aqua>Search \u00BB <white><query>"),
                reader.string("titles.recipe", "<dark_aqua>Recipes \u00BB <white><item>"));

        int rows = builder.homeSize / 9;
        builder.headerRow = reader.integer("layout.header-row", 0, 0, rows - 1);
        builder.navRow = reader.integer("layout.nav-row", 5, 0, rows - 1);
        if (builder.headerRow == builder.navRow) {
            issues.add(ConfigIssue.warning(file, "layout.nav-row",
                    "the navigation row is the same as the header row - buttons may overlap"));
        }
        List<Integer> rowRange = parseRange(reader, issues, file, "layout.content-rows", "1-4", 0, rows - 1);
        List<Integer> colRange = parseRange(reader, issues, file, "layout.content-columns", "1-7", 0, 8);
        builder.contentSlots = new ArrayList<>();
        for (int row : rowRange) {
            for (int column : colRange) {
                builder.contentSlots.add(row * 9 + column);
            }
        }
        if (rowRange.contains(builder.headerRow) || rowRange.contains(builder.navRow)) {
            issues.add(ConfigIssue.warning(file, "layout.content-rows",
                    "the content grid overlaps the header or navigation row"));
        }
        if (builder.contentSlots.isEmpty()) {
            issues.add(ConfigIssue.error(file, "layout", "the content grid is empty - using rows 1-4, columns 1-7"));
            builder.contentSlots = defaultContentSlots();
        }
        builder.breadcrumbStart = reader.integer("layout.breadcrumb-start", 2, 0, 8);
        builder.breadcrumbMax = reader.integer("layout.breadcrumb-max", 3, 1, 5);

        builder.fillerEnabled = reader.bool("filler.enabled", true);
        builder.fillerHeader = reader.bool("filler.header", true);
        builder.fillerNavigation = reader.bool("filler.navigation", true);
        builder.fillerSides = reader.bool("filler.sides", true);

        for (GuiItemKey key : GuiItemKey.values()) {
            builder.items.put(key, readItem(reader, key));
        }
        for (GuiSlot slot : GuiSlot.values()) {
            builder.slots.put(slot, reader.integer(slot.path(), defaultSlot(slot), -1, MAX_SIZE - 1));
        }
        validateSlots(builder, issues, file, builder.homeSize);

        builder.categoryTile = readText(reader, "category", "<white><title>", List.of());
        builder.entry = readText(reader, "entry", "<white><title>", List.of("<yellow>\u25B6 Click to read"));
        builder.entryWithoutSummary = new TextSpec(builder.entry.name(),
                reader.stringList("entry.lore-without-summary"));

        builder.recipe = new RecipeLayout(
                slotList(reader, issues, file, "recipe.grid-slots",
                        List.of(10, 11, 12, 19, 20, 21, 28, 29, 30), builder.recipeSize, 9),
                reader.integer("recipe.single-slot", 20, 0, builder.recipeSize - 1),
                reader.integer("recipe.arrow-slot", 23, 0, builder.recipeSize - 1),
                reader.integer("recipe.result-slot", 25, 0, builder.recipeSize - 1),
                reader.integer("recipe.info-slot", 4, 0, builder.recipeSize - 1));

        builder.sounds = new SoundSet(
                reader.sound("sounds.open", "ui.button.click", 0.5f, 1.2f),
                reader.sound("sounds.click", "ui.button.click", 0.4f, 1.0f),
                reader.sound("sounds.back", "ui.button.click", 0.4f, 0.8f),
                reader.sound("sounds.page", "item.book.page_turn", 0.6f, 1.0f),
                reader.sound("sounds.search", "entity.experience_orb.pickup", 0.4f, 1.4f),
                reader.sound("sounds.error", "block.note_block.bass", 0.6f, 0.7f),
                reader.sound("sounds.close", "ui.button.click", 0.3f, 0.6f));
        reader.validateSounds();

        builder.animation = new Animation(
                reader.bool("animation.page-transition.enabled", true),
                reader.integer("animation.page-transition.steps", 3, 0, MAX_STEPS),
                reader.longValue("animation.page-transition.interval-ticks", 1L, 1L, 200L),
                reader.bool("animation.idle-border.enabled", false),
                reader.longValue("animation.idle-border.interval-ticks", 20L, 5L, 1200L));

        builder.highlight = reader.string("search.highlight", "<color:#ffd166><bold>");
        builder.highlightEnd = reader.string("search.highlight-end", "</bold></color>");
        builder.searchResultUsesArticleIcon = reader.bool("search.result-icon-uses-article-icon", true);
        builder.showEmptyCategories = reader.bool("category.show-empty", false);

        return new GuiSettings(builder);
    }

    private static List<Integer> defaultContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 7; column++) {
                slots.add(row * 9 + column);
            }
        }
        return slots;
    }

    private static int defaultSlot(GuiSlot slot) {
        return switch (slot) {
            case HOME -> 0;
            case CLOSE -> 8;
            case BACK -> 45;
            case PREVIOUS -> 46;
            case PAGE_INFO -> 47;
            case NEXT -> 48;
            case SEARCH -> 50;
            case CATEGORIES -> 51;
            case RELATED -> 52;
            case HELP -> 53;
        };
    }

    private static int size(YamlReader reader, ConfigIssues issues, String file, String path, int fallback) {
        int value = reader.integer(path, fallback, MIN_SIZE, MAX_SIZE);
        if (value % 9 != 0) {
            int rounded = Math.max(MIN_SIZE, Math.min(MAX_SIZE, (value / 9) * 9));
            issues.add(ConfigIssue.warning(file, path, value + " is not a multiple of 9 - using " + rounded));
            return rounded;
        }
        return value;
    }

    /** Reports slots that collide with each other, which would silently drop a button. */
    private static void validateSlots(Builder builder, ConfigIssues issues, String file, int inventorySize) {
        Map<Integer, GuiSlot> used = new java.util.HashMap<>();
        for (Map.Entry<GuiSlot, Integer> entry : builder.slots.entrySet()) {
            int slot = entry.getValue();
            if (slot < 0) {
                continue;
            }
            if (slot >= inventorySize) {
                issues.add(ConfigIssue.error(file, entry.getKey().path(),
                        "slot " + slot + " does not fit a " + inventorySize + " slot inventory - button hidden"));
                builder.slots.put(entry.getKey(), -1);
                continue;
            }
            GuiSlot previous = used.put(slot, entry.getKey());
            if (previous != null) {
                issues.add(ConfigIssue.error(file, entry.getKey().path(),
                        "slot " + slot + " is already used by " + previous.name().toLowerCase(java.util.Locale.ROOT)
                                + " - button hidden"));
                builder.slots.put(entry.getKey(), -1);
            }
        }
        Set<Integer> content = new LinkedHashSet<>(builder.contentSlots);
        for (Map.Entry<GuiSlot, Integer> entry : builder.slots.entrySet()) {
            if (entry.getValue() >= 0 && content.contains(entry.getValue())) {
                issues.add(ConfigIssue.warning(file, entry.getKey().path(),
                        "slot " + entry.getValue() + " is inside the content grid and will be overwritten by entries"));
            }
        }
    }

    private static ItemSpec readItem(YamlReader reader, GuiItemKey key) {
        Material material = reader.material(key.path() + ".material", key.fallbackMaterial());
        String name = reader.string(key.path() + ".name", key.fallbackName());
        List<String> lore = reader.stringList(key.path() + ".lore");
        boolean glowing = reader.bool(key.path() + ".glowing", false);
        return new ItemSpec(material, name, lore, glowing);
    }

    private static TextSpec readText(YamlReader reader, String path, String defaultName, List<String> defaultLore) {
        String name = reader.string(path + ".name", defaultName);
        List<String> lore = reader.contains(path + ".lore") ? reader.stringList(path + ".lore") : defaultLore;
        return new TextSpec(name, lore);
    }

    private static List<Integer> slotList(YamlReader reader, ConfigIssues issues, String file, String path,
                                          List<Integer> fallback, int inventorySize, int expected) {
        List<?> raw = reader.root().getList(path);
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        List<Integer> slots = new ArrayList<>();
        for (Object entry : raw) {
            if (entry instanceof Number number) {
                int slot = number.intValue();
                if (slot < 0 || slot >= inventorySize) {
                    issues.add(ConfigIssue.error(file, path, "slot " + slot + " is outside a "
                            + inventorySize + " slot inventory - ignored"));
                    continue;
                }
                slots.add(slot);
            } else {
                issues.add(ConfigIssue.warning(file, path, "'" + entry + "' is not a slot number - ignored"));
            }
        }
        if (slots.size() != expected) {
            issues.add(ConfigIssue.warning(file, path, "expected " + expected + " slots but found "
                    + slots.size() + " - the recipe grid may look incomplete"));
        }
        return List.copyOf(slots);
    }

    /** Parses an inclusive range such as {@code 1-4}, a list {@code 1,3} or a single value. */
    static List<Integer> parseRange(YamlReader reader, ConfigIssues issues, String file, String path,
                                    String fallback, int min, int max) {
        String raw = reader.string(path, fallback);
        List<Integer> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int dash = token.indexOf('-');
            try {
                if (dash > 0) {
                    int from = Integer.parseInt(token.substring(0, dash).trim());
                    int to = Integer.parseInt(token.substring(dash + 1).trim());
                    if (from > to) {
                        int swap = from;
                        from = to;
                        to = swap;
                    }
                    for (int value = from; value <= to; value++) {
                        if (value >= min && value <= max && !out.contains(value)) {
                            out.add(value);
                        }
                    }
                } else {
                    int value = Integer.parseInt(token);
                    if (value >= min && value <= max && !out.contains(value)) {
                        out.add(value);
                    } else {
                        issues.add(ConfigIssue.warning(file, path, value + " is outside " + min + ".." + max
                                + " - ignored"));
                    }
                }
            } catch (NumberFormatException error) {
                issues.add(ConfigIssue.warning(file, path, "'" + token + "' is not a number or range - ignored"));
            }
        }
        return out;
    }

    public int sizeOf(MenuKind kind) {
        return switch (kind) {
            case HOME -> homeSize;
            case CATEGORY -> categorySize;
            case ARTICLE -> articleSize;
            case SEARCH_RESULTS -> searchSize;
            case RECIPE -> recipeSize;
        };
    }

    /** Which screens exist, used to look up a size. */
    public enum MenuKind {
        HOME, CATEGORY, ARTICLE, SEARCH_RESULTS, RECIPE
    }

    public Titles titles() {
        return titles;
    }

    public int headerRow() {
        return headerRow;
    }

    public int navRow() {
        return navRow;
    }

    public List<Integer> contentSlots() {
        return contentSlots;
    }

    public int pageSize() {
        return contentSlots.size();
    }

    public int breadcrumbStart() {
        return breadcrumbStart;
    }

    public int breadcrumbMax() {
        return breadcrumbMax;
    }

    public boolean fillerEnabled() {
        return fillerEnabled;
    }

    public boolean fillerHeader() {
        return fillerHeader;
    }

    public boolean fillerNavigation() {
        return fillerNavigation;
    }

    public boolean fillerSides() {
        return fillerSides;
    }

    public ItemSpec item(GuiItemKey key) {
        return items.getOrDefault(key, ItemSpec.plain(key.fallbackMaterial(), key.fallbackName()));
    }

    /** Returns -1 when the button is hidden or does not fit the inventory. */
    public int slot(GuiSlot slot) {
        return slots.getOrDefault(slot, -1);
    }

    public TextSpec categoryTile() {
        return categoryTile;
    }

    public TextSpec entry() {
        return entry;
    }

    public TextSpec entryWithoutSummary() {
        return entryWithoutSummary;
    }

    public RecipeLayout recipe() {
        return recipe;
    }

    public SoundSet sounds() {
        return sounds;
    }

    public Animation animation() {
        return animation;
    }

    public String highlight() {
        return highlight;
    }

    public String highlightEnd() {
        return highlightEnd;
    }

    public boolean searchResultUsesArticleIcon() {
        return searchResultUsesArticleIcon;
    }

    public boolean showEmptyCategories() {
        return showEmptyCategories;
    }

    /** Mutable accumulator used while parsing. */
    private static final class Builder {
        private int homeSize = 54;
        private int categorySize = 54;
        private int articleSize = 54;
        private int searchSize = 54;
        private int recipeSize = 54;
        private Titles titles = new Titles("", "", "", "", "");
        private int headerRow;
        private int navRow;
        private List<Integer> contentSlots = new ArrayList<>();
        private int breadcrumbStart = 2;
        private int breadcrumbMax = 3;
        private boolean fillerEnabled = true;
        private boolean fillerHeader = true;
        private boolean fillerNavigation = true;
        private boolean fillerSides = true;
        private final Map<GuiItemKey, ItemSpec> items = new EnumMap<>(GuiItemKey.class);
        private final Map<GuiSlot, Integer> slots = new EnumMap<>(GuiSlot.class);
        private TextSpec categoryTile = new TextSpec("<white><title>", List.of());
        private TextSpec entry = new TextSpec("<white><title>", List.of());
        private TextSpec entryWithoutSummary = new TextSpec("<white><title>", List.of());
        private RecipeLayout recipe = new RecipeLayout(
                List.of(10, 11, 12, 19, 20, 21, 28, 29, 30), 20, 23, 25, 4);
        private SoundSet sounds = new SoundSet(SoundSpec.SILENT, SoundSpec.SILENT, SoundSpec.SILENT,
                SoundSpec.SILENT, SoundSpec.SILENT, SoundSpec.SILENT, SoundSpec.SILENT);
        private Animation animation = new Animation(true, 3, 1L, false, 20L);
        private String highlight = "<color:#ffd166><bold>";
        private String highlightEnd = "</bold></color>";
        private boolean searchResultUsesArticleIcon = true;
        private boolean showEmptyCategories;
    }
}
