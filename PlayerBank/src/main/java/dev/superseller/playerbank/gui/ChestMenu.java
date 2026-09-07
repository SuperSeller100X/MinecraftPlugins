package dev.superseller.playerbank.gui;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.config.BankConfig;
import dev.superseller.playerbank.config.Messages;
import dev.superseller.playerbank.model.BankAccount;
import dev.superseller.playerbank.model.BankLogEntry;
import dev.superseller.playerbank.util.Sounds;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The chest-styled bank menu. One shared {@link BankHolder} inventory with
 * three views: MAIN (balances, deposit/withdraw quick buttons, interest, logs,
 * style, close), LOGS (paginated transaction history) and INTEREST (interest
 * details). Purely display + slot mapping — clicks are routed by
 * {@code ChestMenuListener}, transfers run through the shared
 * {@code Transactor}.
 */
public final class ChestMenu {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final PlayerBankPlugin plugin;

    public ChestMenu(PlayerBankPlugin plugin) {
        this.plugin = plugin;
    }

    // --- opening -------------------------------------------------------------

    public void openMain(Player player) {
        BankHolder holder = new BankHolder();
        holder.view(BankHolder.View.MAIN);
        Inventory inv = Bukkit.createInventory(holder, plugin.bankConfig().chestRows() * 9,
                plugin.messages().deserialize(plugin.bankConfig().chestTitle()));
        holder.setInventory(inv);
        renderMain(player, inv);
        player.openInventory(inv);
        Sounds.play(plugin.bankConfig(), player, "open");
    }

    public void openLogs(Player player, int page) {
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());
        int perPage = ChestLayout.logsPerPage(plugin.bankConfig().chestRows() * 9);
        int pages = acc.logPages(perPage);
        int current = Math.min(Math.max(1, page), pages);
        BankHolder holder = new BankHolder();
        holder.view(BankHolder.View.LOGS);
        holder.page(current);
        Inventory inv = Bukkit.createInventory(holder, plugin.bankConfig().chestRows() * 9,
                plugin.messages().deserialize(plugin.bankConfig().chestTitle()));
        holder.setInventory(inv);
        renderLogs(player, inv, current);
        player.openInventory(inv);
    }

    public void openInterest(Player player) {
        BankHolder holder = new BankHolder();
        holder.view(BankHolder.View.INTEREST);
        Inventory inv = Bukkit.createInventory(holder, plugin.bankConfig().chestRows() * 9,
                plugin.messages().deserialize(plugin.bankConfig().chestTitle()));
        holder.setInventory(inv);
        renderInterest(player, inv);
        player.openInventory(inv);
    }

    // --- rendering -----------------------------------------------------------

    /** Repaints the main view (after a deposit/withdraw from the listener). */
    public void renderMain(Player player, Inventory inv) {
        BankConfig cfg = plugin.bankConfig();
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());

        ItemStack filler = filler();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        Map<String, String> ph = commonPlaceholders(player, acc);
        inv.setItem(ChestLayout.slotInfo(), icon(cfg.chestIcon("info"),
                "gui.chest.info-name", "gui.chest.info-lore", ph));

        List<Amount> quick = cfg.quickAmounts();
        int start = ChestLayout.centeredStart(9, quick.size());
        for (int i = 0; i < quick.size(); i++) {
            Amount amount = quick.get(i);
            Map<String, String> buttonPh = Map.of("amount", amount.label());
            inv.setItem(start + i, icon(cfg.chestIcon(amount.isAll() ? "deposit-all" : "deposit"),
                    amount.isAll() ? "gui.chest.deposit-all-name" : "gui.chest.deposit-name",
                    amount.isAll() ? "gui.chest.deposit-all-lore" : "gui.chest.deposit-lore",
                    buttonPh));
        }

        start = ChestLayout.centeredStart(18, quick.size());
        for (int i = 0; i < quick.size(); i++) {
            Amount amount = quick.get(i);
            Map<String, String> buttonPh = Map.of("amount", amount.label());
            inv.setItem(start + i, icon(cfg.chestIcon(amount.isAll() ? "withdraw-all" : "withdraw"),
                    amount.isAll() ? "gui.chest.withdraw-all-name" : "gui.chest.withdraw-name",
                    amount.isAll() ? "gui.chest.withdraw-all-lore" : "gui.chest.withdraw-lore",
                    buttonPh));
        }

        boolean interestOn = cfg.interestEnabled() && cfg.ratePercent() > 0;
        inv.setItem(ChestLayout.slotInterest(inv.getSize()), icon(cfg.chestIcon("interest"),
                interestOn ? "gui.chest.interest-name" : "gui.chest.interest-disabled-name",
                interestOn ? "gui.chest.interest-lore" : "gui.chest.interest-disabled-lore", ph));

        inv.setItem(ChestLayout.slotLogs(inv.getSize()), icon(cfg.chestIcon("logs"),
                "gui.chest.logs-name", "gui.chest.logs-lore", ph));

        if (cfg.guiPlayerChoice()) {
            inv.setItem(ChestLayout.slotStyle(inv.getSize()), icon(cfg.chestIcon("style"),
                    "gui.chest.style-name", "gui.chest.style-lore",
                    Map.of("style", plugin.bankMenu().resolve(player).label())));
        }

        inv.setItem(ChestLayout.slotClose(inv.getSize()), icon(cfg.chestIcon("close"),
                "gui.chest.close-name", null, Map.of()));
    }

    private void renderLogs(Player player, Inventory inv, int page) {
        Messages messages = plugin.messages();
        BankConfig cfg = plugin.bankConfig();
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());

        int perPage = ChestLayout.logsPerPage(inv.getSize());
        int pages = acc.logPages(perPage);
        page = Math.min(Math.max(1, page), pages);
        int finalPage = page;

        ItemStack filler = filler();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(ChestLayout.slotInfo(), icon(cfg.chestIcon("logs"),
                "gui.chest.logs-title-name", null, Map.of(
                        "page", String.valueOf(finalPage), "pages", String.valueOf(pages))));

        List<BankLogEntry> entries = acc.logPage(finalPage, perPage);
        if (entries.isEmpty()) {
            ItemStack empty = icon(cfg.chestIcon("log-entry"), null, null, Map.of());
            ItemMeta meta = empty.getItemMeta();
            if (meta != null) {
                meta.displayName(messages.gui("logs-empty", Map.of())
                        .decoration(TextDecoration.ITALIC, false));
                empty.setItemMeta(meta);
            }
            inv.setItem(ChestLayout.logsEntryStart(), empty);
        } else {
            int slot = ChestLayout.logsEntryStart();
            for (BankLogEntry entry : entries) {
                inv.setItem(slot++, icon(cfg.chestIcon("log-entry"),
                        "gui.chest.log-entry-name", "gui.chest.log-entry-lore", Map.of(
                                "time", TIME.format(Instant.ofEpochMilli(entry.time())),
                                "type", entry.type(),
                                "amount", plugin.vault().format(entry.amount()),
                                "note", entry.note())));
            }
        }

        inv.setItem(ChestLayout.slotPrevPage(inv.getSize()), icon(cfg.chestIcon("previous-page"),
                "gui.chest.page-previous-name", null, Map.of()));
        inv.setItem(ChestLayout.slotPageIndicator(inv.getSize()), icon(cfg.chestIcon("logs"),
                "gui.chest.page-indicator-name", null, Map.of(
                        "page", String.valueOf(finalPage), "pages", String.valueOf(pages))));
        inv.setItem(ChestLayout.slotNextPage(inv.getSize()), icon(cfg.chestIcon("next-page"),
                "gui.chest.page-next-name", null, Map.of()));
        inv.setItem(ChestLayout.slotBack(inv.getSize()), icon(cfg.chestIcon("close"),
                "gui.chest.back-name", null, Map.of()));
    }

    private void renderInterest(Player player, Inventory inv) {
        BankConfig cfg = plugin.bankConfig();
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());

        ItemStack filler = filler();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(ChestLayout.slotInfo(), icon(cfg.chestIcon("interest"),
                "gui.chest.interest-view-name", "gui.chest.interest-view-lore",
                commonPlaceholders(player, acc)));
        inv.setItem(ChestLayout.slotBack(inv.getSize()), icon(cfg.chestIcon("close"),
                "gui.chest.back-name", null, Map.of()));
    }

    // --- click mapping -------------------------------------------------------

    /** Quick-amount deposit button at this slot, or null. */
    public Amount depositAt(int slot) {
        return quickAt(slot, 9);
    }

    /** Quick-amount withdraw button at this slot, or null. */
    public Amount withdrawAt(int slot) {
        return quickAt(slot, 18);
    }

    private Amount quickAt(int slot, int rowFirst) {
        List<Amount> quick = plugin.bankConfig().quickAmounts();
        int start = ChestLayout.centeredStart(rowFirst, quick.size());
        int index = slot - start;
        if (index < 0 || index >= quick.size()) {
            return null;
        }
        return quick.get(index);
    }

    // --- helpers -------------------------------------------------------------

    private Map<String, String> commonPlaceholders(Player player, BankAccount acc) {
        BankConfig cfg = plugin.bankConfig();
        Map<String, String> ph = new HashMap<>();
        ph.put("player", player.getName());
        ph.put("bank", plugin.vault().format(acc.balance()));
        ph.put("wallet", plugin.vault().format(plugin.vault().wallet(player)));
        ph.put("rate", String.valueOf(cfg.ratePercent()));
        ph.put("interval", cfg.intervalDescription());
        ph.put("next", cfg.interestEnabled() ? plugin.interest().nextRunHuman() : "-");
        ph.put("min", plugin.vault().format(cfg.minTransaction()));
        ph.put("maxd", cfg.maxDeposit() > 0 ? plugin.vault().format(cfg.maxDeposit()) : "∞");
        ph.put("maxw", cfg.maxWithdraw() > 0 ? plugin.vault().format(cfg.maxWithdraw()) : "∞");
        ph.put("cap", cfg.maxBalance() > 0 ? plugin.vault().format(cfg.maxBalance()) : "∞");
        ph.put("minbal", plugin.vault().format(cfg.minBalance()));
        return ph;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(plugin.bankConfig().chestFiller());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Named + lored icon built from messages.yml MiniMessage entries. */
    private ItemStack icon(Material material, String nameKey, String loreKey, Map<String, String> ph) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (nameKey != null) {
                meta.displayName(plugin.messages().gui(nameKey, ph)
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (loreKey != null) {
                List<Component> lore = new ArrayList<>();
                for (Component line : plugin.messages().guiList(loreKey, ph)) {
                    lore.add(line.decoration(TextDecoration.ITALIC, false));
                }
                if (!lore.isEmpty()) {
                    meta.lore(lore);
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
