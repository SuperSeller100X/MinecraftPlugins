package dev.superseller.veltryx.market;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.BuyOrder;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The {@code /orders} GUI: fill other players' buy orders with a click (the
 * payout is instant), or switch to "my orders" to cancel and get the escrow
 * back.
 */
public final class OrderGui implements Listener {

    /** Marks the orders inventory and remembers what each slot fills. */
    public static final class Holder implements InventoryHolder {
        private List<BuyOrder> view = List.of();
        private boolean mine;
        private int page;
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final OrderService service;
    private final EconomyService economy;
    private final Messages messages;

    public OrderGui(OrderService service, EconomyService economy, Messages messages) {
        this.service = service;
        this.economy = economy;
        this.messages = messages;
    }

    /** Opens (or refreshes) the orders browser (Folia-safe). */
    public void open(Player player, boolean mine, int page) {
        PlatformScheduler.runForPlayer(player, () -> {
            Holder holder = new Holder();
            holder.mine = mine;
            holder.page = Math.max(0, page);
            service.sweepExpired(System.currentTimeMillis());
            List<BuyOrder> view = mine
                    ? service.ordersOf(player.getUniqueId())
                    : service.openOrders(System.currentTimeMillis());
            holder.view = view;
            Inventory inventory = Bukkit.createInventory(holder, 54,
                    Txt.legacy(mine ? "§8§lYour buy orders" : "§8§lBuy orders"));
            holder.inventory = inventory;

            int pages = Math.max(1, (view.size() + 44) / 45);
            int pageNow = Math.min(holder.page, pages - 1);
            holder.page = pageNow;
            for (int i = 0; i < 45; i++) {
                int index = pageNow * 45 + i;
                if (index >= view.size()) {
                    break;
                }
                BuyOrder order = view.get(index);
                int held = OrderService.heldCount(player, order.material());
                List<Component> lore = new ArrayList<>();
                lore.add(Txt.legacy("§7Buyer: §f" + order.buyerName()));
                lore.add(Txt.legacy("§7Wants: §f" + order.remaining() + " §7of §f" + order.amount()
                        + " §8(§7" + order.filled() + " done§8)"));
                lore.add(Txt.legacy("§7Pays: §a$§f" + Money.formatCompact(order.priceEachCents())
                        + " §7each §8(§a$§f" + Money.formatCompact(
                                order.priceEachCents() * order.remaining()) + " §7left§8)"));
                lore.add(Txt.legacy("§7You hold: §f" + held));
                lore.add(Component.empty());
                if (order.buyer().equals(player.getUniqueId())) {
                    lore.add(Txt.legacy("§cClick to cancel and get the escrow back"));
                } else {
                    lore.add(Txt.legacy("§eClick to sell one stack, §6shift-click to sell all you have"));
                }
                inventory.setItem(i, item(Material.matchMaterial(order.material()),
                        Math.min(64, order.remaining()),
                        Txt.legacy("§f" + AhService.displayName(order.material())
                                + " §7x" + order.remaining()), lore));
            }
            if (view.isEmpty()) {
                inventory.setItem(22, item(Material.LIME_DYE, 1, Txt.legacy("§aEmpty"),
                        List.of(Txt.legacy(mine
                                ? "§7You have no open orders."
                                : "§7No open orders. Create one with §f/order <item> <amount> <price>"))));
            }

            inventory.setItem(45, item(Material.ARROW, 1, Txt.legacy("§e« Previous page"), List.of()));
            inventory.setItem(47, item(Material.EMERALD, 1,
                    Txt.legacy(mine ? "§aBrowse all orders" : "§6My orders"),
                    List.of(Txt.legacy("§7Switch the view."))));
            inventory.setItem(49, item(Material.GOLD_INGOT, 1,
                    Txt.legacy("§6Your balance: §a$§f" + Money.formatCompact(
                            economy.getBalance(player.getUniqueId()))),
                    List.of(Txt.legacy("§7Page §f" + (pageNow + 1) + "§7/§f" + pages))));
            inventory.setItem(51, item(Material.WRITABLE_BOOK, 1,
                    Txt.legacy("§eCreate an order"),
                    List.of(Txt.legacy("§7Run §f/order <item> <amount> <price>"),
                            Txt.legacy("§7e.g. §f/order iron_ingot 128 1.10"))));
            inventory.setItem(53, item(Material.ARROW, 1, Txt.legacy("§eNext page »"), List.of()));
            player.openInventory(inventory);
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 45) {
            open(player, holder.mine, holder.page - 1);
            return;
        }
        if (slot == 53) {
            open(player, holder.mine, holder.page + 1);
            return;
        }
        if (slot == 47) {
            open(player, !holder.mine, 0);
            return;
        }
        if (slot >= 45) {
            return;
        }
        int index = holder.page * 45 + slot;
        if (index >= holder.view.size()) {
            return;
        }
        BuyOrder order = holder.view.get(index);
        if (order.buyer().equals(player.getUniqueId())) {
            OrderService.Result result = service.cancel(player, order.id());
            messages.send(player, result.templateKey(), result.values());
        } else {
            int held = OrderService.heldCount(player, order.material());
            int want = event.isShiftClick() ? held : Math.min(64, held);
            if (want <= 0) {
                messages.send(player, "ah.not-enough", java.util.Map.of(
                        "item", AhService.displayName(order.material()), "count", "0"));
            } else {
                OrderService.Result result = service.fill(player, order.id(), want);
                if (!result.ok()) {
                    messages.send(player, result.templateKey(), result.values());
                }
            }
        }
        open(player, holder.mine, holder.page);
    }

    private static ItemStack item(Material material, int amount, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(new ArrayList<>(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
