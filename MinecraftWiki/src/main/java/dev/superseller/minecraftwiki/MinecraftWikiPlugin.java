package dev.superseller.minecraftwiki;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.minecraftwiki.api.MinecraftWikiApi;
import dev.superseller.minecraftwiki.article.Article;
import dev.superseller.minecraftwiki.article.ArticleRepository;
import dev.superseller.minecraftwiki.command.WikiAdminCommand;
import dev.superseller.minecraftwiki.command.WikiCommand;
import dev.superseller.minecraftwiki.command.WikiTabCompleter;
import dev.superseller.minecraftwiki.config.CategoryConfig;
import dev.superseller.minecraftwiki.config.ConfigFile;
import dev.superseller.minecraftwiki.config.ConfigIssue;
import dev.superseller.minecraftwiki.config.ConfigIssues;
import dev.superseller.minecraftwiki.config.GuiSettings;
import dev.superseller.minecraftwiki.config.PermissionNames;
import dev.superseller.minecraftwiki.config.PluginSettings;
import dev.superseller.minecraftwiki.config.WikiCategory;
import dev.superseller.minecraftwiki.content.ArticleContentService;
import dev.superseller.minecraftwiki.content.ContentResolver;
import dev.superseller.minecraftwiki.content.ContentResolvers;
import dev.superseller.minecraftwiki.content.Labels;
import dev.superseller.minecraftwiki.gui.ArticleMenu;
import dev.superseller.minecraftwiki.gui.CategoryMenu;
import dev.superseller.minecraftwiki.gui.HelpMenu;
import dev.superseller.minecraftwiki.gui.HomeMenu;
import dev.superseller.minecraftwiki.gui.MenuContext;
import dev.superseller.minecraftwiki.gui.MenuFactory;
import dev.superseller.minecraftwiki.gui.RecipeMenu;
import dev.superseller.minecraftwiki.gui.SearchResultsMenu;
import dev.superseller.minecraftwiki.gui.WikiMenu;
import dev.superseller.minecraftwiki.input.SearchInputManager;
import dev.superseller.minecraftwiki.listener.WikiGuiListener;
import dev.superseller.minecraftwiki.message.Messages;
import dev.superseller.minecraftwiki.provider.ArticleProvider;
import dev.superseller.minecraftwiki.provider.ArticleSink;
import dev.superseller.minecraftwiki.provider.CustomArticleProvider;
import dev.superseller.minecraftwiki.provider.DetailProviders;
import dev.superseller.minecraftwiki.provider.EntityProvider;
import dev.superseller.minecraftwiki.provider.MaterialProvider;
import dev.superseller.minecraftwiki.provider.ProviderIds;
import dev.superseller.minecraftwiki.provider.RecipeProvider;
import dev.superseller.minecraftwiki.provider.RegistryArticleProvider;
import dev.superseller.minecraftwiki.provider.RegistrySnapshot;
import dev.superseller.minecraftwiki.provider.WikiProvider;
import dev.superseller.minecraftwiki.article.ArticleKind;
import dev.superseller.minecraftwiki.scheduler.PlatformScheduler;
import dev.superseller.minecraftwiki.search.SearchIndex;
import dev.superseller.minecraftwiki.search.SearchQuery;
import dev.superseller.minecraftwiki.search.SearchResult;
import dev.superseller.minecraftwiki.search.SearchService;
import dev.superseller.minecraftwiki.session.NavigationManager;
import dev.superseller.minecraftwiki.session.WikiSession;
import dev.superseller.minecraftwiki.util.Text;

/**
 * MinecraftWiki: an in-game encyclopedia for the running server.
 *
 * <p>Startup order matters and is deliberate:</p>
 * <ol>
 *   <li>configuration is parsed and validated, problems collected rather than ignored;</li>
 *   <li>the registries are snapshotted once on the main thread;</li>
 *   <li>providers turn that snapshot into an immutable article catalogue;</li>
 *   <li>the search index is built from the catalogue;</li>
 *   <li>only then are commands, listeners and the API exposed.</li>
 * </ol>
 *
 * <p>Everything a reader touches afterwards is immutable, which is what allows searching and
 * article rendering to run off the main thread. Nothing in this plugin reaches into Minecraft
 * internals: the only reflection used is against Paper's own public scheduler and game rule
 * classes, so the same jar runs on Paper, Purpur and Folia.</p>
 */
public final class MinecraftWikiPlugin extends JavaPlugin implements MenuFactory {

    /** Outcome of a reload, reported to whoever asked for it. */
    public record ReloadResult(int categories, int articles, long millis, long errors, long warnings) {
    }

    private static final String PREFIX_CONFIG = "[Wiki/Config] ";
    private static final String PREFIX_SEARCH = "[Wiki/Search] ";
    private static final String PREFIX_GUI = "[Wiki/GUI] ";

    private ConfigIssues issues = new ConfigIssues();

    private PluginSettings settings;
    private GuiSettings gui;
    private PermissionNames permissions;
    private CategoryConfig categories;
    private Messages messages;
    private Labels labels;

    private RegistrySnapshot snapshot;
    private ArticleRepository repository = new ArticleRepository(List.of());
    private SearchService search;
    private ArticleContentService content;
    private NavigationManager navigation;
    private SearchInputManager searchInput;
    private MenuContext menuContext;

    private final List<WikiProvider> providers = new ArrayList<>();
    private final List<WikiProvider> extraProviders = new ArrayList<>();
    private final List<ContentResolver> extraResolvers = new ArrayList<>();

    private volatile boolean debug;
    private boolean started;

    @Override
    public void onEnable() {
        long begin = System.nanoTime();
        PlatformScheduler.init(this);

        if (!loadConfiguration()) {
            getLogger().severe(PREFIX_CONFIG + "configuration could not be loaded; the wiki is disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        rebuildAll(false);

        registerCommands();
        getServer().getPluginManager().registerEvents(new WikiGuiListener(navigation, search), this);
        getServer().getPluginManager().registerEvents(searchInput, this);
        MinecraftWikiApi.register(this);

        started = true;
        long millis = (System.nanoTime() - begin) / 1_000_000L;
        if (settings.logging().startupSummary()) {
            getLogger().info(settings.logging().prefix() + " enabled in " + millis + "ms: "
                    + repository.size() + " articles in " + categories.size() + " categories, "
                    + search.index().tokenCount() + " indexed tokens from " + providers.size() + " providers.");
            getLogger().info(settings.logging().prefix() + " running on " + PlatformScheduler.platformName()
                    + " (Minecraft " + snapshot.minecraftVersion() + "), Java "
                    + System.getProperty("java.version") + ".");
        }
        if (issues.size() > 0) {
            getLogger().warning(settings.logging().prefix() + " configuration finished with "
                    + issues.count(ConfigIssue.Level.ERROR) + " error(s) and "
                    + issues.count(ConfigIssue.Level.WARNING) + " warning(s).");
        }
    }

    @Override
    public void onDisable() {
        // Close every open wiki first so no inventory is left pointing at a disposed menu.
        for (Player player : getServer().getOnlinePlayers()) {
            WikiSession session = navigation.peek(player.getUniqueId());
            if (session == null) {
                continue;
            }
            WikiMenu menu = session.current();
            if (menu != null && !menu.disposed()) {
                if (player.isOnline() && player.getOpenInventory() != null
                        && player.getOpenInventory().getTopInventory() == menu.inventory()) {
                    player.closeInventory();
                }
                menu.dispose();
            }
        }
        navigation.clear();
        if (search != null) {
            search.clearCooldowns();
        }
        if (content != null) {
            content.clear();
        }
        PlatformScheduler.cancelTasks();
        MinecraftWikiApi.unregister();
        providers.clear();
        started = false;
    }

    // ------------------------------------------------------------------- loading

    /** Parses every configuration file. Returns false only when the core file is unusable. */
    private boolean loadConfiguration() {
        issues = new ConfigIssues();
        saveDefaultConfig();

        ConfigFile configFile = file("config.yml");
        ConfigFile guiFile = file("gui.yml");
        ConfigFile categoriesFile = file("categories.yml");
        ConfigFile messagesFile = file("messages.yml");
        ConfigFile permissionsFile = file("permissions.yml");

        settings = PluginSettings.load(configFile.config(), issues);
        debug = settings.debug();
        gui = GuiSettings.load(guiFile.config(), issues);
        permissions = PermissionNames.load(permissionsFile.config(), issues);
        categories = CategoryConfig.load(categoriesFile.config(), issues);
        labels = Labels.load(guiFile.config());

        ConfigFile languageFile = settings.language().messageFile().equals("messages.yml")
                ? messagesFile
                : file(settings.language().messageFile());
        messages = new Messages(this, settings.language().fallbackToDefault());
        messages.load(languageFile, settings.language().defaultLanguage(), issues);

        issues.report(getLogger());
        return settings != null && gui != null && permissions != null && categories != null;
    }

    private ConfigFile file(String name) {
        ConfigFile file = new ConfigFile(this, name);
        file.load(issues);
        return file;
    }

    /** Rebuilds snapshot, catalogue, index and services. */
    private void rebuildAll(boolean async) {
        long begin = System.nanoTime();
        snapshot = RegistrySnapshot.capture(settings.content().enabledProviders(), issues);
        providers.clear();
        providers.addAll(buildProviders());

        List<Article> articles = new ArrayList<>();
        Map<String, Integer> perProvider = new LinkedHashMap<>();
        Set<dev.superseller.minecraftwiki.article.ArticleId> seen = new java.util.HashSet<>();
        ArticleSink sink = article -> {
            if (article == null || !seen.add(article.id())) {
                return false;
            }
            articles.add(article);
            return true;
        };
        for (WikiProvider provider : providers) {
            int before = articles.size();
            try {
                provider.contribute(sink);
            } catch (Throwable error) {
                issues.add(ConfigIssue.error("provider", provider.id(),
                        "failed to contribute articles: " + error));
                getLogger().warning(settings.logging().prefix() + " provider '" + provider.id()
                        + "' failed: " + error);
            }
            perProvider.put(provider.id(), articles.size() - before);
        }
        repository = new ArticleRepository(articles);
        validateArticles();

        SearchIndex index = SearchIndex.build(repository, categories);
        if (search == null) {
            search = new SearchService(settings, repository, index);
        } else {
            search.update(repository, index);
        }
        search.updateCategories(categories);

        List<ContentResolver> resolvers = new ArrayList<>(ContentResolvers.all(snapshot, labels));
        resolvers.addAll(extraResolvers);
        ArticleContentService previousContent = content;
        content = new ArticleContentService(resolvers, settings.content().cacheSize(),
                settings.content().cacheEnabled());
        if (previousContent != null) {
            // Dropping the old cache is what stops the previous snapshot being retained.
            previousContent.clear();
        }

        if (navigation == null) {
            navigation = new NavigationManager(settings.navigation().historySize(),
                    settings.navigation().keepSessionOpen(), settings.language().defaultLanguage());
        } else {
            navigation.configure(settings.navigation().historySize(),
                    settings.navigation().keepSessionOpen(), settings.language().defaultLanguage());
            // Sessions may point at menus built from the previous catalogue.
            navigation.clear();
        }
        if (searchInput == null) {
            searchInput = new SearchInputManager(settings, gui, messages, permissions, search,
                    navigation, this, settings.input().cancelWord(),
                    settings.input().timeoutSeconds());
        } else {
            searchInput.configure(settings, gui, messages, permissions, this);
        }
        menuContext = new MenuContext(this, settings, gui, messages, permissions, categories,
                search, content, snapshot, labels, navigation, this, searchInput);

        long millis = (System.nanoTime() - begin) / 1_000_000L;
        if (debug) {
            for (Map.Entry<String, Integer> entry : perProvider.entrySet()) {
                getLogger().info(PREFIX_SEARCH + "provider " + entry.getKey() + " -> "
                        + entry.getValue() + " article(s)");
            }
        }
        if (settings.logging().startupSummary() && started) {
            getLogger().info(PREFIX_SEARCH + "index rebuilt: " + index.tokenCount() + " tokens over "
                    + index.size() + " articles in " + index.buildMillis() + "ms");
        }
        if (async) {
            getLogger().info(settings.logging().prefix() + " rebuild finished in " + millis + "ms ("
                    + repository.size() + " articles).");
        }
    }

    /** Builds the provider list from the enabled providers in config.yml. */
    private List<WikiProvider> buildProviders() {
        String version = snapshot.minecraftVersion();
        Set<String> enabled = settings.content().enabledProviders();
        List<WikiProvider> out = new ArrayList<>();
        if (enabled.contains(ProviderIds.BLOCKS)) {
            out.add(MaterialProvider.blocks(snapshot, version));
        }
        if (enabled.contains(ProviderIds.ITEMS)) {
            out.add(MaterialProvider.items(snapshot, version));
        }
        if (categories.exists("materials")) {
            out.add(MaterialProvider.combined(snapshot, version));
        }
        if (enabled.contains(ProviderIds.ENTITIES)) {
            out.add(EntityProvider.mobs(snapshot, version));
            out.add(EntityProvider.all(snapshot, version));
        }
        if (enabled.contains(ProviderIds.RECIPES)) {
            out.add(new RecipeProvider("recipes", snapshot, version));
        }
        addRegistry(out, enabled, ProviderIds.ENCHANTMENTS, "enchantments", ArticleKind.ENCHANTMENT,
                org.bukkit.Material.ENCHANTED_BOOK, snapshot.enchantments(), version);
        addRegistry(out, enabled, ProviderIds.EFFECTS, "effects", ArticleKind.EFFECT,
                org.bukkit.Material.POTION, snapshot.effects(), version);
        addRegistry(out, enabled, ProviderIds.POTIONS, "potions", ArticleKind.POTION,
                org.bukkit.Material.POTION, snapshot.potions(), version);
        addRegistry(out, enabled, ProviderIds.BIOMES, "biomes", ArticleKind.BIOME,
                org.bukkit.Material.OAK_SAPLING, snapshot.biomes(), version);
        addRegistry(out, enabled, ProviderIds.STRUCTURES, "structures", ArticleKind.STRUCTURE,
                org.bukkit.Material.CHISELED_STONE_BRICKS, snapshot.structures(), version);
        addRegistry(out, enabled, ProviderIds.SOUNDS, "sounds", ArticleKind.SOUND,
                org.bukkit.Material.NOTE_BLOCK, snapshot.sounds(), version);
        addRegistry(out, enabled, ProviderIds.PARTICLES, "particles", ArticleKind.PARTICLE,
                org.bukkit.Material.GLOWSTONE_DUST, snapshot.particles(), version);
        addRegistry(out, enabled, ProviderIds.ATTRIBUTES, "attributes", ArticleKind.ATTRIBUTE,
                org.bukkit.Material.GOLDEN_CARROT, snapshot.attributes(), version);
        addRegistry(out, enabled, ProviderIds.DAMAGE_TYPES, "damage-types", ArticleKind.DAMAGE_TYPE,
                org.bukkit.Material.IRON_SWORD, snapshot.damageTypes(), version);
        addRegistry(out, enabled, ProviderIds.GAME_EVENTS, "game-events", ArticleKind.GAME_EVENT,
                org.bukkit.Material.SCULK_SENSOR, snapshot.gameEvents(), version);
        addRegistry(out, enabled, ProviderIds.VILLAGER_PROFESSIONS, "villager-professions",
                ArticleKind.VILLAGER_PROFESSION, org.bukkit.Material.EMERALD,
                snapshot.villagerProfessions(), version);
        addRegistry(out, enabled, ProviderIds.ADVANCEMENTS, "advancements", ArticleKind.ADVANCEMENT,
                org.bukkit.Material.GOLD_BLOCK, snapshot.advancements(), version);
        for (WikiProvider provider : DetailProviders.all(snapshot, version)) {
            if (enabled.contains(provider.id())) {
                out.add(provider);
            }
        }
        if (enabled.contains(ProviderIds.CUSTOM_ARTICLES)) {
            out.add(CustomArticleProvider.load(file("articles.yml").config(), categories, version, issues));
        }
        out.addAll(extraProviders);
        return out;
    }

    private void addRegistry(List<WikiProvider> out, Set<String> enabled, String id, String categoryId,
                             ArticleKind kind, org.bukkit.Material icon,
                             Map<String, dev.superseller.minecraftwiki.provider.RegistryEntry> entries,
                             String version) {
        if (!enabled.contains(id)) {
            return;
        }
        if (!categories.exists(categoryId)) {
            issues.add(ConfigIssue.warning("config.yml", "content.providers." + id,
                    "the category '" + categoryId + "' does not exist in categories.yml, "
                            + "so its articles would be invisible"));
            return;
        }
        out.add(new RegistryArticleProvider(id, categoryId, kind, icon, entries, snapshot, version));
    }

    /** Reports articles whose category is missing or disabled, which would hide them silently. */
    private void validateArticles() {
        Map<String, Integer> orphans = new LinkedHashMap<>();
        Map<String, Integer> hidden = new LinkedHashMap<>();
        for (Article article : repository.all()) {
            WikiCategory category = categories.get(article.categoryId());
            if (category == null) {
                orphans.merge(article.categoryId(), 1, Integer::sum);
            } else if (category.hidden()) {
                hidden.merge(article.categoryId(), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : orphans.entrySet()) {
            issues.add(ConfigIssue.error("articles", entry.getKey(),
                    entry.getValue() + " article(s) reference a category that does not exist in "
                            + "categories.yml - they are not reachable"));
        }
        for (Map.Entry<String, Integer> entry : hidden.entrySet()) {
            issues.add(ConfigIssue.info("articles", entry.getKey(),
                    entry.getValue() + " article(s) belong to a disabled category and stay hidden"));
        }
    }

    // -------------------------------------------------------------------- reload

    /** Reloads configuration and rebuilds every derived structure. */
    public ReloadResult reloadAll() {
        long begin = System.nanoTime();
        if (!loadConfiguration()) {
            return new ReloadResult(0, 0, 0L, 1L, 0L);
        }
        rebuildAll(settings.content().rebuildIndexAsync());
        issues.report(getLogger());
        long millis = (System.nanoTime() - begin) / 1_000_000L;
        if (settings.navigation().reopenAfterReload()) {
            reopenOpenMenus();
        }
        return new ReloadResult(categories.size(), repository.size(), millis,
                issues.count(ConfigIssue.Level.ERROR), issues.count(ConfigIssue.Level.WARNING));
    }

    /** Rebuilds the catalogue and index without re-reading configuration. */
    public long rebuildContent() {
        long begin = System.nanoTime();
        rebuildAll(false);
        return (System.nanoTime() - begin) / 1_000_000L;
    }

    private void reopenOpenMenus() {
        for (Player player : getServer().getOnlinePlayers()) {
            WikiSession session = navigation.peek(player.getUniqueId());
            if (session == null) {
                continue;
            }
            WikiMenu menu = session.current();
            if (menu == null) {
                continue;
            }
            PlatformScheduler.runEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!menu.disposed()) {
                    menu.dispose();
                }
                navigation.openRoot(player, home(player));
            });
        }
    }

    // ------------------------------------------------------------------ commands

    private void registerCommands() {
        WikiCommand wikiCommand = new WikiCommand(this);
        WikiAdminCommand adminCommand = new WikiAdminCommand(this);
        WikiTabCompleter completer = new WikiTabCompleter(this);
        bind("wiki", wikiCommand, completer);
        bind("wikiadmin", adminCommand, completer);
        registerCategoryPermissions();
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor,
                      org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning(PREFIX_GUI + "the '" + name + "' command is missing from plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    /** Registers one permission per category so browsing works without a permissions plugin. */
    private void registerCategoryPermissions() {
        for (WikiCategory category : categories.all()) {
            String node = category.permission() == null
                    ? permissions.category(category.id())
                    : category.permission();
            try {
                if (getServer().getPluginManager().getPermission(node) != null) {
                    continue;
                }
                org.bukkit.permissions.Permission permission = new org.bukkit.permissions.Permission(
                        node, "Browse the " + Text.plain(Text.mini(category.title())) + " wiki category",
                        org.bukkit.permissions.PermissionDefault.TRUE);
                getServer().getPluginManager().addPermission(permission);
            } catch (Throwable error) {
                getLogger().warning(PREFIX_CONFIG + "could not register permission '" + node + "': " + error);
            }
        }
    }

    // ------------------------------------------------------------------- opening

    public void openHome(Player player) {
        navigation.openRoot(player, home(player));
    }

    public void openCategory(Player player, WikiCategory category) {
        navigation.open(player, category(player, category, 1));
    }

    public void openArticle(Player player, Article article) {
        navigation.open(player, article(player, article));
    }

    public void openSearchResults(Player player, SearchQuery query, List<SearchResult> results) {
        navigation.open(player, searchResults(player, query, results, 1));
    }

    /** Finds an article by exact title when the id lookup fails, for friendlier commands. */
    public Article findArticleByTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return null;
        }
        String needle = Text.fold(rawTitle.trim());
        for (Article article : repository.all()) {
            if (Text.fold(article.title()).equals(needle)) {
                return article;
            }
        }
        for (Article article : repository.all()) {
            if (Text.fold(article.title()).replace(" ", "_").equals(needle.replace(" ", "_"))) {
                return article;
            }
        }
        return null;
    }

    // --------------------------------------------------------------- MenuFactory

    @Override
    public WikiMenu home(Player player) {
        return new HomeMenu(menuContext, player, 1);
    }

    @Override
    public WikiMenu category(Player player, WikiCategory category, int page) {
        return new CategoryMenu(menuContext, player, category, page);
    }

    @Override
    public WikiMenu article(Player player, Article article) {
        return new ArticleMenu(menuContext, player, article);
    }

    @Override
    public WikiMenu searchResults(Player player, SearchQuery query, List<SearchResult> results, int page) {
        return new SearchResultsMenu(menuContext, player, query, results, page);
    }

    @Override
    public WikiMenu recipe(Player player, String resultKey, int recipeIndex) {
        return new RecipeMenu(menuContext, player, resultKey, recipeIndex);
    }

    @Override
    public WikiMenu help(Player player) {
        return new HelpMenu(menuContext, player);
    }

    // ----------------------------------------------------------------- accessors

    public PluginSettings settings() {
        return settings;
    }

    public GuiSettings gui() {
        return gui;
    }

    public PermissionNames permissions() {
        return permissions;
    }

    public CategoryConfig categories() {
        return categories;
    }

    public Messages messages() {
        return messages;
    }

    public RegistrySnapshot snapshot() {
        return snapshot;
    }

    public ArticleRepository repository() {
        return repository;
    }

    public SearchService search() {
        return search;
    }

    public ArticleContentService content() {
        return content;
    }

    public NavigationManager navigation() {
        return navigation;
    }

    public SearchInputManager searchInput() {
        return searchInput;
    }

    public List<WikiProvider> providers() {
        return List.copyOf(providers);
    }

    public boolean debug() {
        return debug;
    }

    public void debug(boolean enabled) {
        this.debug = enabled;
        getLogger().info(settings.logging().prefix() + " debug logging " + (enabled ? "enabled" : "disabled"));
    }

    /** Registers an extra provider; it takes part in the next catalogue rebuild. */
    public void registerProvider(WikiProvider provider) {
        if (provider != null) {
            extraProviders.add(provider);
        }
    }

    /** Registers an extra content resolver; it applies to articles resolved from now on. */
    public void registerResolver(ContentResolver resolver) {
        if (resolver != null) {
            extraResolvers.add(resolver);
        }
    }

    /** Logs a debug line when debug mode is on. */
    public void debug(String area, String message) {
        if (debug) {
            getLogger().info("[Wiki/" + area + "] " + message);
        }
    }

    /** True once the plugin finished enabling and all services exist. */
    public boolean isReady() {
        return started && menuContext != null;
    }

    /** Provider count by id, used by the admin command and tests. */
    public Map<String, Integer> providerCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (WikiProvider provider : providers) {
            int count = 0;
            if (provider instanceof ArticleProvider articleProvider) {
                for (String category : articleProvider.categories()) {
                    count += repository.sizeOf(category);
                }
            }
            out.put(provider.id().toLowerCase(Locale.ROOT), count);
        }
        return out;
    }
}
