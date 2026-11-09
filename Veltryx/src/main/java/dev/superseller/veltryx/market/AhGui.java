package dev.superseller.veltryx.market;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.AhListing;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/** Browse listings, buy, reclaim, or list the stack in the main hand. */
public final class AhGui implements Listener {
    public static final class Holder implements InventoryHolder {
        private List<AhListing> view = List.of();
        private boolean mine, transitioning;
        private int page;
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private final AhService service;
    private final EconomyService economy;
    private final Messages messages;
    private final MarketPricePrompt prompt;
    private final boolean ownsPrompt;

    public AhGui(AhService service, EconomyService economy, Messages messages, MarketPricePrompt prompt) {
        this(service, economy, messages, prompt, false);
    }
    private AhGui(AhService service, EconomyService economy, Messages messages,
                  MarketPricePrompt prompt, boolean ownsPrompt) {
        this.service = service; this.economy = economy; this.messages = messages;
        this.prompt = prompt; this.ownsPrompt = ownsPrompt;
    }
    /** Standalone compatibility constructor; the GUI also handles its own prompt events. */
    public AhGui(AhService service, EconomyService economy, Messages messages) {
        this(service, economy, messages, new MarketPricePrompt(messages), true);
    }
    public void cancelPendingPrice(Player player) { prompt.cancelPending(player); }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPriceChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        if (ownsPrompt) prompt.onChat(event);
    }
    @EventHandler public void onPriceQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (ownsPrompt) prompt.onQuit(event);
    }

    public void open(Player player, boolean mine, int page) {
        PlatformScheduler.runForPlayer(player, () -> {
            prompt.cancelPending(player);
            Holder holder = new Holder(); holder.mine = mine;
            long now = System.currentTimeMillis();
            List<AhListing> view = mine ? service.listingsOf(player.getUniqueId()) : service.liveListings(now);
            holder.view = view;
            int pages = Math.max(1, (view.size() + 44) / 45);
            int pageNow = Math.max(0, Math.min(page, pages - 1)); holder.page = pageNow;
            Inventory inventory = Bukkit.createInventory(holder, 54,
                    Txt.legacy(mine ? "§8§lYour listings" : "§8§lAuction house"));
            holder.inventory = inventory;
            for (int i = 0; i < 45; i++) {
                int index = pageNow * 45 + i;
                if (index >= view.size()) break;
                AhListing listing = view.get(index);
                boolean expired = !listing.isLiveAt(now);
                boolean own = listing.seller().equals(player.getUniqueId());
                ItemStack original = service.listingItem(listing);
                if (original == null) {
                    inventory.setItem(i, item(Material.BARRIER, 1, Txt.legacy("§cListing unavailable"),
                            List.of(Txt.legacy("§7Item data cannot be read; contact an administrator."))));
                    continue;
                }
                ItemStack icon = original.clone();
                icon.setAmount(Math.min(icon.getMaxStackSize(), Math.max(1, icon.getAmount())));
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                    if (!lore.isEmpty()) lore.add(Component.empty());
                    lore.add(Txt.legacy("§7Seller: §f" + listing.sellerName()));
                    lore.add(Txt.legacy("§7Amount: §f" + listing.amount()));
                    lore.add(Txt.legacy("§7Total price: §a$§f" + Money.formatNumber(listing.priceExactCents())));
                    lore.add(Component.empty());
                    lore.add(Txt.legacy(own ? (expired ? "§cExpired — click to reclaim" : "§cClick to cancel and reclaim")
                            : "§eClick to buy the whole listing"));
                    meta.lore(lore); icon.setItemMeta(meta);
                }
                inventory.setItem(i, icon);
            }
            if (view.isEmpty()) inventory.setItem(22, item(Material.LIME_DYE, 1, Txt.legacy("§aEmpty"),
                    List.of(Txt.legacy(mine ? "§7You have no listings." : "§7No listings right now."))));
            inventory.setItem(45, item(Material.ARROW, 1, Txt.legacy("§e« Previous page"), List.of()));
            inventory.setItem(47, item(Material.EMERALD, 1, Txt.legacy(mine ? "§aBrowse all listings" : "§6My listings"),
                    List.of(Txt.legacy("§7Switch the view."))));
            inventory.setItem(49, item(Material.GOLD_INGOT, 1,
                    Txt.legacy("§6Your balance: §a$§f" + Money.formatCompact(economy.getBalanceExact(player.getUniqueId()))),
                    List.of(Txt.legacy("§7Page §f" + (pageNow + 1) + "§7/§f" + pages))));
            inventory.setItem(51, item(Material.CHEST, 1, Txt.legacy("§eList the item in your hand"),
                    List.of(Txt.legacy("§7Click, then type the total price in chat."),
                            Txt.legacy("§7Or use §f/ah sell <total-price>"))));
            inventory.setItem(53, item(Material.ARROW, 1, Txt.legacy("§eNext page »"), List.of()));
            player.openInventory(inventory);
        });
    }

    private void beginListing(Player player, Holder holder) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR || held.getAmount() <= 0) {
            messages.send(player, "ah.hold-item"); return;
        }
        ItemStack expected = held.clone(); holder.transitioning = true;
        prompt.ask(player, input -> {
            if (!player.hasPermission("veltryx.ah")) { messages.send(player, "shop.no-permission"); return; }
            ItemStack current = player.getInventory().getItemInMainHand();
            if (current == null || current.getAmount() != expected.getAmount() || !current.isSimilar(expected)) {
                messages.send(player, "ah.hand-changed"); open(player, false, 0); return;
            }
            BigInteger price = Money.parseExact(input);
            if (price == null) {
                messages.send(player, "general.invalid-amount", Map.of("input", input));
                open(player, false, 0); return;
            }
            AhService.Result result = service.listHeldItem(player, price);
            messages.send(player, result.templateKey(), result.values());
            open(player, result.ok(), 0);
        }, () -> open(player, false, 0));
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || holder.transitioning
                || event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;
        if (!player.hasPermission("veltryx.ah")) { messages.send(player, "shop.no-permission"); return; }
        int slot = event.getSlot();
        if (slot == 51) { beginListing(player, holder); return; }
        if (slot == 45 || slot == 53 || slot == 47) {
            holder.transitioning = true;
            if (slot == 47) open(player, !holder.mine, 0);
            else open(player, holder.mine, holder.page + (slot == 45 ? -1 : 1));
            return;
        }
        if (slot < 0 || slot >= 45) return;
        int index = holder.page * 45 + slot;
        if (index >= holder.view.size()) return;
        holder.transitioning = true;
        AhListing listing = holder.view.get(index);
        AhService.Result result = listing.seller().equals(player.getUniqueId())
                ? service.reclaim(player, listing.id()) : service.buy(player, listing.id());
        messages.send(player, result.templateKey(), result.values());
        open(player, holder.mine, holder.page);
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private static ItemStack item(Material material, int amount, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(name); meta.lore(new ArrayList<>(lore)); item.setItemMeta(meta); }
        return item;
    }
}
