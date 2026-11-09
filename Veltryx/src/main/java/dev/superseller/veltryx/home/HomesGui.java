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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The {@code /homes} GUI family:
 * <ul>
 *   <li><b>Overview</b> - every home as its icon. Click opens the home.</li>
 *   <li><b>Detail</b> - teleport, rename, change icon, delete one home.</li>
 *   <li><b>Icon picker</b> - pick any Minecraft block or item as the icon.</li>
 * </ul>
 * Renames are typed in chat after clicking the rename button.
 */
public final class HomesGui implements Listener {

    /** Marks the homes overview and remembers which slot holds which home. */
    public static final class OverviewHolder implements InventoryHolder {
        private final Map<Integer, String> slotNames = new LinkedHashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Marks the per-home detail screen. */
    public static final class DetailHolder implements InventoryHolder {
        private final String homeName;
        private Inventory inventory;

        DetailHolder(String homeName) {
            this.homeName = homeName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Marks the icon picker and remembers its page and home. */
    public static final class IconHolder implements InventoryHolder {
        private final String homeName;
        private final int page;
        private final List<Material> materials = new ArrayList<>();
        private Inventory inventory;

        IconHolder(String homeName, int page) {
            this.homeName = homeName;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Players currently typing a new home name in chat. */
    private final Map<UUID, String> pendingRenames = new ConcurrentHashMap<>();
    private java.util.function.Consumer<Player> cancelOtherPrompt = player -> { };

    public void attachPromptCancellation(java.util.function.Consumer<Player> cancel) { this.cancelOtherPrompt = cancel; }
    public void cancelPendingRename(Player player) {
        String name = pendingRenames.remove(player.getUniqueId());
        if (name != null) messages.send(player, "home.rename-cancelled", Map.of("name", name));
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
            OverviewHolder holder = new OverviewHolder();
            Inventory inventory = Bukkit.createInventory(holder, 54, Txt.legacy("§8§lYour homes"));
            holder.inventory = inventory;

            List<HomeLocation> homes = service.list(player.getUniqueId());
            int slot = 0;
            for (HomeLocation home : homes) {
                if (slot >= 45) {
                    break;
                }
                inventory.setItem(slot, item(iconOf(player, home), 1,
                        Txt.legacy("§b" + home.name()),
                        List.of(Txt.legacy("§7World: §f" + home.world()),
                                Txt.legacy(String.format(java.util.Locale.ROOT, "§7Position: §f%d, %d, %d",
                                        (int) home.x(), (int) home.y(), (int) home.z())),
                                Component.empty(),
                                Txt.legacy("§eClick to manage this home"))));
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
                            economy.getBalanceExact(player.getUniqueId()))), List.of()));
            inventory.setItem(51, item(Material.ENDER_EYE, 1,
                    Txt.legacy("§eCreate a home"),
                    List.of(Txt.legacy("§7Stand where you want it and run"),
                            Txt.legacy("§f/sethome <name>§7 — e.g. §f/sethome base"))));
            player.openInventory(inventory);
        });
    }

    /** Opens the per-home detail screen. */
    public void openDetail(Player player, String homeName) {
        PlatformScheduler.runForPlayer(player, () -> {
            HomeLocation home = service.find(player.getUniqueId(), homeName);
            if (home == null) {
                open(player);
                return;
            }
            DetailHolder holder = new DetailHolder(home.name());
            Inventory inventory = Bukkit.createInventory(holder, 27, Txt.legacy("§8§lHome: §b" + home.name()));
            holder.inventory = inventory;

            inventory.setItem(4, item(iconOf(player, home), 1,
                    Txt.legacy("§b" + home.name()),
                    List.of(Txt.legacy(String.format(java.util.Locale.ROOT, "§7Position: §f%d, %d, %d",
                            (int) home.x(), (int) home.y(), (int) home.z())))));
            inventory.setItem(10, item(Material.ENDER_PEARL, 1,
                    Txt.legacy("§aTeleport"),
                    List.of(Txt.legacy("§7Warp to this home."))));
            inventory.setItem(12, item(Material.WRITABLE_BOOK, 1,
                    Txt.legacy("§eRename"),
                    List.of(Txt.legacy("§7Type the new name in chat."))));
            inventory.setItem(14, item(Material.COMPASS, 1,
                    Txt.legacy("§eChange icon"),
                    List.of(Txt.legacy("§7Any block or item can be the icon."))));
            inventory.setItem(16, item(Material.BARRIER, 1,
                    Txt.legacy("§cDelete home"),
                    List.of(Txt.legacy("§7Removes this home."))));
            inventory.setItem(22, item(Material.ARROW, 1,
                    Txt.legacy("§7Back to your homes"), List.of()));
            player.openInventory(inventory);
        });
    }

    /** Opens the icon picker (any Minecraft block or item). */
    public void openIcons(Player player, String homeName, int page) {
        PlatformScheduler.runForPlayer(player, () -> {
            List<Material> all = new ArrayList<>();
            for (Material material : Material.values()) {
                if (material.isItem() && material != Material.AIR) {
                    all.add(material);
                }
            }
            int perPage = 45;
            int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
            int safePage = Math.max(0, Math.min(page, pages - 1));
            IconHolder holder = new IconHolder(homeName, safePage);
            Inventory inventory = Bukkit.createInventory(holder, 54,
                    Txt.legacy("§8§lIcon for §b" + homeName + " §7(" + (safePage + 1) + "/" + pages + ")"));
            holder.inventory = inventory;

            int from = safePage * perPage;
            for (int i = 0; i < perPage && from + i < all.size(); i++) {
                Material material = all.get(from + i);
                holder.materials.add(material);
                inventory.setItem(i, item(material, 1,
                        Txt.legacy("§f" + material.name()),
                        List.of(Txt.legacy("§eClick to use this icon"))));
            }
            if (safePage > 0) {
                inventory.setItem(45, item(Material.ARROW, 1, Txt.legacy("§ePrevious page"), List.of()));
            }
            inventory.setItem(48, item(Material.LIME_DYE, 1,
                    Txt.legacy("§aDefault icon"), List.of(Txt.legacy("§7Reset to the standard icon."))));
            inventory.setItem(49, item(Material.BARRIER, 1, Txt.legacy("§cBack"), List.of()));
            if (safePage < pages - 1) {
                inventory.setItem(53, item(Material.ARROW, 1, Txt.legacy("§eNext page"), List.of()));
            }
            player.openInventory(inventory);
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holder = event.getInventory().getHolder();
        if (holder instanceof OverviewHolder overview) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != overview) {
                return;
            }
            String homeName = overview.slotNames.get(event.getSlot());
            if (homeName == null) {
                return;
            }
            openDetail(player, homeName);
            return;
        }
        if (holder instanceof DetailHolder detail) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != detail) {
                return;
            }
            handleDetail(player, detail.homeName, event.getSlot());
            return;
        }
        if (holder instanceof IconHolder icons) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != icons) {
                return;
            }
            handleIcons(player, icons, event.getSlot());
        }
    }

    private void handleDetail(Player player, String homeName, int slot) {
        switch (slot) {
            case 10 -> {
                HomeService.Result result = service.teleport(player, homeName);
                messages.send(player, result.templateKey(), result.values());
                if (result.ok()) {
                    player.closeInventory();
                }
            }
            case 12 -> PlatformScheduler.runForPlayer(player, () -> {
                cancelOtherPrompt.accept(player);
                player.closeInventory();
                pendingRenames.put(player.getUniqueId(), homeName);
                messages.send(player, "home.rename-prompt", Map.of("name", homeName));
            });
            case 14 -> openIcons(player, homeName, 0);
            case 16 -> {
                HomeService.Result result = service.delete(player, homeName);
                messages.send(player, result.templateKey(), result.values());
                open(player);
            }
            case 22 -> open(player);
            default -> { }
        }
    }

    private void handleIcons(Player player, IconHolder icons, int slot) {
        if (slot >= 0 && slot < icons.materials.size()) {
            HomeService.Result result = service.setIcon(player, icons.homeName, icons.materials.get(slot).name());
            messages.send(player, result.templateKey(), result.values());
            openDetail(player, icons.homeName);
            return;
        }
        if (slot == 45) {
            openIcons(player, icons.homeName, icons.page - 1);
        } else if (slot == 48) {
            service.setIcon(player, icons.homeName, null);
            messages.send(player, "home.icon-set", Map.of("name", icons.homeName, "icon", "default"));
            openDetail(player, icons.homeName);
        } else if (slot == 49) {
            openDetail(player, icons.homeName);
        } else if (slot == 53) {
            openIcons(player, icons.homeName, icons.page + 1);
        }
    }

    /** Captures the typed new home name for a pending rename. */
    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        String pending = pendingRenames.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        pendingRenames.remove(player.getUniqueId());
        String typed = event.getMessage().trim();
        if (typed.equalsIgnoreCase("cancel")) {
            messages.send(player, "home.rename-cancelled", Map.of("name", pending));
            return;
        }
        HomeService.Result result = service.rename(player, pending, typed);
        messages.send(player, result.templateKey(), result.values());
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        pendingRenames.remove(event.getPlayer().getUniqueId());
    }

    /** The home's custom icon material, or the default ender pearl. */
    private Material iconOf(Player player, HomeLocation home) {
        String icon = service.icon(player.getUniqueId(), home.name());
        Material material = icon == null ? null : Material.matchMaterial(icon);
        return material == null ? Material.ENDER_PEARL : material;
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
