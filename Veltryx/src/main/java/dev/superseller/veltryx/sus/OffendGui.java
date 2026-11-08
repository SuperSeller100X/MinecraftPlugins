package dev.superseller.veltryx.sus;

import dev.superseller.veltryx.util.Txt;
import dev.superseller.veltryx.anticheat.AntiCheatService;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.model.BanEntry;
import dev.superseller.veltryx.model.FlagEntry;
import dev.superseller.veltryx.punish.BanService;
import dev.superseller.veltryx.util.PlatformScheduler;
import dev.superseller.veltryx.util.TimeUtil;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
 * The offend ban menu: opened from the {@code /sus} detail view or
 * {@code /offend <player>}. Lists every configured ban reason (category and
 * duration shown) and applies the custom ban with one more click.
 */
public final class OffendGui implements Listener {

    /** Marks the offend inventory and remembers the ban target. */
    public static final class Holder implements InventoryHolder {
        private final UUID target;
        private final String name;
        private Inventory inventory;

        Holder(UUID target, String name) {
            this.target = target;
            this.name = name;
        }

        public UUID target() {
            return target;
        }

        public String name() {
            return name;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final VeltryxConfig config;
    private final Messages messages;
    private final BanService bans;
    private final AntiCheatService anticheat;
    private SusGui susGui;

    public OffendGui(VeltryxConfig config, Messages messages, BanService bans,
                     AntiCheatService anticheat) {
        this.config = config;
        this.messages = messages;
        this.bans = bans;
        this.anticheat = anticheat;
    }

    /** Wires the back button to the suspect detail view. */
    public void attach(SusGui susGui) {
        this.susGui = susGui;
    }

    /** Opens the ban reason menu for a target (Folia-safe). */
    public void open(Player staff, UUID target, String name) {
        PlatformScheduler.runForPlayer(staff, () -> {
            List<VeltryxConfig.OffendReason> reasons = config.offendReasons();
            int rows = Math.max(2, Math.min(6, (reasons.size() + 8) / 9 + 1));
            Holder holder = new Holder(target, name);
            Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                    Txt.legacy("§8§lOFFEND — " + name));
            holder.inventory = inventory;

            int capacity = (rows - 1) * 9;
            for (int i = 0; i < reasons.size() && i < capacity; i++) {
                inventory.setItem(i, reasonItem(reasons.get(i), name));
            }
            inventory.setItem(rows * 9 - 1, item(Material.ARROW, Txt.legacy("§7Back"),
                    List.of(Txt.legacy("§7Back to the suspect view"))));
            staff.openInventory(inventory);
        });
    }

    private static ItemStack reasonItem(VeltryxConfig.OffendReason reason, String targetName) {
        String duration = reason.durationSeconds() == Long.MAX_VALUE
                ? "permanent" : TimeUtil.format(reason.durationSeconds());
        return item(materialFor(reason.category()),
                Txt.legacy("§c" + reason.display()),
                List.of(Txt.legacy("§7Category: §f" + reason.category()),
                        Txt.legacy("§7Duration: §f" + duration),
                        Component.text(""),
                        Txt.legacy("§eClick to ban §f" + targetName)));
    }

    private static Material materialFor(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "xray-esp" -> Material.DIAMOND_ORE;
            case "hacking" -> Material.IRON_SWORD;
            default -> Material.NETHER_STAR;
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player staff)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) {
            return;
        }

        List<VeltryxConfig.OffendReason> reasons = config.offendReasons();
        int slot = event.getSlot();
        if (slot == holder.getInventory().getSize() - 1) {
            if (susGui != null) {
                susGui.openDetail(staff, holder.target());
            } else {
                staff.closeInventory();
            }
            return;
        }
        if (slot >= 0 && slot < reasons.size()) {
            apply(staff, holder.target(), holder.name(), reasons.get(slot));
        }
    }

    private void apply(Player staff, UUID target, String name, VeltryxConfig.OffendReason reason) {
        if (!staff.hasPermission("veltryx.offend")) {
            messages.send(staff, "general.no-permission");
            return;
        }
        StringBuilder evidence = new StringBuilder();
        for (FlagEntry flag : anticheat.storedFlags(target, 8)) {
            if (evidence.length() > 0) {
                evidence.append(" | ");
            }
            evidence.append(flag.check()).append(' ')
                    .append(String.format(Locale.ROOT, "%.1f", flag.vl()))
                    .append(" (").append(flag.detail()).append(')');
        }
        long expires = TimeUtil.expiryMillis(System.currentTimeMillis(), reason.durationSeconds());
        BanEntry entry = bans.ban(target, name, reason.category(), reason.display(),
                evidence.toString(), staff.getName(), expires, false);

        String duration = reason.durationSeconds() == Long.MAX_VALUE
                ? "permanent" : TimeUtil.format(reason.durationSeconds());
        messages.send(staff, "offend.done", Map.of(
                "player", name, "reason", reason.display(),
                "duration", duration, "id", entry.id()));

        Component staffMessage = messages.render("offend.staff-note", Map.of(
                "staff", staff.getName(), "player", name,
                "reason", reason.display(), "duration", duration, "id", entry.id()));
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(staff.getUniqueId())
                    && other.hasPermission("veltryx.anticheat.alerts")) {
                other.sendMessage(staffMessage);
            }
        }
        Bukkit.getConsoleSender().sendMessage(staffMessage);
        staff.closeInventory();
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
