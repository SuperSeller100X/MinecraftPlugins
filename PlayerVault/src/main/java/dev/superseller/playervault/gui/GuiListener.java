package dev.superseller.playervault.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import dev.superseller.playervault.config.Messages;
import dev.superseller.playervault.config.Settings;
import dev.superseller.playervault.model.VaultData;
import dev.superseller.playervault.service.VaultService;

/**
 * Click, drag and close handling for the vault GUI.
 *
 * <p>Design rule: only rows the player owns are ever rendered, so a plain storage
 * click can stay uncancelled and behave exactly like a normal chest. Everything in
 * the utility bar is cancelled, because those slots hold buttons that must never be
 * taken, moved or duplicated.
 *
 * <p>{@link ClickType#DOUBLE_CLICK} is cancelled while a vault is open. Vanilla's
 * "collect to cursor" scans the <em>whole</em> top inventory, which would otherwise
 * let a player pull a GUI button into their inventory.
 */
public final class GuiListener implements Listener {

    private final Messages messages;
    private final VaultService service;
    private final VaultGui gui;
    private final Supplier<Settings> settings;

    public GuiListener(Messages messages, VaultService service, VaultGui gui, Supplier<Settings> settings) {
        this.messages = messages;
        this.service = service;
        this.gui = gui;
        this.settings = settings;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        VaultHolder holder = VaultGui.holder(top);
        if (holder == null) {
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.UNKNOWN) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            // Clicked outside the window; let vanilla drop the cursor item.
            return;
        }
        GuiLayout layout = holder.layout();
        int page = holder.page();
        if (!layout.isUtilitySlot(page, rawSlot)) {
            // Storage slot in the vault, or anywhere in the player's own inventory.
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            handleButton(player, holder, layout.buttonIndex(page, rawSlot));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        VaultHolder holder = VaultGui.holder(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        GuiLayout layout = holder.layout();
        int page = holder.page();
        for (int rawSlot : event.getRawSlots()) {
            if (layout.isUtilitySlot(page, rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        VaultHolder holder = VaultGui.holder(event.getInventory());
        if (holder == null) {
            return;
        }
        VaultData data = collect(holder, event.getInventory());
        service.page(holder.owner(), holder.page());
        service.saveAsync(data);
    }

    /**
     * Copies the visible page of an open vault back into the cached data.
     *
     * <p>Addressed by {@link VaultHolder#owner()} rather than by the viewer, so an
     * admin inspecting somebody else's vault writes back to the right player.
     *
     * <p>Also used during shutdown, when an asynchronous save would no longer run.
     */
    public VaultData collect(VaultHolder holder, Inventory inventory) {
        VaultData data = service.vaultById(holder.owner());
        GuiLayout layout = holder.layout();
        int page = holder.page();
        for (int slot = 0; slot < layout.storageSlots(page); slot++) {
            data.item(layout.vaultSlot(page, slot), inventory.getItem(slot));
        }
        return data;
    }

    // ── buttons ──────────────────────────────────────────────────────────────

    private void handleButton(Player player, VaultHolder holder, int button) {
        if (button < 0) {
            return;
        }
        Settings.Buttons buttons = settings.get().gui().buttons();
        int pages = holder.layout().pageCount();
        if (button == buttons.info().slot()) {
            playClick(player);
            showInfo(player, holder);
        } else if (button == buttons.previous().slot() && pages > 1 && holder.page() > 0) {
            playClick(player);
            reopen(player, holder, holder.page() - 1);
        } else if (button == buttons.next().slot() && pages > 1 && holder.page() < pages - 1) {
            playClick(player);
            reopen(player, holder, holder.page() + 1);
        } else if (holder.adminView()) {
            // Money and inventory buttons are owner-only; an admin only looks.
            return;
        } else if (button == buttons.upgrade().slot()) {
            playClick(player);
            attemptUpgrade(player);
        } else if (button == buttons.sort().slot()) {
            playClick(player);
            sort(player);
        } else if (button == buttons.deposit().slot()) {
            playClick(player);
            depositAll(player);
        } else if (button == buttons.withdraw().slot()) {
            playClick(player);
            withdrawAll(player);
        }
    }

    private void reopen(Player viewer, VaultHolder holder, int page) {
        if (holder.adminView()) {
            gui.openAdmin(viewer, holder.owner(), page);
            return;
        }
        gui.open(viewer, page);
    }

    private void attemptUpgrade(Player player) {
        if (!player.hasPermission("playervault.upgrade")) {
            messages.send(player, "no-permission");
            return;
        }
        VaultData data = service.vaultById(player.getUniqueId());
        if (gui.isMaxed(player, data)) {
            messages.send(player, "upgrade.already-max",
                    "%rows%", String.valueOf(settings.get().vault().maxRows()));
            return;
        }
        service.upgrade(player, 1, true);
    }

    private void sort(Player player) {
        if (!player.hasPermission("playervault.sort")) {
            messages.send(player, "no-permission");
            return;
        }
        VaultData data = service.vaultById(player.getUniqueId());
        int used = data.sort();
        service.saveAsync(data);
        gui.repaint(player);
        messages.send(player, "sort.done",
                "%used%", String.valueOf(used),
                "%slots%", String.valueOf(data.capacity()));
    }

    /** Moves the player's whole inventory into the vault; the rest stays behind. */
    private void depositAll(Player player) {
        VaultData data = service.vaultById(player.getUniqueId());
        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            ItemStack rest = data.add(stack);
            if (rest != null) {
                leftover.add(rest);
            }
        }
        ItemStack[] rebuilt = new ItemStack[contents.length];
        for (int i = 0; i < leftover.size() && i < rebuilt.length; i++) {
            rebuilt[i] = leftover.get(i);
        }
        inventory.setStorageContents(rebuilt);
        service.saveAsync(data);
        gui.repaint(player);
    }

    /** Moves the vault into the player's inventory; whatever does not fit stays. */
    private void withdrawAll(Player player) {
        VaultData data = service.vaultById(player.getUniqueId());
        ItemStack[] stored = data.contents();
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack stack : stored) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                remaining.addAll(overflow.values());
            }
        }
        ItemStack[] rebuilt = new ItemStack[stored.length];
        for (int i = 0; i < remaining.size() && i < rebuilt.length; i++) {
            rebuilt[i] = remaining.get(i);
        }
        data.contents(rebuilt);
        service.saveAsync(data);
        gui.repaint(player);
    }

    private void showInfo(Player player, VaultHolder holder) {
        VaultData data = service.vaultById(holder.owner());
        int pages = new GuiLayout(settings.get().gui().pageRows(), data.rows()).pageCount();
        messages.sendPlain(player, "info.header");
        messages.sendPlain(player, "info.rows",
                "%rows%", String.valueOf(data.rows()),
                "%slots%", String.valueOf(data.capacity()));
        messages.sendPlain(player, "info.used",
                "%used%", String.valueOf(data.usedSlots()),
                "%slots%", String.valueOf(data.capacity()),
                "%free%", String.valueOf(data.capacity() - data.usedSlots()));
        messages.sendPlain(player, "info.pages", "%pages%", String.valueOf(pages));
        messages.sendPlain(player, "info.next", "%price%", service.money(service.nextPrice(data)));
        messages.sendPlain(player, "info.spent", "%spent%", service.money(data.totalSpent()));
        messages.sendPlain(player, "info.economy",
                "%provider%", service.economy().isEnabled() ? service.economy().providerName() : "none");
    }

    private void playClick(Player player) {
        Settings.Gui guiConfig = settings.get().gui();
        if (!guiConfig.sounds()) {
            return;
        }
        String sound = guiConfig.sound();
        if (sound != null && !sound.isBlank()) {
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        }
    }
}
