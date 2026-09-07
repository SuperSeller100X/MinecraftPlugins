package dev.superseller.playerbank.listener;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.gui.Amount;
import dev.superseller.playerbank.gui.BankHolder;
import dev.superseller.playerbank.gui.BankMenu;
import dev.superseller.playerbank.gui.ChestLayout;
import dev.superseller.playerbank.gui.ChestMenu;
import dev.superseller.playerbank.gui.MenuStyle;
import dev.superseller.playerbank.util.Sounds;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Handles clicks inside the chest-styled bank menu. All item movement is
 * cancelled (the menu is display-only); clicks on buttons run a deposit or
 * withdraw through the shared {@code Transactor} and repaint the menu.
 */
public final class ChestMenuListener implements Listener {

    private final PlayerBankPlugin plugin;
    private final BankMenu menu;
    private final ChestMenu chest;

    public ChestMenuListener(PlayerBankPlugin plugin, BankMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
        this.chest = menu.chest();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof BankHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof BankHolder)) {
            return;
        }
        int slot = event.getSlot();
        int size = top.getSize();
        switch (holder.view()) {
            case MAIN -> onMainClick(player, top, slot, size);
            case LOGS -> onLogsClick(player, holder.page(), slot, size);
            case INTEREST -> onInterestClick(player, slot, size);
        }
    }

    private void onMainClick(Player player, Inventory top, int slot, int size) {
        Amount deposit = chest.depositAt(slot);
        if (deposit != null) {
            Sounds.play(plugin.bankConfig(), player, "click");
            menu.feedback(player, plugin.transactor().deposit(player, deposit), true);
            chest.renderMain(player, top);
            return;
        }
        Amount withdraw = chest.withdrawAt(slot);
        if (withdraw != null) {
            Sounds.play(plugin.bankConfig(), player, "click");
            menu.feedback(player, plugin.transactor().withdraw(player, withdraw), false);
            chest.renderMain(player, top);
            return;
        }
        if (slot == ChestLayout.slotInterest(size)) {
            Sounds.play(plugin.bankConfig(), player, "click");
            chest.openInterest(player);
        } else if (slot == ChestLayout.slotLogs(size)) {
            Sounds.play(plugin.bankConfig(), player, "click");
            chest.openLogs(player, 1);
        } else if (slot == ChestLayout.slotStyle(size)) {
            toggleStyle(player);
        } else if (slot == ChestLayout.slotClose(size)) {
            player.closeInventory();
        }
    }

    private void onLogsClick(Player player, int current, int slot, int size) {
        if (slot == ChestLayout.slotPrevPage(size)) {
            Sounds.play(plugin.bankConfig(), player, "click");
            chest.openLogs(player, current - 1);
        } else if (slot == ChestLayout.slotNextPage(size)) {
            Sounds.play(plugin.bankConfig(), player, "click");
            chest.openLogs(player, current + 1);
        } else if (slot == ChestLayout.slotBack(size)) {
            Sounds.play(plugin.bankConfig(), player, "click");
            chest.openMain(player);
        }
    }

    private void onInterestClick(Player player, int slot, int size) {
        if (slot == ChestLayout.slotBack(size)) {
            Sounds.play(plugin.bankConfig(), player, "click");
            chest.openMain(player);
        }
    }

    private void toggleStyle(Player player) {
        MenuStyle current = menu.resolve(player);
        MenuStyle target = current == MenuStyle.CHEST ? MenuStyle.DIALOG : MenuStyle.CHEST;
        Sounds.play(plugin.bankConfig(), player, "click");
        if (!menu.setPlayerStyle(player, target)) {
            plugin.messages().send(player, "gui-style-locked",
                    Map.of("style", current.label()));
            return;
        }
        plugin.messages().send(player, "gui-style-set",
                Map.of("style", target.label()));
        menu.open(player, target);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BankHolder) {
            event.setCancelled(true);
        }
    }
}
