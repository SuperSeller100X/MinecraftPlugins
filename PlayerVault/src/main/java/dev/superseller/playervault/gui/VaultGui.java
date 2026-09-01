package dev.superseller.playervault.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.superseller.playervault.config.Messages;
import dev.superseller.playervault.config.Settings;
import dev.superseller.playervault.model.VaultData;
import dev.superseller.playervault.service.VaultService;
import dev.superseller.playervault.util.Texts;
import net.kyori.adventure.text.Component;

/**
 * Builds and paints the vault inventory.
 *
 * <p>A page shows as many storage rows as the vault has (capped at
 * {@link GuiLayout#MAX_STORAGE_ROWS_PER_PAGE}) plus one utility row underneath.
 * Only rows the player actually owns are rendered, so a slot can never be clicked
 * into a part of the vault that has not been bought yet.
 *
 * <p>The same view is used for admin inspection. In that mode the money and
 * inventory buttons are hidden, because they would act on the viewer rather than on
 * the vault's owner.
 *
 * <p>All methods must run on the region thread that owns the viewer.
 */
public final class VaultGui {

    private final Messages messages;
    private final VaultService service;
    private final Supplier<Settings> settings;

    public VaultGui(Messages messages, VaultService service, Supplier<Settings> settings) {
        this.messages = messages;
        this.service = service;
        this.settings = settings;
    }

    /** Returns the holder when {@code inventory} is one of ours, otherwise {@code null}. */
    public static VaultHolder holder(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        return inventory.getHolder() instanceof VaultHolder vaultHolder ? vaultHolder : null;
    }

    /**
     * Opens a player's own vault.
     *
     * @param player the viewer and owner
     * @param requestedPage zero-based page, or a negative number to reuse the last one
     */
    public void open(Player player, int requestedPage) {
        open(player, player.getUniqueId(), requestedPage, false);
    }

    /**
     * Opens somebody else's vault for inspection.
     *
     * @param viewer the admin looking at the vault
     * @param owner whose vault is shown
     */
    public void openAdmin(Player viewer, UUID owner, int requestedPage) {
        open(viewer, owner, requestedPage, true);
    }

    private void open(Player viewer, UUID owner, int requestedPage, boolean adminView) {
        VaultData data = service.vaultById(owner);
        if (!adminView) {
            service.syncBonusRows(viewer, data);
        }
        GuiLayout layout = new GuiLayout(settings.get().gui().pageRows(), data.rows());
        int remembered = settings.get().gui().rememberPage() ? service.page(owner) : 0;
        int page = layout.clampPage(requestedPage < 0 ? remembered : requestedPage);

        VaultHolder holder = new VaultHolder(owner, page, layout, adminView);
        Inventory inventory = Bukkit.createInventory(holder, layout.inventorySize(page), title(layout, page));
        holder.inventory(inventory);

        fillStorage(inventory, holder, data);
        fillButtons(viewer, inventory, holder, data);

        service.page(owner, page);
        viewer.openInventory(inventory);
    }

    /** Repaints an already open vault, rebuilding it when the size changed. */
    public void repaint(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        VaultHolder holder = holder(player.getOpenInventory().getTopInventory());
        if (holder == null) {
            return;
        }
        VaultData data = service.vaultById(holder.owner());
        if (holder.layout().totalRows() != data.rows()) {
            open(player, holder.owner(), holder.page(), holder.adminView());
            return;
        }
        Inventory inventory = player.getOpenInventory().getTopInventory();
        fillStorage(inventory, holder, data);
        fillButtons(player, inventory, holder, data);
    }

    private Component title(GuiLayout layout, int page) {
        String raw = Texts.replace(settings.get().gui().title(), Map.of(
                "%page%", String.valueOf(page + 1),
                "%pages%", String.valueOf(layout.pageCount())));
        return Texts.parse(raw);
    }

    private void fillStorage(Inventory inventory, VaultHolder holder, VaultData data) {
        GuiLayout layout = holder.layout();
        int page = holder.page();
        for (int slot = 0; slot < layout.storageSlots(page); slot++) {
            inventory.setItem(slot, data.item(layout.vaultSlot(page, slot)));
        }
    }

    private void fillButtons(Player viewer, Inventory inventory, VaultHolder holder, VaultData data) {
        Settings.Buttons buttons = settings.get().gui().buttons();
        GuiLayout layout = holder.layout();
        int page = holder.page();
        int pages = layout.pageCount();
        int base = layout.storageSlots(page);
        boolean adminView = holder.adminView();

        ItemStack[] bar = new ItemStack[GuiLayout.UTILITY_SLOTS];
        for (int i = 0; i < bar.length; i++) {
            bar[i] = icon(buttons.filler().material(), "gui.filler-name", List.of());
        }
        if (pages > 1) {
            if (page > 0) {
                bar[buttons.previous().slot()] = icon(buttons.previous().material(),
                        "gui.previous-name", messages.rawList("gui.page-lore-nav"));
            }
            if (page < pages - 1) {
                bar[buttons.next().slot()] = icon(buttons.next().material(),
                        "gui.next-name", messages.rawList("gui.page-lore-nav"));
            }
        }
        bar[buttons.page().slot()] = icon(buttons.page().material(), "gui.page-name",
                messages.rawList("gui.page-lore",
                        "%from%", String.valueOf(layout.firstRow(page) + 1),
                        "%to%", String.valueOf(layout.firstRow(page) + layout.storageRows(page)),
                        "%rows%", String.valueOf(data.rows())),
                "%page%", String.valueOf(page + 1),
                "%pages%", String.valueOf(pages));
        bar[buttons.info().slot()] = icon(buttons.info().material(), "gui.info-name",
                messages.rawList("gui.info-lore",
                        "%rows%", String.valueOf(data.rows()),
                        "%slots%", String.valueOf(data.capacity()),
                        "%used%", String.valueOf(data.usedSlots())));

        if (!adminView) {
            bar[buttons.sort().slot()] =
                    icon(buttons.sort().material(), "gui.sort-name", messages.rawList("gui.sort-lore"));
            bar[buttons.deposit().slot()] =
                    icon(buttons.deposit().material(), "gui.deposit-name", messages.rawList("gui.deposit-lore"));
            bar[buttons.withdraw().slot()] =
                    icon(buttons.withdraw().material(), "gui.withdraw-name", messages.rawList("gui.withdraw-lore"));
            bar[buttons.upgrade().slot()] = upgradeButton(viewer, data, buttons.upgrade());
        }

        for (int i = 0; i < bar.length; i++) {
            inventory.setItem(base + i, bar[i]);
        }
    }

    private ItemStack upgradeButton(Player viewer, VaultData data, Settings.ButtonSpec spec) {
        if (isMaxed(viewer, data)) {
            return icon(spec.material(), "gui.upgrade-maxed-name",
                    messages.rawList("gui.upgrade-maxed-lore", "%rows%", String.valueOf(data.rows())));
        }
        String price = service.money(service.nextPrice(data));
        String balance = settings.get().economy().enabled()
                ? service.money(service.balance(viewer))
                : "-";
        return icon(spec.material(), "gui.upgrade-name",
                messages.rawList("gui.upgrade-lore", "%price%", price, "%balance%", balance));
    }

    /** {@code true} when the row limit applies to this player and has been reached. */
    public boolean isMaxed(Player viewer, VaultData data) {
        Settings.Vault config = settings.get().vault();
        return config.limited()
                && !viewer.hasPermission("playervault.bypass.limit")
                && data.rows() >= config.maxRows();
    }

    /** Builds a GUI icon from a material, a name key and raw lore lines. */
    private ItemStack icon(Material material, String nameKey, List<String> lore, String... replacements) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Texts.parse(messages.raw(nameKey, replacements)));
            Map<String, String> values = Texts.map(replacements);
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Texts.parse(Texts.replace(line, values)));
            }
            meta.lore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }
}
