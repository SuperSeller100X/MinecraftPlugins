package dev.superseller.minecraftwiki.config;

/**
 * Named GUI button slots. Slots resolve to {@code -1} when the server owner hides a
 * button in {@code gui.yml}.
 */
public enum GuiSlot {

    HOME("navigation.header.home"),
    CLOSE("navigation.header.close"),
    BACK("navigation.slots.back"),
    PREVIOUS("navigation.slots.previous"),
    PAGE_INFO("navigation.slots.page-info"),
    NEXT("navigation.slots.next"),
    SEARCH("navigation.slots.search"),
    CATEGORIES("navigation.slots.categories"),
    RELATED("navigation.slots.related"),
    HELP("navigation.slots.help");

    private final String path;

    GuiSlot(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
