package dev.superseller.veltryx.rtp;

import java.util.ArrayList;
import java.util.List;

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

import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.Txt;

/**
 * The {@code /rtp} GUI: pick Overworld, Nether or End. Each option shows
 * the search radius; the search then finds a safe block and the teleport
 * happens after a 3 second countdown.
 */
public final class RtpGui implements Listener {

    /** Marks the world picker. */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final RtpService service;

    public RtpGui(RtpService service) {
        this.service = service;
    }

    /** Opens the world picker (Folia-safe). */
    public void open(Player player) {
        PlatformScheduler.runForPlayer(player, () -> {
            Holder holder = new Holder();
            Inventory inventory = Bukkit.createInventory(holder, 27, Txt.legacy("§8§lRandom teleport"));
            holder.inventory = inventory;

            inventory.setItem(10, item("GRASS_BLOCK", "§aOverworld", List.of(
                    "§7Radius: up to 10 million blocks",
                    "§7Finds a safe block on the surface",
                    "",
                    "§eClick to teleport")));
            inventory.setItem(13, item("NETHERRACK", "§cNether", List.of(
                    "§7Radius: 1.25 million blocks (nether scale)",
                    "§7Searches below the bedrock roof",
                    "",
                    "§eClick to teleport")));
            inventory.setItem(15, item("END_STONE", "§dEnd", List.of(
                    "§7Radius: up to 10 million blocks",
                    "§7Outer islands — never in the void",
                    "",
                    "§eClick to teleport")));
            inventory.setItem(22, item("CLOCK", "§7Teleporting takes 3 seconds", List.of(
                    "§7A safe spot is searched first, then a",
                    "§7short countdown, then you teleport.")));
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
        switch (event.getSlot()) {
            case 10 -> {
                player.closeInventory();
                service.start(player, "overworld");
            }
            case 13 -> {
                player.closeInventory();
                service.start(player, "nether");
            }
            case 15 -> {
                player.closeInventory();
                service.start(player, "end");
            }
            default -> { }
        }
    }

    private static ItemStack item(String materialName, String name, List<String> lore) {
        Material material = Material.matchMaterial(materialName);
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Txt.legacy(name));
            List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Txt.legacy(line));
            }
            meta.lore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }
}
