package dev.superseller.minecraftwiki.gui;

import dev.superseller.minecraftwiki.config.CategoryConfig;
import dev.superseller.minecraftwiki.config.GuiSettings;
import dev.superseller.minecraftwiki.config.PermissionNames;
import dev.superseller.minecraftwiki.config.PluginSettings;
import dev.superseller.minecraftwiki.content.ArticleContentService;
import dev.superseller.minecraftwiki.content.Labels;
import dev.superseller.minecraftwiki.input.SearchInputManager;
import dev.superseller.minecraftwiki.message.Messages;
import dev.superseller.minecraftwiki.provider.RegistrySnapshot;
import dev.superseller.minecraftwiki.search.SearchService;
import dev.superseller.minecraftwiki.session.NavigationManager;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * The services a menu needs, handed to it instead of looked up statically.
 *
 * <p>Menus never reach for global state, which is what makes them testable and makes the
 * ownership of every service explicit. A single instance is rebuilt on reload.</p>
 */
public final class MenuContext {

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final GuiSettings gui;
    private final Messages messages;
    private final PermissionNames permissions;
    private final CategoryConfig categories;
    private final SearchService search;
    private final ArticleContentService content;
    private final RegistrySnapshot snapshot;
    private final Labels labels;
    private final NavigationManager navigation;
    private final MenuFactory menus;
    private final SearchInputManager searchInput;

    public MenuContext(JavaPlugin plugin, PluginSettings settings, GuiSettings gui, Messages messages,
                       PermissionNames permissions, CategoryConfig categories, SearchService search,
                       ArticleContentService content, RegistrySnapshot snapshot, Labels labels,
                       NavigationManager navigation, MenuFactory menus, SearchInputManager searchInput) {
        this.plugin = plugin;
        this.settings = settings;
        this.gui = gui;
        this.messages = messages;
        this.permissions = permissions;
        this.categories = categories;
        this.search = search;
        this.content = content;
        this.snapshot = snapshot;
        this.labels = labels;
        this.navigation = navigation;
        this.menus = menus;
        this.searchInput = searchInput;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public PluginSettings settings() {
        return settings;
    }

    public GuiSettings gui() {
        return gui;
    }

    public Messages messages() {
        return messages;
    }

    public PermissionNames permissions() {
        return permissions;
    }

    public CategoryConfig categories() {
        return categories;
    }

    public SearchService search() {
        return search;
    }

    public ArticleContentService content() {
        return content;
    }

    public RegistrySnapshot snapshot() {
        return snapshot;
    }

    public Labels labels() {
        return labels;
    }

    public NavigationManager navigation() {
        return navigation;
    }

    public MenuFactory menus() {
        return menus;
    }

    public SearchInputManager searchInput() {
        return searchInput;
    }
}
