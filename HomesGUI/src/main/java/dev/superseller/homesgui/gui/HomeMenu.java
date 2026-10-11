package dev.superseller.homesgui.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.superseller.homesgui.HomesGUIPlugin;
import dev.superseller.homesgui.config.HomesSettings;
import dev.superseller.homesgui.config.Messages;
import dev.superseller.homesgui.model.HomeLocation;
import dev.superseller.homesgui.model.PlayerHomes;
import dev.superseller.homesgui.service.HomeService;
import dev.superseller.homesgui.util.SoundPlayer;

/** Paginated chest GUI: click to visit, set, update, delete, or buy home slots. */
public final class HomeMenu {

    private final HomesGUIPlugin plugin;
    private final HomeService service;

    public HomeMenu(HomesGUIPlugin plugin, HomeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void open(Player player) {
        open(player, 0);
    }

    /** Must be called on the viewer's owning entity/region thread. */
    public void open(Player player, int requestedPage) {
        HomesSettings.Gui gui = plugin.settings().gui();
        if (!gui.enabled()) {
            plugin.messages().send(player, "gui-disabled");
            return;
        }
        int displaySlots = displaySlots(player);
        int pageSize = gui.pageSize();
        int pages = Math.max(1, (int) (((long) displaySlots + pageSize - 1L) / pageSize));
        int page = Math.clamp(requestedPage, 0, pages - 1);
        HomeMenuHolder holder = new HomeMenuHolder(player.getUniqueId(), page);
        String title = gui.title().replace("%player%", player.getName())
                .replace("%page%", Integer.toString(page + 1)).replace("%pages%", Integer.toString(pages));
        Inventory inventory = Bukkit.createInventory(holder, gui.inventorySize(), Messages.deserialize(title));
        holder.inventory(inventory);
        render(player, inventory, page, displaySlots, pages);
        player.openInventory(inventory);
        SoundPlayer.play(plugin, player, "gui-open");
    }

    public int pageSize() {
        return plugin.settings().gui().pageSize();
    }

    public int displaySlots(Player player) {
        PlayerHomes data = service.profile(player.getUniqueId());
        return Math.max(service.availableSlots(player), data.highestSavedSlot());
    }

    private void render(Player player, Inventory inventory, int page, int displaySlots, int pages) {
        HomesSettings.Gui gui = plugin.settings().gui();
        if (gui.fillEmpty()) {
            ItemStack filler = createItem(gui.filler(), Map.of());
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }

        PlayerHomes data = service.profile(player.getUniqueId());
        int pageSize = gui.pageSize();
        long firstSlot = (long) page * pageSize + 1L;
        for (int offset = 0; offset < pageSize; offset++) {
            long slotNumber = firstSlot + offset;
            if (slotNumber > displaySlots || slotNumber > Integer.MAX_VALUE) {
                break;
            }
            int homeSlot = (int) slotNumber;
            HomeLocation home = data.home(homeSlot);
            Map<String, String> values = baseValues(player, page, pages, displaySlots);
            values.put("%slot%", Integer.toString(homeSlot));
            if (home == null) {
                inventory.setItem(offset, createItem(gui.empty(), values));
            } else {
                values.put("%world%", home.world());
                values.put("%x%", coordinate(home.x()));
                values.put("%y%", coordinate(home.y()));
                values.put("%z%", coordinate(home.z()));
                inventory.setItem(offset, createItem(gui.home(), values));
            }
        }

        int barStart = inventory.getSize() - 9;
        Map<String, String> values = baseValues(player, page, pages, displaySlots);
        addButton(inventory, barStart, gui.previous(), values);
        addButton(inventory, barStart, gui.info(), values);
        addButton(inventory, barStart, gui.buy(), values);
        addButton(inventory, barStart, gui.close(), values);
        addButton(inventory, barStart, gui.next(), values);
    }

    private Map<String, String> baseValues(Player player, int page, int pages, int displaySlots) {
        PlayerHomes data = service.profile(player.getUniqueId());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("%player%", player.getName());
        values.put("%page%", Integer.toString(page + 1));
        values.put("%pages%", Integer.toString(pages));
        values.put("%saved%", Integer.toString(data.savedCount()));
        values.put("%available%", Integer.toString(service.availableSlots(player)));
        values.put("%purchased%", Integer.toString(data.purchasedSlots()));
        values.put("%next-price%", service.formatMoney(service.nextPrice(player.getUniqueId())));
        return values;
    }

    private static void addButton(Inventory inventory, int barStart, HomesSettings.Button button,
                                  Map<String, String> values) {
        inventory.setItem(barStart + button.position(), createItem(button.icon(), values));
    }

    private static ItemStack createItem(HomesSettings.Icon icon, Map<String, String> values) {
        ItemStack item = new ItemStack(icon.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Messages.deserialize(replace(icon.name(), values)));
        List<net.kyori.adventure.text.Component> lore = icon.lore().stream()
                .map(line -> Messages.deserialize(replace(line, values))).toList();
        meta.lore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String replace(String input, Map<String, String> values) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static String coordinate(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }
}
