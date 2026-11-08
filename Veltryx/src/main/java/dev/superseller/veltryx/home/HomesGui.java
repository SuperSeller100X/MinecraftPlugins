package dev.superseller.veltryx.home;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.HomeLocation;
import dev.superseller.veltryx.util.Money;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * The {@code /homes} GUI: every home in the top rows. Left-click teleports,
 * shift-click deletes. The bottom row shows usage, the price of the next home
 * and the balance.
 */
public final class HomesGui implements Listener {

    /** Marks the homes inventory and remembers which slot holds which home. */
    public static final class Holder implements InventoryHolder {
        private final Map<Integer, String> slotNames = new LinkedHashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final HomeService service;
    private final EconomyService economy;
    private final Messages messages;
    private final VeltryxConfig config;

    public HomesGui(HomeService service, EconomyService economy, Messages messages, VeltryxConfig config) {
        this.service = service;
        this.economy = economy;
        this.messages = messages;
        this.config = config;
    }

    /** Opens (or refreshes) the homes overview (Folia-safe). */
    public void open(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            Holder holder = new Holder();
            Inventory inventory = Bukkit.createInventory(holder, 54, Txt.legacy("§8§lYour homes"));
            holder.inventory = inventory;

            List<HomeLocation> homes = service.list(player.getUniqueId());
            int slot = 0;
            for (HomeLocation home : homes) {
                if (slot >= 45) {
                    break;
                }
                inventory.setItem(slot, item(Material.ENDER_PEARL, 1,
                        Txt.legacy("§b" + home.name()),
                        List.of(Txt.legacy("§7World: §f" + home.world()),
                                Txt.legacy(String.format(java.util.Locale.ROOT, "§7Position: §f%d, %d, %d",
                                        (int) home.x(), (int) home.y(), (int) home.z())),
                                Component.empty(),
                                Txt.legacy("§eLeft-click to teleport"),
                                Txt.legacy("§cShift-click to delete"))));
                holder.slotNames.put(slot, home.name());
                slot++;
            }
            if (homes.isEmpty()) {
                inventory.setItem(22, item(Material.LIME_DYE, 1,
                        Txt.legacy("§aNo homes yet"),
                        List.of(Txt.legacy("§7Use §f/sethome <name>§7 to create one."))));
            }

            int nextNumber = homes.size() + 1;
            long nextPrice = HomePricing.costCents(nextNumber, config.freeHomes(), config.homePriceStep());
            inventory.setItem(45, item(Material.WRITABLE_BOOK, 1,
                    Txt.legacy("§aHomes: §f" + homes.size() + "§7/§f" + config.maxHomes()),
                    List.of(Txt.legacy("§7The first §f" + config.freeHomes() + "§7 homes are free."),
                            Txt.legacy("§7Every extra home costs §f$2.5k§7 more than the last."))));
            inventory.setItem(47, item(Material.GOLD_INGOT, 1,
                    Txt.legacy("§6Next home (§f#" + nextNumber + "§6)"),
                    homes.size() >= config.maxHomes()
                            ? List.of(Txt.legacy("§cYou reached the maximum."))
                            : List.of(Txt.legacy("§7Price: §a$§f" + Money.formatCompact(nextPrice)))));
            inventory.setItem(49, item(Material.EMERALD, 1,
                    Txt.legacy("§6Your balance: §a$§f" + Money.formatCompact(
                            economy.getBalance(player.getUniqueId()))), List.of()));
            inventory.setItem(51, item(Material.ENDER_EYE, 1,
                    Txt.legacy("§eCreate a home"),
                    List.of(Txt.legacy("§7Stand where you want it and run"),
                            Txt.legacy("§f/sethome <name>§7 — e.g. §f/sethome base"))));
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
        String homeName = holder.slotNames.get(event.getSlot());
        if (homeName == null) {
            return;
        }
        if (event.isShiftClick()) {
            HomeService.Result result = service.delete(player, homeName);
            messages.send(player, result.templateKey(), result.values());
            open(player);
            return;
        }
        HomeService.Result result = service.teleport(player, homeName);
        messages.send(player, result.templateKey(), result.values());
        if (result.ok()) {
            player.closeInventory();
        }
    }

    private static ItemStack item(Material material, int amount, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(new ArrayList<>(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
