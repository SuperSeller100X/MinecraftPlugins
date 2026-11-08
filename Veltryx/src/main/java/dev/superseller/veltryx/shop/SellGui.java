package dev.superseller.veltryx.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.SellNotices;
import dev.superseller.veltryx.util.Txt;

/** A deposit tray: the gray panes are placeholders, not sellable items. */
public final class SellGui implements Listener {
    private static final int INPUTS = 45, CONFIRM = 49;
    public static final class Holder implements InventoryHolder {
        final UUID owner;
        final ItemStack[] deposited = new ItemStack[INPUTS];
        boolean returned;
        Inventory inventory;
        Holder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private final Map<UUID, Holder> open = new ConcurrentHashMap<>();
    private final ShopService shop;
    private final Messages messages;
    public SellGui(ShopService shop, Messages messages) { this.shop = shop; this.messages = messages; }

    public void open(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            if (!player.hasPermission("veltryx.sell")) { messages.send(player, "general.no-permission"); return; }
            Holder h = new Holder(player.getUniqueId());
            Inventory inv = Bukkit.createInventory(h, 54, Txt.legacy("§8Sell items")); h.inventory = inv;
            for (int i = 0; i < INPUTS; i++) renderSlot(h, i);
            for (int i = INPUTS; i < 54; i++) inv.setItem(i, ShopItems.icon("GRAY_STAINED_GLASS_PANE", "§8 ", List.of()));
            inv.setItem(CONFIRM, ShopItems.icon("LIME_STAINED_GLASS_PANE", "§aSell deposited items",
                    List.of("§7Put items in the gray slots above.", "§7Unsellable items will stay here.", "§eClick to sell")));
            player.openInventory(inv);
            open.put(player.getUniqueId(), h);
        });
    }

    private static boolean empty(ItemStack s) { return s == null || s.getType() == Material.AIR || s.getAmount() <= 0; }
    private static ItemStack placeholder() {
        return ShopItems.icon("GRAY_STAINED_GLASS_PANE", "§7Place items here", List.of("§8Click or shift-click to add items."));
    }
    private static void renderSlot(Holder h, int slot) {
        ItemStack s = h.deposited[slot];
        if (empty(s)) { h.deposited[slot] = null; h.inventory.setItem(slot, placeholder()); }
        else h.inventory.setItem(slot, s);
    }
    private static ItemStack copyAmount(ItemStack item, int amount) {
        ItemStack copy = item.clone(); copy.setAmount(amount); return copy;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h) || !(event.getWhoClicked() instanceof Player p)) return;
        if (!h.owner.equals(p.getUniqueId()) || h.returned) { event.setCancelled(true); return; }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true); return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;
        if (clicked.getHolder() != h) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack source = clicked.getItem(event.getSlot());
                if (empty(source)) return;
                int remaining = insert(h, source);
                if (remaining == 0) clicked.setItem(event.getSlot(), null);
                else { source.setAmount(remaining); clicked.setItem(event.getSlot(), source); }
            }
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (slot == CONFIRM) { sell(p, h); return; }
        if (slot < 0 || slot >= INPUTS) return;
        ItemStack current = h.deposited[slot];
        if (event.isShiftClick()) {
            if (!empty(current)) {
                Map<Integer, ItemStack> remaining = p.getInventory().addItem(current.clone());
                h.deposited[slot] = remaining.isEmpty() ? null : remaining.values().iterator().next();
                renderSlot(h, slot);
            }
            return;
        }
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
        ItemStack cursor = event.getCursor();
        if (empty(cursor)) {
            if (empty(current)) return;
            int take = click == ClickType.RIGHT ? (current.getAmount() + 1) / 2 : current.getAmount();
            p.setItemOnCursor(copyAmount(current, take));
            current.setAmount(current.getAmount() - take);
        } else if (empty(current)) {
            int put = click == ClickType.RIGHT ? 1 : cursor.getAmount();
            h.deposited[slot] = copyAmount(cursor, put);
            cursor.setAmount(cursor.getAmount() - put);
            p.setItemOnCursor(empty(cursor) ? null : cursor);
        } else if (current.isSimilar(cursor)) {
            int room = Math.max(0, current.getMaxStackSize() - current.getAmount());
            int put = Math.min(room, click == ClickType.RIGHT ? 1 : cursor.getAmount());
            current.setAmount(current.getAmount() + put);
            cursor.setAmount(cursor.getAmount() - put);
            p.setItemOnCursor(empty(cursor) ? null : cursor);
        } else if (click == ClickType.LEFT) {
            h.deposited[slot] = cursor.clone(); p.setItemOnCursor(current);
        }
        renderSlot(h, slot);
    }

    /** Shift-click from the player inventory into the tray without moving placeholders. */
    private static int insert(Holder h, ItemStack source) {
        int left = source.getAmount();
        for (int pass = 0; pass < 2 && left > 0; pass++) for (int i = 0; i < INPUTS && left > 0; i++) {
            ItemStack current = h.deposited[i];
            if (pass == 0 && !empty(current) && current.isSimilar(source)) {
                int put = Math.min(left, Math.max(0, current.getMaxStackSize() - current.getAmount()));
                current.setAmount(current.getAmount() + put); left -= put; renderSlot(h, i);
            } else if (pass == 1 && empty(current)) {
                int put = Math.min(left, source.getMaxStackSize());
                h.deposited[i] = copyAmount(source, put); left -= put; renderSlot(h, i);
            }
        }
        return left;
    }
    private void sell(Player player, Holder h) {
        if (!player.hasPermission("veltryx.sell")) { messages.send(player, "general.no-permission"); return; }
        List<ItemStack> deposited = new ArrayList<>();
        for (ItemStack stack : h.deposited) if (!empty(stack)) deposited.add(stack);
        String singleItem = deposited.size() == 1 ? PriceTable.displayName(deposited.get(0).getType().name()) : "";
        ShopService.SellResult result = shop.sellDeposited(player, deposited);
        if (result.ok() && deposited.size() == 1) {
            SellNotices.show(player, messages, "shop.sold", Map.of("count", Integer.toString(result.sold()),
                    "item", singleItem,
                    "balance", SellNotices.compact(shop.balance(player))), result.earnedCents());
        } else if (result.ok()) SellNotices.show(player, messages, "shop.sold-all",
                Map.of("balance", SellNotices.compact(shop.balance(player))), result.earnedCents());
        else if (deposited.isEmpty()) messages.send(player, "shop.nothing");
        else messages.send(player, "shop.no-sell");
        for (int i = 0; i < INPUTS; i++) renderSlot(h, i);
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        for (int slot : event.getRawSlots()) if (slot < 54) { event.setCancelled(true); return; }
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder h && event.getPlayer() instanceof Player p
                && h.owner.equals(p.getUniqueId())) returnItems(p, h);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Holder h = open.get(event.getPlayer().getUniqueId());
        if (h != null) returnItems(event.getPlayer(), h);
    }
    private void returnItems(Player player, Holder h) {
        if (h.returned) return;
        h.returned = true; open.remove(h.owner, h);
        for (int i = 0; i < INPUTS; i++) {
            ItemStack stack = h.deposited[i]; h.deposited[i] = null;
            if (empty(stack)) continue;
            for (ItemStack left : player.getInventory().addItem(stack).values()) {
                var drop = player.getWorld().dropItemNaturally(player.getLocation(), left);
                if (drop != null) { drop.setOwner(h.owner); drop.setUnlimitedLifetime(true); }
            }
        }
    }
}
