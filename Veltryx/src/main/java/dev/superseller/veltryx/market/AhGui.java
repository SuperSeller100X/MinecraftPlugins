package dev.superseller.veltryx.market;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.AhListing;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * The {@code /ah} GUI: browse live listings, buy with a click, and switch to
 * "my listings" to cancel or reclaim expired ones.
 */
public final class AhGui implements Listener {

    /** Marks the auction inventory and remembers what each slot sells. */
    public static final class Holder implements InventoryHolder {
        private List<AhListing> view = List.of();
        private boolean mine;
        private int page;
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final AhService service;
    private final EconomyService economy;
    private final Messages messages;

    public AhGui(AhService service, EconomyService economy, Messages messages) {
        this.service = service;
        this.economy = economy;
        this.messages = messages;
    }

    /** Opens (or refreshes) the auction house (Folia-safe). */
    public void open(Player player, boolean mine, int page) {
        PlatformScheduler.runForPlayer(player, () -> {
            Holder holder = new Holder();
            holder.mine = mine;
            holder.page = Math.max(0, page);
            long now = System.currentTimeMillis();
            List<AhListing> view = mine
                    ? service.listingsOf(player.getUniqueId())
                    : service.liveListings(now);
            holder.view = view;
            Inventory inventory = Bukkit.createInventory(holder, 54,
                    Txt.legacy(mine ? "§8§lYour listings" : "§8§lAuction house"));
            holder.inventory = inventory;

            int pages = Math.max(1, (view.size() + 44) / 45);
            int pageNow = Math.min(holder.page, pages - 1);
            holder.page = pageNow;
            for (int i = 0; i < 45; i++) {
                int index = pageNow * 45 + i;
                if (index >= view.size()) {
                    break;
                }
                AhListing listing = view.get(index);
                long each = listing.amount() > 0 ? listing.priceCents() / listing.amount() : 0;
                boolean expired = !listing.isLiveAt(now);
                boolean own = listing.seller().equals(player.getUniqueId());
                List<Component> lore = new ArrayList<>();
                lore.add(Txt.legacy("§7Seller: §f" + listing.sellerName()));
                lore.add(Txt.legacy("§7Amount: §f" + listing.amount()));
                lore.add(Txt.legacy("§7Price: §a$§f" + Money.formatCompact(listing.priceCents())
                        + " §8(§7§a$§f" + Money.formatCompact(each) + "§7 each§8)"));
                lore.add(Component.empty());
                if (own && !expired) {
                    lore.add(Txt.legacy("§cClick to cancel and take your items back"));
                } else if (own) {
                    lore.add(Txt.legacy("§cExpired — click to reclaim your items"));
                } else {
                    lore.add(Txt.legacy("§eClick to buy"));
                }
                inventory.setItem(i, item(Material.matchMaterial(listing.material()),
                        Math.min(64, listing.amount()),
                        Txt.legacy((expired ? "§8" : "§f") + AhService.displayName(listing.material())
                                + (listing.amount() > 64 ? " §7x" + listing.amount() : "")),
                        lore));
            }
            if (view.isEmpty()) {
                inventory.setItem(22, item(Material.LIME_DYE, 1, Txt.legacy("§aEmpty"),
                        List.of(Txt.legacy(mine
                                ? "§7You have no listings."
                                : "§7No listings right now. Sell items with §f/ah sell <price>"))));
            }

            inventory.setItem(45, item(Material.ARROW, 1, Txt.legacy("§e« Previous page"), List.of()));
            inventory.setItem(47, item(Material.EMERALD, 1,
                    Txt.legacy(mine ? "§aBrowse all listings" : "§6My listings"),
                    List.of(Txt.legacy("§7Switch the view."))));
            inventory.setItem(49, item(Material.GOLD_INGOT, 1,
                    Txt.legacy("§6Your balance: §a$§f" + Money.formatCompact(
                            economy.getBalance(player.getUniqueId()))),
                    List.of(Txt.legacy("§7Page §f" + (pageNow + 1) + "§7/§f" + pages))));
            inventory.setItem(51, item(Material.CHEST, 1,
                    Txt.legacy("§eSell an item"),
                    List.of(Txt.legacy("§7Hold the item and run"),
                            Txt.legacy("§f/ah sell <price-per-item>"))));
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
        AhListing listing = holder.view.get(index);
        AhService.Result result = listing.seller().equals(player.getUniqueId())
                ? service.reclaim(player, listing.id())
                : service.buy(player, listing.id());
        messages.send(player, result.templateKey(), result.values());
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
