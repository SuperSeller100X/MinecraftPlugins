package dev.superseller.veltryx.sus;

import dev.superseller.veltryx.anticheat.AntiCheatService;
import dev.superseller.veltryx.anticheat.CheckType;
import dev.superseller.veltryx.anticheat.PlayerTrace;
import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.model.BanEntry;
import dev.superseller.veltryx.model.FlagEntry;
import dev.superseller.veltryx.punish.BanService;
import dev.superseller.veltryx.staff.FreezeService;
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
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The {@code /sus} investigation GUI.
 *
 * <p>Main view: every online player as a head, sorted by suspicion score
 * (violation levels + recent flags). Detail view: the player's per-check
 * violation levels, recent flags and one-click staff actions (teleport,
 * freeze, kick, offend-xray, offend-hacking).
 */
public final class SusGui implements Listener {

    /** Marks Veltryx inventories and remembers the clicked target. */
    public static final class Holder implements InventoryHolder {
        private final boolean detailView;
        private final UUID target;
        private Inventory inventory;

        Holder(boolean detailView, UUID target) {
            this.detailView = detailView;
            this.target = target;
        }

        public boolean detailView() {
            return detailView;
        }

        public UUID target() {
            return target;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final AntiCheatService anticheat;
    private final BanService bans;
    private final FreezeService freeze;
    private final VeltryxConfig config;
    private final Messages messages;

    public SusGui(AntiCheatService anticheat, BanService bans, FreezeService freeze,
                  VeltryxConfig config, Messages messages) {
        this.anticheat = anticheat;
        this.bans = bans;
        this.freeze = freeze;
        this.config = config;
        this.messages = messages;
    }

    /** Opens the suspicion overview for a staff member (Folia-safe). */
    public void openList(Player staff) {
        PlatformScheduler.runForPlayer(staff, () -> {
            List<PlayerTrace> ranked = anticheat.ranked();
            int rows = Math.max(1, Math.min(6, (ranked.size() + 8) / 9));
            Holder holder = new Holder(false, null);
            Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                    Component.text("§8§lSUS — flagged players"));
            holder.inventory = inventory;

            for (int i = 0; i < ranked.size() && i < rows * 9; i++) {
                PlayerTrace trace = ranked.get(i);
                Player online = Bukkit.getPlayer(trace.uuid());
                if (online == null) {
                    continue;
                }
                ItemStack head = head(online, Component.text("§f" + online.getName()),
                        List.of(Component.text("§7Suspicion: §c" + format(anticheat.suspicion(trace))),
                                Component.text("§7Ping: §f" + online.getPing()),
                                Component.text("§7Flags: §f" + trace.recentFlags().size()),
                                Component.text(""),
                                Component.text("§eClick to inspect")));
                inventory.setItem(i, head);
            }
            if (ranked.isEmpty()) {
                inventory.setItem(4, item(Material.LIME_DYE, Component.text("§aNo suspicious players"),
                        List.of(Component.text("§7Nobody is currently flagged."))));
            }
            staff.openInventory(inventory);
        });
    }

    /** Opens the detail view for one player (Folia-safe). */
    public void openDetail(Player staff, UUID target) {
        PlatformScheduler.runForPlayer(staff, () -> {
            Player online = Bukkit.getPlayer(target);
            String name = online != null ? online.getName() : "unknown";
            Holder holder = new Holder(true, target);
            Inventory inventory = Bukkit.createInventory(holder, 54,
                    Component.text("§8§lSUS — " + name));
            holder.inventory = inventory;

            PlayerTrace trace = anticheat.traceIfPresent(target);
            double suspicion = trace == null ? 0.0 : anticheat.suspicion(trace);

            inventory.setItem(4, head(target, Component.text("§f" + name),
                    List.of(Component.text("§7Suspicion: §c" + format(suspicion)),
                            Component.text("§7Ping: §f" + (online != null ? online.getPing() : -1)),
                            Component.text("§7Online: §f" + (online != null ? "yes" : "no")))));

            // Per-check violation levels.
            int slot = 18;
            if (trace != null) {
                for (Map.Entry<CheckType, Double> entry : trace.violationLevels().entrySet()) {
                    if (slot >= 27) {
                        break;
                    }
                    inventory.setItem(slot++, item(Material.PAPER,
                            Component.text("§c" + entry.getKey().display()),
                            List.of(Component.text("§7Violation level: §f" + format(entry.getValue())))));
                }
            }

            // Recent flags.
            slot = 27;
            List<FlagEntry> flags = anticheat.storedFlags(target, 8);
            for (FlagEntry flag : flags) {
                if (slot >= 36) {
                    break;
                }
                inventory.setItem(slot++, item(Material.REDSTONE,
                        Component.text("§e" + flag.check()),
                        List.of(Component.text("§7VL: §f" + format(flag.vl())),
                                Component.text("§7Detail: §f" + flag.detail()),
                                Component.text("§7When: §f" + TimeUtil.formatTimestamp(flag.issued())))));
            }

            // Staff actions.
            inventory.setItem(45, item(Material.COMPASS, Component.text("§bTeleport to player"),
                    List.of(Component.text("§7Jump to their location"))));
            inventory.setItem(46, item(Material.PACKED_ICE, Component.text("§bFreeze / unfreeze"),
                    List.of(Component.text("§7Lock them in place"))));
            inventory.setItem(47, item(Material.IRON_DOOR, Component.text("§cKick"),
                    List.of(Component.text("§7Disconnect the player"))));
            inventory.setItem(51, item(Material.DIAMOND_ORE, Component.text("§cOffend: X-Ray / ESP"),
                    List.of(Component.text("§7Ban for §f" + TimeUtil.format(30L * 86_400L)))));
            inventory.setItem(52, item(Material.IRON_SWORD, Component.text("§cOffend: Hacking"),
                    List.of(Component.text("§7Permanent ban"))));
            inventory.setItem(53, item(Material.ARROW, Component.text("§7Back to list"), List.of()));
            staff.openInventory(inventory);
        });
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

        if (!holder.detailView()) {
            int slot = event.getSlot();
            List<PlayerTrace> ranked = anticheat.ranked();
            if (slot < ranked.size()) {
                openDetail(staff, ranked.get(slot).uuid());
            }
            return;
        }

        UUID target = holder.target();
        Player online = Bukkit.getPlayer(target);
        switch (event.getSlot()) {
            case 45 -> {
                if (online != null && staff.hasPermission("veltryx.sus")) {
                    PlatformScheduler.runForPlayer(staff, () -> staff.teleportAsync(online.getLocation()));
                    staff.closeInventory();
                }
            }
            case 46 -> {
                if (online != null && staff.hasPermission("veltryx.moderation.freeze")) {
                    boolean frozen = freeze.toggle(online);
                    messages.send(staff, frozen ? "freeze.frozen" : "freeze.unfrozen",
                            Map.of("player", online.getName()));
                }
            }
            case 47 -> {
                if (online != null && staff.hasPermission("veltryx.offend")) {
                    online.kick(messages.render("sus.kicked",
                            Map.of("staff", staff.getName())));
                    messages.send(staff, "sus.kick-done", Map.of("player", online.getName()));
                    staff.closeInventory();
                }
            }
            case 51 -> offend(staff, online, target, "xray");
            case 52 -> offend(staff, online, target, "hacking");
            case 53 -> openList(staff);
            default -> {
            }
        }
    }

    private void offend(Player staff, Player online, UUID target, String reasonKey) {
        if (!staff.hasPermission("veltryx.offend")) {
            messages.send(staff, "general.no-permission");
            return;
        }
        config.resolveOffendReason(reasonKey).ifPresent(reason -> {
            String name = online != null ? online.getName() : reasonKey;
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
            staff.closeInventory();
        });
    }

    // -------------------------------------------------------------- items

    private static ItemStack head(UUID owner, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            Player online = Bukkit.getPlayer(owner);
            if (online != null) {
                skull.setOwningPlayer(online);
            }
            skull.displayName(name);
            skull.lore(lore);
            item.setItemMeta(skull);
        }
        return item;
    }

    private static ItemStack head(Player owner, Component name, List<Component> lore) {
        return head(owner.getUniqueId(), name, lore);
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

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
