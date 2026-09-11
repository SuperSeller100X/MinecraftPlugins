package dev.superseller.minecraftwiki.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import dev.superseller.minecraftwiki.gui.MenuHolder;
import dev.superseller.minecraftwiki.gui.WikiMenu;
import dev.superseller.minecraftwiki.scheduler.PlatformScheduler;
import dev.superseller.minecraftwiki.search.SearchService;
import dev.superseller.minecraftwiki.session.NavigationManager;

/**
 * The single listener that owns every interaction with a wiki inventory.
 *
 * <p>Security model: a wiki inventory is read-only, so <em>every</em> click in a view whose top
 * inventory is ours is cancelled before anything else runs - left, right, shift, double click,
 * number key, middle click, drop and offhand swap alike. Cancelling unconditionally rather than
 * per click type is what closes the shift-click, double-click and number-key item movement
 * exploits in one place instead of relying on a list that a future click type could slip past.</p>
 *
 * <p>Drags are cancelled when any dragged slot belongs to the wiki. Offhand swapping is
 * cancelled while a wiki inventory is open. Inventories that are not ours are never touched.</p>
 */
public final class WikiGuiListener implements Listener {

    private final NavigationManager navigation;
    private final SearchService search;

    public WikiGuiListener(NavigationManager navigation, SearchService search) {
        this.navigation = navigation;
        this.search = search;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        MenuHolder holder = holder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        // Cancel first, always: nothing may ever leave or enter a wiki inventory.
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            // A click in the player's own inventory or outside the window: nothing to do.
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        WikiMenu menu = holder.menu();
        if (menu == null || menu.disposed()) {
            return;
        }
        menu.click(rawSlot, dev.superseller.minecraftwiki.gui.ClickInfo.of(event.getClick()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        MenuHolder holder = holder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot != null && rawSlot < topSize) {
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (holder(player.getOpenInventory().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        MenuHolder holder = holder(event.getInventory());
        if (holder == null) {
            return;
        }
        WikiMenu menu = holder.menu();
        if (!(event.getPlayer() instanceof Player player)) {
            if (menu != null) {
                menu.dispose();
            }
            return;
        }
        navigation.closed(player);
        if (menu != null) {
            // Disposal is deferred so it can never run while the close event is still iterating
            // the inventory, and so a reopen in the same tick still sees a consistent state.
            PlatformScheduler.runEntity(player, menu::dispose);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        navigation.forget(event.getPlayer().getUniqueId());
        search.forget(event.getPlayer().getUniqueId());
    }

    /** The wiki holder of an inventory, or null when the inventory is not ours. */
    private static MenuHolder holder(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof MenuHolder menuHolder ? menuHolder : null;
    }
}
