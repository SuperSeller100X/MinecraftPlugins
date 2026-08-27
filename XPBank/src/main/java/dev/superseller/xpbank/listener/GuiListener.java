package dev.superseller.xpbank.listener;

import dev.superseller.xpbank.XPBankPlugin;
import dev.superseller.xpbank.bank.BankService;
import dev.superseller.xpbank.bank.TransactionResult;
import dev.superseller.xpbank.config.Messages;
import dev.superseller.xpbank.gui.BankGui;
import dev.superseller.xpbank.gui.BankHolder;
import dev.superseller.xpbank.util.Numbers;
import dev.superseller.xpbank.util.Sounds;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Handles clicks inside the XP Bank GUI. All item movement is cancelled (the
 * menu is display-only); clicks on action buttons run a deposit or withdraw on
 * the player's own thread and repaint the menu.
 */
public final class GuiListener implements Listener {

    private final XPBankPlugin plugin;
    private final BankService bank;
    private final BankGui gui;
    private final Messages messages;

    public GuiListener(XPBankPlugin plugin, BankService bank, BankGui gui, Messages messages) {
        this.plugin = plugin;
        this.bank = bank;
        this.gui = gui;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof BankHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof BankHolder)) {
            return;
        }

        int slot = event.getSlot();
        Long deposit = gui.depositAmountFor(slot);
        Long withdraw = gui.withdrawAmountFor(slot);
        if (deposit == null && withdraw == null) {
            return;
        }

        Sounds.play(plugin.bankConfig(), player, "gui-click");
        if (deposit != null) {
            handle(player, top, bank.deposit(player, deposit), "deposit");
        } else {
            handle(player, top, bank.withdraw(player, withdraw), "withdraw");
        }
    }

    private void handle(Player player, Inventory top, TransactionResult result, String type) {
        if (result.ok()) {
            Sounds.play(plugin.bankConfig(), player, type);
            messages.send(player, type + "-success", Map.of(
                    "amount", Numbers.grouped(result.amount()),
                    "amount_short", Numbers.compact(result.amount()),
                    "onhand", Numbers.grouped(result.onHand()),
                    "banked", Numbers.grouped(result.banked())));
        } else {
            Sounds.play(plugin.bankConfig(), player, "error");
            messages.send(player, "error." + result.status().name().toLowerCase(java.util.Locale.US),
                    Map.of("amount", Numbers.grouped(result.amount())));
        }
        gui.render(player, top);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BankHolder) {
            event.setCancelled(true);
        }
    }
}
