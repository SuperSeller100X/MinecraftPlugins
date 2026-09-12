package dev.superseller.minecraftwiki.config;

import org.bukkit.Material;

/**
 * Every configurable GUI icon, with its path in {@code gui.yml} and the plain fallback
 * used when the file does not define it.
 *
 * <p>The bundled {@code gui.yml} is the source of truth for the polished appearance; the
 * fallbacks below only keep the interface functional if a key is removed, which is why
 * they are deliberately plain.</p>
 */
public enum GuiItemKey {

    FILLER("filler", Material.GRAY_STAINED_GLASS_PANE, ""),
    SEPARATOR("separator", Material.LIGHT_GRAY_STAINED_GLASS_PANE, "\u00BB"),

    HOME("items.home", Material.BOOK, "Home"),
    BACK("items.back", Material.ARROW, "Back"),
    CLOSE("items.close", Material.BARRIER, "Close"),
    PREVIOUS("items.previous", Material.SPECTRAL_ARROW, "Previous page"),
    NEXT("items.next", Material.SPECTRAL_ARROW, "Next page"),
    PAGE_INFO("items.page-info", Material.PAPER, "Page"),
    PAGE_INFO_SINGLE("items.page-info-single", Material.PAPER, "Entries"),
    SEARCH("items.search", Material.COMPASS, "Search"),
    CATEGORIES("items.categories", Material.CHEST, "Categories"),
    RELATED("items.related", Material.NAME_TAG, "Related"),
    HELP("items.help", Material.OAK_SIGN, "Help"),

    SEARCH_RESULT("search.result", Material.SPYGLASS, "Result"),
    SEARCH_NO_RESULTS("search.no-results", Material.BARRIER, "No results"),
    SEARCH_PROMPT("search.prompt", Material.WRITABLE_BOOK, "Search"),

    ARTICLE_TITLE("article.title-item", Material.BOOK, "Article"),
    ARTICLE_SECTION("article.section", Material.WRITABLE_BOOK, "Section"),
    ARTICLE_TEXT("article.text", Material.PAPER, "Text"),
    ARTICLE_PAGE_HEADER("article.page-header", Material.BOOK, "Page"),
    ARTICLE_EMPTY("article.empty", Material.BARRIER, "No content"),
    ARTICLE_ITEM_REF("article.item-ref", Material.PAPER, "Item"),
    ARTICLE_ENTITY_REF("article.entity-ref", Material.PAPER, "Entity"),
    ARTICLE_COMMAND_REF("article.command-ref", Material.COMMAND_BLOCK, "Command"),
    ARTICLE_RECIPE_REF("article.recipe-ref", Material.CRAFTING_TABLE, "Recipes"),
    ARTICLE_TAG_REF("article.tag-ref", Material.STRING, "Tag"),
    ARTICLE_SOUND_REF("article.sound-ref", Material.NOTE_BLOCK, "Sound"),
    ARTICLE_ARTICLE_REF("article.article-ref", Material.ENDER_EYE, "See also"),

    RECIPE_ARROW("recipe.arrow", Material.ARROW, "Result"),
    RECIPE_INFO("recipe.info", Material.CRAFTING_TABLE, "Recipe"),
    RECIPE_INGREDIENT("recipe.ingredient", Material.PAPER, "Ingredient"),
    RECIPE_NONE("recipe.none", Material.BARRIER, "No recipes"),
    RECIPE_EMPTY_SLOT("recipe.empty-slot", Material.LIGHT_GRAY_STAINED_GLASS_PANE, "");

    private final String path;
    private final Material fallbackMaterial;
    private final String fallbackName;

    GuiItemKey(String path, Material fallbackMaterial, String fallbackName) {
        this.path = path;
        this.fallbackMaterial = fallbackMaterial;
        this.fallbackName = fallbackName;
    }

    public String path() {
        return path;
    }

    public Material fallbackMaterial() {
        return fallbackMaterial;
    }

    public String fallbackName() {
        return fallbackName;
    }
}
