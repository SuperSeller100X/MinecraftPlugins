package dev.superseller.veltryx.shop;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.SellNotices;
import dev.superseller.veltryx.util.Txt;

/** Gray six-row SellGUI with the nether-star sell-all control. */
public final class SellGui implements Listener {
    public static final class Holder implements InventoryHolder {
        final Map<Integer, String> slots = new LinkedHashMap<>();
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private final ShopService shop;
    private final Messages messages;
    public SellGui(ShopService shop, Messages messages) { this.shop = shop; this.messages = messages; }
    public void open(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            Holder h = new Holder();
            Inventory inv = Bukkit.createInventory(h, 54, Txt.legacy("§8SellGUI")); h.inventory = inv;
            ShopItems.fill(inv);
            Map<String, Integer> counts = new LinkedHashMap<>();
            Map<String, Long> totals = new LinkedHashMap<>();
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack == null || stack.getType() == Material.AIR) continue;
                PriceTable.Entry e = shop.prices().get(stack.getType().name());
                if (e == null || e.sellCents() <= 0) continue;
                long unit = ItemValue.valueCents(e.sellCents(), e.buyCents(), stack);
                if (unit <= 0) continue;
                counts.merge(e.material(), stack.getAmount(), Integer::sum);
                totals.merge(e.material(), dev.superseller.veltryx.util.Money.multiplySaturated(unit, stack.getAmount()),
                        dev.superseller.veltryx.util.Money::addSaturated);
            }
            int slot = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (slot >= 45) break;
                long total = totals.getOrDefault(e.getKey(), 0L);
                inv.setItem(slot, ShopItems.icon(e.getKey(), Math.min(64, e.getValue()),
                        "§b" + PriceTable.displayName(e.getKey()), List.of("§7Amount: " + e.getValue(),
                                "§7Payout: §a$§f" + SellNotices.compact(total), "§eClick to sell")));
                h.slots.put(slot++, e.getKey());
            }
            long value = 0; for (long total : totals.values()) value = dev.superseller.veltryx.util.Money.addSaturated(value, total);
            inv.setItem(49, ShopItems.icon("NETHER_STAR", "§bSell everything",
                    List.of("§7Total payout: §a$§f" + SellNotices.compact(value), "§eClick to sell all")));
            player.openInventory(inv);
        });
    }
    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player) || e.getClickedInventory() == null
                || e.getClickedInventory().getHolder() != h) return;
        int slot = e.getSlot();
        String material = h.slots.get(slot);
        if (slot != 49 && material == null) return;
        ShopService.SellResult result = slot == 49 ? shop.sellAll(player) : shop.sell(player, material, Integer.MAX_VALUE);
        if (result.ok()) {
            if (slot == 49) SellNotices.show(player, messages, "shop.sold-all",
                    Map.of("balance", SellNotices.compact(shop.balance(player))), result.earnedCents());
            else SellNotices.show(player, messages, "shop.sold", Map.of("count", String.valueOf(result.sold()),
                    "item", PriceTable.displayName(material), "balance", SellNotices.compact(shop.balance(player))), result.earnedCents());
        } else if ("shop.no-sell".equals(result.failKey())) player.sendMessage(messages.render("shop.no-sell"));
        else player.sendMessage(messages.render("shop.nothing"));
        open(player);
    }
    @EventHandler public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
