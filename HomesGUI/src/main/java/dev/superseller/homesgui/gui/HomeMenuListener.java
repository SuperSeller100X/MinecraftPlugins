package dev.superseller.homesgui.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import dev.superseller.homesgui.HomesGUIPlugin;
import dev.superseller.homesgui.config.HomesSettings;
import dev.superseller.homesgui.service.HomeService;
import dev.superseller.homesgui.util.SoundPlayer;

/** Cancels all item movement in the menu and dispatches only explicit GUI actions. */
public final class HomeMenuListener implements Listener {

    private final HomesGUIPlugin plugin;
    private final HomeService service;
    private final HomeMenu menu;

    public HomeMenuListener(HomesGUIPlugin plugin, HomeService service, HomeMenu menu) {
        this.plugin = plugin;
        this.service = service;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof HomeMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer().equals(player.getUniqueId())) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        ClickType click = event.getClick();
        if (click == ClickType.DOUBLE_CLICK || click == ClickType.UNKNOWN
                || click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND
                || click == ClickType.DROP || click == ClickType.CONTROL_DROP
                || click == ClickType.MIDDLE) {
            return;
        }

        HomesSettings.Gui gui = plugin.settings().gui();
        int barStart = top.getSize() - 9;
        if (rawSlot >= barStart) {
            handleControl(player, holder, rawSlot - barStart, gui);
            return;
        }
        if (rawSlot >= gui.pageSize()) {
            return;
        }
        int slot = holder.page() * gui.pageSize() + rawSlot + 1;
        if (slot > menu.displaySlots(player)) {
            return;
        }
        handleHome(player, holder, slot, click);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof HomeMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleControl(Player player, HomeMenuHolder holder, int position, HomesSettings.Gui gui) {
        SoundPlayer.play(plugin, player, "gui-click");
        if (position == gui.previous().position()) {
            if (holder.page() > 0) {
                menu.open(player, holder.page() - 1);
            }
        } else if (position == gui.next().position()) {
            int pages = pageCount(menu.displaySlots(player), gui.pageSize());
            if (holder.page() + 1 < pages) {
                menu.open(player, holder.page() + 1);
            }
        } else if (position == gui.info().position()) {
            if (player.hasPermission("homesgui.info")) {
                service.sendInfo(player);
            } else {
                plugin.messages().send(player, "no-permission");
            }
        } else if (position == gui.buy().position()) {
            if (player.hasPermission("homesgui.buy")) {
                service.purchase(player, 1);
                menu.open(player, holder.page());
            } else {
                plugin.messages().send(player, "no-permission");
            }
        } else if (position == gui.close().position()) {
            player.closeInventory();
        }
    }

    private void handleHome(Player player, HomeMenuHolder holder, int slot, ClickType click) {
        var data = service.profile(player.getUniqueId());
        boolean exists = data.hasHome(slot);
        if (click.isShiftClick()) {
            if (!exists) {
                return;
            }
            if (!player.hasPermission("homesgui.delete")) {
                plugin.messages().send(player, "no-permission");
                return;
            }
            if (service.deleteHome(player, slot)) {
                menu.open(player, holder.page());
            }
            return;
        }
        if (click.isRightClick() || !exists) {
            if (!player.hasPermission("homesgui.set")) {
                plugin.messages().send(player, "no-permission");
                return;
            }
            if (service.setHome(player, slot)) {
                menu.open(player, holder.page());
            }
            return;
        }
        if (click.isLeftClick()) {
            if (!player.hasPermission("homesgui.use")) {
                plugin.messages().send(player, "no-permission");
                return;
            }
            player.closeInventory();
            service.teleportHome(player, slot);
        }
    }

    private static int pageCount(int slots, int pageSize) {
        return Math.max(1, (int) (((long) slots + pageSize - 1L) / pageSize));
    }
}
