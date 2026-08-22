package dev.superseller.subscriptions.listener;

import dev.superseller.subscriptions.gui.GuiManager;
import dev.superseller.subscriptions.gui.MenuHolder;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {

    private final GuiManager gui;

    public GuiListener(GuiManager gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            gui.handleClick(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (holder.menu == MenuHolder.Menu.STOCK) {
            return;
        }
        event.setCancelled(true);
    }
}
