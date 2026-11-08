package dev.superseller.veltryx.market;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.BuyOrder;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
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

    /** Immutable draft state is only changed by clicking controls; no funds are held until confirm. */
    private record Draft(Material material, int amount, long price) { }
    private static final class CreateHolder implements InventoryHolder {
        final UUID owner;
        final Draft draft;
        Inventory inventory;
        boolean transitioning;
        CreateHolder(UUID owner, Draft draft) { this.owner = owner; this.draft = draft; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class PickerHolder implements InventoryHolder {
        final UUID owner;
        final Draft draft;
        final List<Material> choices;
        int page;
        Inventory inventory;
        boolean transitioning;
        PickerHolder(UUID owner, Draft draft, List<Material> choices, int page) {
            this.owner = owner; this.draft = draft; this.choices = choices; this.page = page;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static boolean orderable(Material material) {
        return material != null && !material.isLegacy() && material.isItem()
                && material != Material.AIR && material != Material.SPAWNER;
    }

    /** Opens the GUI used by /order with no arguments. */
    public void openCreate(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            Material initial = held != null && orderable(held.getType()) ? held.getType() : null;
            renderCreate(player, new Draft(initial, 1, 100));
        });
    }

    private void renderCreate(Player player, Draft draft) {
        CreateHolder holder = new CreateHolder(player.getUniqueId(), draft);
        Inventory inv = Bukkit.createInventory(holder, 54, Txt.legacy("§8§lCreate a buy order"));
        holder.inventory = inv;
        inv.setItem(4, item(draft.material() == null ? Material.PAPER : draft.material(), 1,
                Txt.legacy(draft.material() == null ? "§eChoose an item" : "§b" + AhService.displayName(draft.material().name())),
                List.of(Txt.legacy("§7Click to browse all item types."),
                        Txt.legacy("§7Or click an item in your inventory."),
                        Txt.legacy("§8Only the material is tracked, not item metadata."))));
        int[] amountSlots = {9,10,11,15,16,17,27,35};
        int[] amountChanges = {-1024,-64,-1,1,64,1024,-10000,10000};
        for (int i=0; i<amountSlots.length; i++) {
            int change = amountChanges[i];
            inv.setItem(amountSlots[i], item(change < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                    1, Txt.legacy((change < 0 ? "§c" : "§a+") + change + " items"), List.of()));
        }
        inv.setItem(13, item(Material.CHEST, 1, Txt.legacy("§eAmount: §f" + draft.amount()),
                List.of(Txt.legacy("§7Between 1 and 23,040 items."))));
        int[] priceSlots = {18,19,20,21,23,24,25,26};
        long[] priceChanges = {-1_000_000L,-10_000L,-100L,-1L,1L,100L,10_000L,1_000_000L};
        for (int i=0; i<priceSlots.length; i++) {
            long change = priceChanges[i];
            inv.setItem(priceSlots[i], item(change < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                    1, Txt.legacy((change < 0 ? "§c-$" : "§a+$") + Money.formatNumber(Math.abs(change)) + " each"), List.of()));
        }
        inv.setItem(22, item(Material.GOLD_INGOT, 1, Txt.legacy("§ePrice each: §a$§f" + Money.formatNumber(draft.price())),
                List.of(Txt.legacy("§7Between $0.01 and $100,000.00."))));
        inv.setItem(31, item(Material.EMERALD, 1,
                Txt.legacy("§eTotal escrow: §a$§f" + Money.formatNumber(draft.price() * draft.amount())),
                List.of(Txt.legacy("§7Your balance: §a$§f" + Money.formatCompact(economy.getBalanceExact(player.getUniqueId()))),
                        Txt.legacy("§7Paid only when you confirm."))));
        inv.setItem(45, item(Material.ARROW, 1, Txt.legacy("§eBrowse orders"), List.of()));
        inv.setItem(49, item(Material.LIME_STAINED_GLASS_PANE, 1, Txt.legacy("§aCreate order"),
                List.of(Txt.legacy("§7Confirm the item, amount and price."))));
        player.openInventory(inv);
    }

    private void openPicker(Player player, Draft draft, int requestedPage) {
        PlatformScheduler.runForPlayer(player, () -> {
            List<Material> choices = java.util.Arrays.stream(Material.values()).filter(OrderGui::orderable)
                    .sorted(java.util.Comparator.comparing(Material::name)).toList();
            int pages = Math.max(1, (choices.size() + 44) / 45);
            int page = Math.max(0, Math.min(requestedPage, pages - 1));
            PickerHolder holder = new PickerHolder(player.getUniqueId(), draft, choices, page);
            Inventory inv = Bukkit.createInventory(holder, 54, Txt.legacy("§8Choose order item §7(" + (page + 1) + "/" + pages + ")"));
            holder.inventory = inv;
            for (int slot=0; slot<45 && page*45+slot<choices.size(); slot++) {
                Material material = choices.get(page*45+slot);
                inv.setItem(slot, item(material, 1, Txt.legacy("§f" + AhService.displayName(material.name())),
                        List.of(Txt.legacy("§eClick to select"))));
            }
            if (page>0) inv.setItem(45, item(Material.ARROW,1,Txt.legacy("§ePrevious page"),List.of()));
            inv.setItem(49, item(Material.BARRIER,1,Txt.legacy("§eBack to order"),List.of()));
            if (page+1<pages) inv.setItem(53,item(Material.ARROW,1,Txt.legacy("§eNext page"),List.of()));
            player.openInventory(inv);
        });
    }

    private void nextCreate(Player player, CreateHolder holder, Draft draft) {
        holder.transitioning = true;
        PlatformScheduler.runForPlayer(player, () -> renderCreate(player, draft));
    }

    private void createClick(InventoryClickEvent event, Player player, CreateHolder holder) {
        if (!holder.owner.equals(player.getUniqueId()) || holder.transitioning) return;
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;
        Draft draft = holder.draft;
        if (clicked.getHolder() != holder) {
            ItemStack selected = clicked.getItem(event.getSlot());
            if (selected != null && orderable(selected.getType()))
                nextCreate(player, holder, new Draft(selected.getType(), draft.amount(), draft.price()));
            return;
        }
        int slot = event.getSlot();
        int amountDelta = switch (slot) {
            case 9 -> -1024; case 10 -> -64; case 11 -> -1; case 15 -> 1;
            case 16 -> 64; case 17 -> 1024; case 27 -> -10000; case 35 -> 10000; default -> 0;
        };
        if (amountDelta != 0) {
            int amount = Math.max(1, Math.min(23040, draft.amount() + amountDelta));
            nextCreate(player, holder, new Draft(draft.material(), amount, draft.price())); return;
        }
        long priceDelta = switch (slot) {
            case 18 -> -1_000_000L; case 19 -> -10_000L; case 20 -> -100L; case 21 -> -1L;
            case 23 -> 1L; case 24 -> 100L; case 25 -> 10_000L; case 26 -> 1_000_000L; default -> 0L;
        };
        if (priceDelta != 0) {
            long price = Math.max(1L, Math.min(AhService.MAX_PRICE_EACH, draft.price() + priceDelta));
            nextCreate(player, holder, new Draft(draft.material(), draft.amount(), price)); return;
        }
        if (slot == 4) { holder.transitioning = true; openPicker(player, draft, 0); }
        else if (slot == 45) { holder.transitioning = true; open(player, false, 0); }
        else if (slot == 49) {
            if (!player.hasPermission("veltryx.order")) { messages.send(player, "shop.no-permission"); return; }
            if (!orderable(draft.material())) { messages.send(player, "order.choose-item"); return; }
            holder.transitioning = true;
            OrderService.Result result = service.create(player, draft.material().name(), draft.amount(), draft.price());
            messages.send(player, result.templateKey(), result.values());
            if (result.ok()) open(player, true, 0); else holder.transitioning = false;
        }
    }

    private void pickerClick(InventoryClickEvent event, Player player, PickerHolder holder) {
        if (!holder.owner.equals(player.getUniqueId()) || holder.transitioning) return;
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;
        if (clicked.getHolder() != holder) {
            ItemStack selected = clicked.getItem(event.getSlot());
            if (selected != null && orderable(selected.getType())) {
                holder.transitioning = true;
                PlatformScheduler.runForPlayer(player, () -> renderCreate(player,
                        new Draft(selected.getType(), holder.draft.amount(), holder.draft.price())));
            }
            return;
        }
        int slot = event.getSlot();
        if (slot >= 0 && slot < 45 && holder.page*45+slot < holder.choices.size()) {
            Material selected = holder.choices.get(holder.page*45+slot);
            holder.transitioning = true;
            PlatformScheduler.runForPlayer(player, () -> renderCreate(player,
                    new Draft(selected, holder.draft.amount(), holder.draft.price())));
        } else if (slot == 45 && holder.page > 0) {
            holder.transitioning = true; openPicker(player, holder.draft, holder.page-1);
        } else if (slot == 53 && (holder.page+1)*45 < holder.choices.size()) {
            holder.transitioning = true; openPicker(player, holder.draft, holder.page+1);
        } else if (slot == 49) {
            holder.transitioning = true;
            PlatformScheduler.runForPlayer(player, () -> renderCreate(player, holder.draft));
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
                    lore.add(Txt.legacy("§7Ready to collect: §f" + order.claimable()));
                    if (order.claimable() > 0) lore.add(Txt.legacy("§eLeft-click to collect supplied items"));
                    if (!order.closed()) lore.add(Txt.legacy("§cRight-click to cancel and refund the remainder"));
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
                                : "§7No open orders. Create one with §f/order"))));
            }

            inventory.setItem(45, item(Material.ARROW, 1, Txt.legacy("§e« Previous page"), List.of()));
            inventory.setItem(47, item(Material.EMERALD, 1,
                    Txt.legacy(mine ? "§aBrowse all orders" : "§6My orders"),
                    List.of(Txt.legacy("§7Switch the view."))));
            inventory.setItem(49, item(Material.GOLD_INGOT, 1,
                    Txt.legacy("§6Your balance: §a$§f" + Money.formatCompact(
                            economy.getBalanceExact(player.getUniqueId()))),
                    List.of(Txt.legacy("§7Page §f" + (pageNow + 1) + "§7/§f" + pages))));
            inventory.setItem(51, item(Material.WRITABLE_BOOK, 1,
                    Txt.legacy("§eCreate an order"),
                    List.of(Txt.legacy("§7Click to set up a buy order."))));
            inventory.setItem(53, item(Material.ARROW, 1, Txt.legacy("§eNext page »"), List.of()));
            player.openInventory(inventory);
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder current = event.getInventory().getHolder();
        if (!(current instanceof Holder) && !(current instanceof CreateHolder) && !(current instanceof PickerHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (current instanceof CreateHolder create) { createClick(event, player, create); return; }
        if (current instanceof PickerHolder picker) { pickerClick(event, player, picker); return; }
        Holder holder = (Holder) current;
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
        if (slot == 51) { openCreate(player); return; }
        if (slot < 0 || slot >= 45) {
            return;
        }
        int index = holder.page * 45 + slot;
        if (index >= holder.view.size()) {
            return;
        }
        BuyOrder order = holder.view.get(index);
        if (order.buyer().equals(player.getUniqueId())) {
            OrderService.Result result = event.isRightClick()
                    ? service.cancel(player, order.id()) : service.claim(player, order.id());
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

    @EventHandler public void onDrag(InventoryDragEvent event) {
        InventoryHolder current = event.getView().getTopInventory().getHolder();
        if (current instanceof Holder || current instanceof CreateHolder || current instanceof PickerHolder)
            event.setCancelled(true);
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
