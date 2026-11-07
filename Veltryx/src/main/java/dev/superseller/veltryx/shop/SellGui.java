package dev.superseller.veltryx.shop;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.util.PlatformScheduler;
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
 * The {@code /sell} GUI: every sellable item in the player's inventory with
 * its payout. Click an item to sell all of it, or use the button to sell
 * everything at once. Sell prices are always below shop buy prices.
 */
public final class SellGui implements Listener {

    /** Marks the sell inventory and remembers which slot sells what. */
    public static final class Holder implements InventoryHolder {
        private final Map<Integer, String> slotMaterials = new LinkedHashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ShopService shop;
    private final Messages messages;

    public SellGui(ShopService shop, Messages messages) {
        this.shop = shop;
        this.messages = messages;
    }

    /** Opens (or refreshes) the sell overview (Folia-safe). */
    public void open(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            Holder holder = new Holder();
            Inventory inventory = Bukkit.createInventory(holder, 54,
                    Component.text("§8§lSell items"));
            holder.inventory = inventory;

            Map<String, Integer> counts = new LinkedHashMap<>();
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack == null || stack.getType() == Material.AIR) {
                    continue;
                }
                if (shop.prices().sellCents(stack.getType().name()) <= 0) {
                    continue;
                }
                counts.merge(stack.getType().name(), stack.getAmount(), Integer::sum);
            }

            int slot = 0;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (slot >= 45) {
                    break;
                }
                String materialName = entry.getKey();
                int amount = entry.getValue();
                long unit = shop.prices().sellCents(materialName);
                long total = unit * amount;
                Material material = Material.matchMaterial(materialName);
                inventory.setItem(slot, item(material == null ? Material.PAPER : material, amount,
                        Component.text("§f" + PriceTable.displayName(materialName)),
                        List.of(Component.text("§7Amount: §f" + amount),
                                Component.text("§7Payout: §6" + shop.format(total)
                                        + " §8(§7" + shop.format(unit) + " each§8)"),
                                Component.text(""),
                                Component.text("§eClick to sell all"))));
                holder.slotMaterials.put(slot, materialName);
                slot++;
            }
            if (counts.isEmpty()) {
                inventory.setItem(22, item(Material.LIME_DYE, 1,
                        Component.text("§aNothing to sell"),
                        List.of(Component.text("§7Your inventory has no sellable items."))));
            }

            long value = 0;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                value += shop.prices().sellCents(entry.getKey()) * entry.getValue();
            }
            inventory.setItem(49, item(Material.EMERALD, 1,
                    Component.text("§aSell everything"),
                    List.of(Component.text("§7Total payout: §6" + shop.format(value)),
                            Component.text(""),
                            Component.text("§eClick to sell it all"))));
            inventory.setItem(53, item(Material.GOLD_INGOT, 1,
                    Component.text("§6Your balance: §a" + shop.format(shop.balance(player))), List.of()));
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
        if (slot == 49) {
            ShopService.SellResult result = shop.sellAll(player);
            if (result.ok()) {
                player.sendMessage(messages.render("shop.sold-all", Map.of(
                        "price", shop.format(result.earnedCents()),
                        "balance", shop.format(shop.balance(player)))));
            } else {
                player.sendMessage(messages.render(result.failKey().equals("shop.no-sell")
                        ? "shop.no-sell" : "shop.nothing"));
            }
            open(player);
            return;
        }
        String materialName = holder.slotMaterials.get(slot);
        if (materialName == null) {
            return;
        }
        ShopService.SellResult result = shop.sell(player, materialName, Integer.MAX_VALUE);
        if (result.ok()) {
            player.sendMessage(messages.render("shop.sold", Map.of(
                    "amount", String.valueOf(result.sold()),
                    "item", PriceTable.displayName(materialName),
                    "price", shop.format(result.earnedCents()),
                    "balance", shop.format(shop.balance(player)))));
        } else {
            player.sendMessage(messages.render(result.failKey().equals("shop.no-sell")
                    ? "shop.no-sell" : "shop.nothing"));
        }
        open(player);
    }

    private static ItemStack item(Material material, int amount, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(new ArrayList<>(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
