package dev.superseller.xpbank.gui;

import dev.superseller.xpbank.bank.BankService;
import dev.superseller.xpbank.config.Messages;
import dev.superseller.xpbank.config.XPBankConfig;
import dev.superseller.xpbank.util.ExperienceUtil;
import dev.superseller.xpbank.util.Numbers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds and opens the XP Bank menu. Layout is a configurable row count; the
 * bottom row holds a balance display flanked by deposit and withdraw buttons.
 * Every button click resolves to a plain deposit/withdraw of a preset amount
 * (10 / 100 / all) so the whole flow works without chat input, which keeps it
 * fully Folia-safe.
 */
public final class BankGui {

    private final XPBankConfig config;
    private final Messages messages;
    private final BankService bank;

    // Slot layout constants (for a 3-row / 27-slot inventory by default).
    public static final int SLOT_BALANCE = 13;
    public static final int SLOT_DEPOSIT_10 = 10;
    public static final int SLOT_DEPOSIT_100 = 11;
    public static final int SLOT_DEPOSIT_ALL = 12;
    public static final int SLOT_WITHDRAW_10 = 16;
    public static final int SLOT_WITHDRAW_100 = 15;
    public static final int SLOT_WITHDRAW_ALL = 14;

    public BankGui(XPBankConfig config, Messages messages, BankService bank) {
        this.config = config;
        this.messages = messages;
        this.bank = bank;
    }

    public void open(Player player) {
        int size = config.guiRows() * 9;
        BankHolder holder = new BankHolder();
        Component title = messages.deserialize(config.guiTitle());
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);
        render(player, inv);
        player.openInventory(inv);
    }

    /** Repaints the menu contents for the given viewer. */
    public void render(Player player, Inventory inv) {
        int size = inv.getSize();
        ItemStack filler = simple(config.guiFiller(), " ", List.of());
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        long banked = bank.getBalance(player.getUniqueId());
        long onHand = ExperienceUtil.getPlayerExp(player);

        inv.setItem(SLOT_BALANCE, icon(config.guiBalanceIcon(), "gui.balance-name",
                "gui.balance-lore", Map.of(
                        "banked", Numbers.grouped(banked),
                        "banked_short", Numbers.compact(banked),
                        "onhand", Numbers.grouped(onHand),
                        "onhand_short", Numbers.compact(onHand))));

        inv.setItem(SLOT_DEPOSIT_10, icon(config.guiDepositIcon(), "gui.deposit-name",
                "gui.deposit-lore", Map.of("amount", "10")));
        inv.setItem(SLOT_DEPOSIT_100, icon(config.guiDepositIcon(), "gui.deposit-name",
                "gui.deposit-lore", Map.of("amount", "100")));
        inv.setItem(SLOT_DEPOSIT_ALL, icon(config.guiDepositIcon(), "gui.deposit-all-name",
                "gui.deposit-all-lore", Map.of()));

        inv.setItem(SLOT_WITHDRAW_10, icon(config.guiWithdrawIcon(), "gui.withdraw-name",
                "gui.withdraw-lore", Map.of("amount", "10")));
        inv.setItem(SLOT_WITHDRAW_100, icon(config.guiWithdrawIcon(), "gui.withdraw-name",
                "gui.withdraw-lore", Map.of("amount", "100")));
        inv.setItem(SLOT_WITHDRAW_ALL, icon(config.guiWithdrawIcon(), "gui.withdraw-all-name",
                "gui.withdraw-all-lore", Map.of()));
    }

    /** Maps a clicked slot to the amount sentinel to act on, or null. */
    public Long depositAmountFor(int slot) {
        return switch (slot) {
            case SLOT_DEPOSIT_10 -> 10L;
            case SLOT_DEPOSIT_100 -> 100L;
            case SLOT_DEPOSIT_ALL -> Numbers.ALL;
            default -> null;
        };
    }

    public Long withdrawAmountFor(int slot) {
        return switch (slot) {
            case SLOT_WITHDRAW_10 -> 10L;
            case SLOT_WITHDRAW_100 -> 100L;
            case SLOT_WITHDRAW_ALL -> Numbers.ALL;
            default -> null;
        };
    }

    private ItemStack icon(Material material, String nameKey, String loreKey, Map<String, String> ph) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.componentNoPrefix(nameKey, ph)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines(loreKey, ph)) {
                lore.add(messages.deserialize(line, ph)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack simple(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> loreLines(String loreKey, Map<String, String> ph) {
        return messages.stringList(loreKey);
    }
}
