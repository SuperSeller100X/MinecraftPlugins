package dev.superseller.minecraftwiki.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import dev.superseller.minecraftwiki.config.GuiItemKey;
import dev.superseller.minecraftwiki.config.GuiSlot;
import dev.superseller.minecraftwiki.config.ItemSpec;
import dev.superseller.minecraftwiki.scheduler.PlatformScheduler;
import dev.superseller.minecraftwiki.session.WikiSession;
import dev.superseller.minecraftwiki.util.Text;

/**
 * The base class every wiki screen extends.
 *
 * <p>It owns the inventory, the slot-to-button map, the header breadcrumb, the navigation
 * row, the border filler and the page transition animation, so a concrete menu only has to
 * say what its content grid contains. That is what keeps five screens from duplicating a
 * thousand lines of layout code.</p>
 *
 * <p>All rendering happens on the player's own thread. The animation schedules a bounded
 * number of tasks and every one of them is cancelled when the menu is disposed, so a player
 * closing a menu mid-animation cannot leak a task or write to a closed inventory.</p>
 */
public abstract class WikiMenu {

    /** Which screen this is. */
    public enum MenuKind {
        HOME, CATEGORY, ARTICLE, SEARCH_RESULTS, RECIPE, HELP
    }

    /** One breadcrumb entry. */
    public record Crumb(ItemSpec icon, Map<String, String> placeholders, Runnable action) {
    }

    private static final int MIN_SIZE = 9;
    private static final int MAX_SIZE = 54;
    private static final int COLUMNS = 9;

    protected final MenuContext ctx;
    protected final Player player;
    protected final WikiSession session;

    private final MenuHolder holder = new MenuHolder();
    private final Map<Integer, GuiButton> buttons = new LinkedHashMap<>();
    private final List<PlatformScheduler.ScheduledTask> tasks = new ArrayList<>();

    private Inventory inventory;
    private boolean disposed;

    protected WikiMenu(MenuContext ctx, Player player) {
        this.ctx = ctx;
        this.player = player;
        this.session = ctx.navigation().session(player);
    }

    // ------------------------------------------------------------- to implement

    public abstract MenuKind menuKind();

    protected abstract Component title();

    protected abstract int size();

    /** Adds this menu's content buttons. */
    protected abstract void build();

    /** Breadcrumb entries, oldest first. The current screen is added automatically. */
    protected List<Crumb> crumbs() {
        return List.of();
    }

    /** The breadcrumb entry for this screen, or null to show no breadcrumb bar. */
    protected Crumb currentCrumb() {
        return null;
    }

    /** Pagination for this screen, or null when the screen is not paginated. */
    protected Pagination pagination() {
        return null;
    }

    /** The page currently shown. */
    protected int page() {
        return 1;
    }

    /** Moves to another page of this screen. */
    protected void goToPage(int page) {
        // not paginated
    }

    /** The context specific "related" button, or null to leave the slot empty. */
    protected GuiButton relatedButton() {
        return null;
    }

    /** Called after the inventory has been closed and the menu discarded. */
    protected void onDispose() {
        // nothing to release by default
    }

    // ------------------------------------------------------------------ lifecycle

    /** Creates the inventory, renders it and shows it to the player. */
    public final void open() {
        if (disposed || player == null || !player.isOnline()) {
            return;
        }
        cancelTasks();
        int slots = normaliseSize(size());
        inventory = Bukkit.createInventory(holder, slots, title());
        holder.inventory(inventory);
        holder.menu(this);
        render(true);
        try {
            player.openInventory(inventory);
        } catch (Throwable error) {
            ctx.plugin().getLogger().warning("[Wiki/GUI] could not open " + menuKind()
                    + " for " + player.getName() + ": " + error);
            return;
        }
        ctx.gui().sounds().open().play(player);
    }

    /** Re-renders in place, used after a reload or an in-menu change. */
    public final void refresh() {
        if (disposed || inventory == null) {
            return;
        }
        render(false);
    }

    private void render(boolean animate) {
        buttons.clear();
        inventory.clear();
        build();
        layoutHeader();
        layoutNavigation();
        layoutFiller();
        apply(animate);
    }

    // ------------------------------------------------------------------- layout

    /** Registers a button. Slots outside the inventory are dropped with a debug note. */
    protected final void put(int slot, GuiButton button) {
        if (button == null || inventory == null) {
            return;
        }
        if (slot < 0 || slot >= inventory.getSize()) {
            ctx.plugin().getLogger().fine("[Wiki/GUI] " + menuKind() + " ignored a button at slot " + slot
                    + " outside a " + inventory.getSize() + " slot inventory");
            return;
        }
        buttons.put(slot, button);
    }

    /** The first free content slot at or after {@code from}, or -1 when full. */
    protected final int freeContentSlot(int from) {
        List<Integer> slots = ctx.gui().contentSlots();
        for (int i = from; i < slots.size(); i++) {
            if (!buttons.containsKey(slots.get(i))) {
                return slots.get(i);
            }
        }
        return -1;
    }

    private void layoutHeader() {
        int home = ctx.gui().slot(GuiSlot.HOME);
        if (home >= 0) {
            put(home, new GuiButton(ItemFactory.create(ctx.gui().item(GuiItemKey.HOME), Map.of()),
                    click -> ctx.navigation().openRoot(player, ctx.menus().home(player))));
        }
        int close = ctx.gui().slot(GuiSlot.CLOSE);
        if (close >= 0) {
            put(close, new GuiButton(ItemFactory.create(ctx.gui().item(GuiItemKey.CLOSE), Map.of()),
                    click -> closeInventory()));
        }

        List<Crumb> trail = new ArrayList<>(crumbs());
        Crumb current = currentCrumb();
        if (current != null) {
            trail.add(current);
        }
        if (trail.isEmpty()) {
            return;
        }
        int max = ctx.gui().breadcrumbMax();
        if (trail.size() > max) {
            trail = new ArrayList<>(trail.subList(trail.size() - max, trail.size()));
        }
        int slot = ctx.gui().breadcrumbStart();
        for (int i = 0; i < trail.size(); i++) {
            if (slot >= COLUMNS) {
                break;
            }
            Crumb crumb = trail.get(i);
            Runnable action = crumb.action();
            put(slot, action == null
                    ? GuiButton.display(ItemFactory.create(crumb.icon(), crumb.placeholders()))
                    : new GuiButton(ItemFactory.create(crumb.icon(), crumb.placeholders()),
                            click -> action.run()));
            slot++;
            if (i < trail.size() - 1 && slot < COLUMNS) {
                put(slot, GuiButton.display(ItemFactory.create(
                        ctx.gui().item(GuiItemKey.SEPARATOR), Map.of())));
                slot++;
            }
        }
    }

    private void layoutNavigation() {
        var gui = ctx.gui();
        if (session.hasHistory()) {
            int slot = gui.slot(GuiSlot.BACK);
            if (slot >= 0) {
                ItemSpec spec = gui.item(GuiItemKey.BACK);
                put(slot, new GuiButton(
                        ItemFactory.create(spec, Map.of("previous", backLabel())),
                        click -> {
                            gui.sounds().back().play(player);
                            if (!ctx.navigation().back(player)) {
                                ctx.navigation().openRoot(player, ctx.menus().home(player));
                            }
                        }));
            }
        }

        Pagination pagination = pagination();
        int page = page();
        if (pagination != null) {
            int previousSlot = gui.slot(GuiSlot.PREVIOUS);
            if (previousSlot >= 0 && pagination.hasPrevious(page)) {
                put(previousSlot, pageButton(GuiItemKey.PREVIOUS, page - 1, pagination,
                        () -> gui.sounds().page().play(player)));
            }
            int nextSlot = gui.slot(GuiSlot.NEXT);
            if (nextSlot >= 0 && pagination.hasNext(page)) {
                put(nextSlot, pageButton(GuiItemKey.NEXT, page + 1, pagination,
                        () -> gui.sounds().page().play(player)));
            }
            int infoSlot = gui.slot(GuiSlot.PAGE_INFO);
            if (infoSlot >= 0) {
                ItemSpec spec = pagination.pages() > 1
                        ? gui.item(GuiItemKey.PAGE_INFO)
                        : gui.item(GuiItemKey.PAGE_INFO_SINGLE);
                put(infoSlot, GuiButton.display(ItemFactory.create(spec, Map.of(
                        "page", Integer.toString(pagination.clamp(page)),
                        "pages", Integer.toString(pagination.pages()),
                        "shown", Integer.toString(pagination.shown(page)),
                        "total", Integer.toString(pagination.total())))));
            }
        }

        int searchSlot = gui.slot(GuiSlot.SEARCH);
        if (searchSlot >= 0 && player.hasPermission(ctx.permissions().search())) {
            put(searchSlot, new GuiButton(ItemFactory.create(gui.item(GuiItemKey.SEARCH), Map.of()),
                    click -> ctx.searchInput().prompt(player, this)));
        }
        int categoriesSlot = gui.slot(GuiSlot.CATEGORIES);
        if (categoriesSlot >= 0 && menuKind() != MenuKind.HOME) {
            put(categoriesSlot, new GuiButton(ItemFactory.create(gui.item(GuiItemKey.CATEGORIES), Map.of()),
                    click -> ctx.navigation().open(player, ctx.menus().home(player))));
        }
        int relatedSlot = gui.slot(GuiSlot.RELATED);
        GuiButton related = relatedButton();
        if (relatedSlot >= 0 && related != null) {
            put(relatedSlot, related);
        }
        int helpSlot = gui.slot(GuiSlot.HELP);
        if (helpSlot >= 0) {
            put(helpSlot, new GuiButton(ItemFactory.create(gui.item(GuiItemKey.HELP), Map.of()),
                    click -> ctx.navigation().open(player, ctx.menus().help(player))));
        }
    }

    private GuiButton pageButton(GuiItemKey key, int target, Pagination pagination, Runnable sound) {
        ItemSpec spec = ctx.gui().item(key);
        return new GuiButton(
                ItemFactory.create(spec, Map.of(
                        "page", Integer.toString(pagination.clamp(target)),
                        "pages", Integer.toString(pagination.pages()))),
                click -> {
                    sound.run();
                    goToPage(pagination.clamp(target));
                });
    }

    /** Plain title of the screen the Back button returns to. */
    private String backLabel() {
        WikiMenu previous = session.peekHistory();
        if (previous == null) {
            return "";
        }
        try {
            return shorten(Text.plain(previous.title()), 24);
        } catch (Throwable error) {
            return "";
        }
    }

    private void layoutFiller() {
        var gui = ctx.gui();
        if (!gui.fillerEnabled() || inventory == null) {
            return;
        }
        ItemSpec filler = gui.item(GuiItemKey.FILLER);
        List<Integer> contentSlots = gui.contentSlots();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (buttons.containsKey(slot)) {
                continue;
            }
            int row = slot / COLUMNS;
            int column = slot % COLUMNS;
            boolean fill = false;
            if (gui.fillerHeader() && row == gui.headerRow()) {
                fill = true;
            }
            if (gui.fillerNavigation() && row == gui.navRow()) {
                fill = true;
            }
            if (gui.fillerSides() && (column == 0 || column == COLUMNS - 1) && !contentSlots.contains(slot)) {
                fill = true;
            }
            if (fill) {
                buttons.put(slot, GuiButton.display(ItemFactory.create(filler, Map.of())));
            }
        }
    }

    // -------------------------------------------------------------------- render

    private void apply(boolean animate) {
        List<Integer> content = new ArrayList<>();
        List<Integer> frame = new ArrayList<>();
        for (Integer slot : buttons.keySet()) {
            if (ctx.gui().contentSlots().contains(slot)) {
                content.add(slot);
            } else {
                frame.add(slot);
            }
        }
        for (Integer slot : frame) {
            inventory.setItem(slot, buttons.get(slot).icon());
        }
        var animation = ctx.gui().animation();
        if (!animate || !animation.pageTransition() || animation.steps() <= 1 || content.size() <= 1) {
            for (Integer slot : content) {
                inventory.setItem(slot, buttons.get(slot).icon());
            }
            return;
        }
        int steps = Math.min(animation.steps(), content.size());
        int perStep = (content.size() + steps - 1) / steps;
        for (int step = 0; step < steps; step++) {
            int from = step * perStep;
            int to = Math.min(content.size(), from + perStep);
            if (from >= to) {
                break;
            }
            List<Integer> chunk = List.copyOf(content.subList(from, to));
            if (step == 0) {
                applyChunk(chunk);
                continue;
            }
            long delay = step * animation.intervalTicks();
            tasks.add(PlatformScheduler.runEntityLater(player, () -> applyChunk(chunk), delay));
        }
    }

    private void applyChunk(List<Integer> slots) {
        if (disposed || inventory == null) {
            return;
        }
        for (Integer slot : slots) {
            GuiButton button = buttons.get(slot);
            if (button != null) {
                inventory.setItem(slot, button.icon());
            }
        }
    }

    // --------------------------------------------------------------------- input

    /** Routes a click. Unknown slots do nothing, because the listener already cancelled them. */
    public final void click(int slot, ClickInfo info) {
        if (disposed) {
            return;
        }
        GuiButton button = buttons.get(slot);
        if (button == null || button.action() == null) {
            return;
        }
        try {
            button.action().run(info);
        } catch (Throwable error) {
            ctx.plugin().getLogger().warning("[Wiki/GUI] a click in " + menuKind()
                    + " failed: " + error);
            ctx.gui().sounds().error().play(player);
        }
    }

    /** Closes the player's inventory without touching anyone else's. */
    public final void closeInventory() {
        ctx.gui().sounds().close().play(player);
        if (player.isOnline() && player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory() == inventory) {
            player.closeInventory();
        }
    }

    /** Releases animation tasks and detaches from the holder. */
    public final void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        cancelTasks();
        buttons.clear();
        holder.menu(null);
        holder.inventory(null);
        inventory = null;
        onDispose();
    }

    private void cancelTasks() {
        for (PlatformScheduler.ScheduledTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    public final boolean disposed() {
        return disposed;
    }

    public final Player player() {
        return player;
    }

    public final Inventory inventory() {
        return inventory;
    }

    /** Language the player has chosen, for message lookups. */
    protected final String language() {
        return ctx.messages().resolve(session.language());
    }

    /** Normalises a configured inventory size to a legal chest size. */
    private static int normaliseSize(int size) {
        int clamped = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        int rows = Math.max(1, clamped / COLUMNS);
        return rows * COLUMNS;
    }

    /** Convenience for subclasses building placeholder maps. */
    protected static Map<String, String> placeholders(String... keyValuePairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }

    /** Truncates a value for use in a title or single lore line. */
    protected static String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "\u2026";
    }

    /** Strips MiniMessage tags so a value can be embedded safely inside another tag. */
    protected static String plain(String value) {
        return value == null ? "" : Text.plain(Text.mini(value));
    }
}
