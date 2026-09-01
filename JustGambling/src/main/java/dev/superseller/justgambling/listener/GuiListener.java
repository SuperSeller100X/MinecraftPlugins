package dev.superseller.justgambling.listener;

import dev.superseller.justgambling.gui.GamblingGui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Routes and locks JustGambling inventory menus. */
public final class GuiListener implements Listener {
    private final GamblingGui gui;

    public GuiListener(GamblingGui gui) {
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        gui.onClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof dev.superseller.justgambling.gui.GamblingHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        gui.onClose(event);
    }
}
