package dev.superseller.gifty.listener;

import dev.superseller.gifty.GuiSessionManager;
import dev.superseller.gifty.config.GiftyConfig;
import dev.superseller.gifty.gui.GiftyHolder;
import dev.superseller.gifty.input.ChatInputManager;
import dev.superseller.gifty.util.Sounds;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Central GUI interaction handling for all Gifty inventories.
 */
public final class GuiListener implements Listener {

    private static final int SLOT_MONEY = 2;
    private static final int SLOT_MESSAGE = 4;
    private static final int SLOT_CANCEL = 6;
    private static final int SLOT_SEND = 8;

    private static final int INBOX_PREV = 45;
    private static final int INBOX_PAGE_INFO = 47;
    private static final int INBOX_MONEY_ALL = 49;
    private static final int INBOX_NEXT = 51;
    private static final int INBOX_CLOSE = 53;

    private final GuiSessionManager sessions;
    private final ChatInputManager chatInput;
    private final GiftyConfig config;

    public GuiListener(GuiSessionManager sessions, ChatInputManager chatInput, GiftyConfig config) {
        this.sessions = sessions;
        this.chatInput = chatInput;
        this.config = config;
    }

    private GiftyHolder holderOf(Inventory inv) {
        if (inv == null || inv.getHolder() == null) {
            return null;
        }
        return inv.getHolder() instanceof GiftyHolder ? (GiftyHolder) inv.getHolder() : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        GiftyHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        switch (holder.kind) {
            case COMPOSE:
                onClickCompose(event, holder, player);
                break;
            case CONFIRM:
                onClickConfirm(event, holder, player);
                break;
            case INBOX:
                onClickInbox(event, holder, player);
                break;
            default:
                break;
        }
    }

    private void onClickCompose(InventoryClickEvent event, GiftyHolder holder, Player player) {
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() == null) {
            return;
        }
        if (event.getClickedInventory() == top) {
            // allow free editing inside the item-slot area only
            if (event.getSlot() >= config.sendSlots()) {
                event.setCancelled(true);
            }
        }
    }

    private void onClickConfirm(InventoryClickEvent event, GiftyHolder holder, Player player) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot == SLOT_MONEY) {
            if (config.playSounds()) {
                Sounds.click(player);
            }
            holder.done = true; // closing for the prompt must not cancel the draft
            chatInput.prompt(player, ChatInputManager.Kind.MONEY, holder.targetId);
        } else if (slot == SLOT_MESSAGE) {
            if (config.playSounds()) {
                Sounds.click(player);
            }
            holder.done = true; // closing for the prompt must not cancel the draft
            chatInput.prompt(player, ChatInputManager.Kind.MESSAGE, holder.targetId);
        } else if (slot == SLOT_CANCEL) {
            if (config.playSounds()) {
                Sounds.click(player);
            }
            holder.done = true;
            player.closeInventory();
            sessions.abandonConfirm(player);
        } else if (slot == SLOT_SEND) {
            sessions.sendGift(player);
        }
    }

    private void onClickInbox(InventoryClickEvent event, GiftyHolder holder, Player player) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot >= 0 && slot < 45) {
            long id = holder.deliveryIds.length > slot ? holder.deliveryIds[slot] : 0;
            if (id == 0) {
                return;
            }
            if (holder.readOnly) {
                return;
            }
            if (config.playSounds()) {
                Sounds.click(player);
            }
            if (event.isShiftClick()) {
                sessions.claimAll(player, id);
            } else if (event.isLeftClick()) {
                sessions.claimItems(player, id);
            } else if (event.isRightClick()) {
                sessions.claimMoney(player, id);
            }
            return;
        }
        if (slot == INBOX_PREV) {
            if (holder.readOnly) {
                sessions.openInboxForAdmin(player, holder.targetId, holder.page - 1);
            } else {
                holder.done = true;
                player.closeInventory();
                final int target = holder.page - 1;
                dev.superseller.gifty.scheduler.PlatformScheduler.runEntitySync(player,
                        () -> sessions.openInbox(player, target, false));
            }
        } else if (slot == INBOX_NEXT) {
            if (holder.readOnly) {
                sessions.openInboxForAdmin(player, holder.targetId, holder.page + 1);
            } else {
                holder.done = true;
                player.closeInventory();
                final int target = holder.page + 1;
                dev.superseller.gifty.scheduler.PlatformScheduler.runEntitySync(player,
                        () -> sessions.openInbox(player, target, false));
            }
        } else if (slot == INBOX_MONEY_ALL && !holder.readOnly) {
            sessions.claimAllMoney(player);
        } else if (slot == INBOX_CLOSE) {
            holder.done = true;
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        GiftyHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        int size = event.getView().getTopInventory().getSize();
        switch (holder.kind) {
            case COMPOSE:
                for (int raw : event.getRawSlots()) {
                    if (raw < size && raw >= config.sendSlots()) {
                        event.setCancelled(true);
                        return;
                    }
                }
                break;
            case CONFIRM:
            case INBOX:
            default:
                for (int raw : event.getRawSlots()) {
                    if (raw < size) {
                        event.setCancelled(true);
                        return;
                    }
                }
                break;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        GiftyHolder holder = holderOf(event.getInventory());
        if (holder == null) {
            return;
        }
        // only react if this holder is the player's current session
        GiftyHolder current = sessions.currentHolder(player);
        if (current != holder || holder.done) {
            return;
        }
        switch (holder.kind) {
            case COMPOSE:
                sessions.onComposeClose(player);
                break;
            case CONFIRM:
                sessions.onConfirmClose(player);
                break;
            case INBOX:
                sessions.onInboxClose(player);
                break;
            default:
                break;
        }
    }
}
