package dev.superseller.combattag.listener;

import dev.superseller.combattag.combat.BlockedAction;
import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.combat.RestrictionService;
import dev.superseller.combattag.config.PluginConfig;
import dev.superseller.combattag.gui.CombatTagHolder;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Blocks shop GUIs (EconomyShopGUI and friends) and the EasyMending repair GUI from opening
 * while the viewer is combat tagged. Detection works both by inventory title and by the
 * inventory holder's class name, so no hard dependency on those plugins is required.
 */
public final class InventoryListener implements Listener {

    private final PluginConfig config;
    private final CombatManager combat;
    private final RestrictionService restrictions;

    public InventoryListener(PluginConfig config, CombatManager combat, RestrictionService restrictions) {
        this.config = config;
        this.combat = combat;
        this.restrictions = restrictions;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!combat.isTagged(player.getUniqueId())) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof CombatTagHolder) {
            return; // Our own status GUI is always allowed.
        }

        String title = plainTitle(event);
        String holderClass = holder == null ? "" : holder.getClass().getName().toLowerCase(Locale.ROOT);

        if (config.isBlockEasyMending()
                && matchesClass(holderClass, config.getEasyMendingHolderClasses())
                && restrictions.denyIfBlocked(player, BlockedAction.EASYMENDING, "GUI")) {
            event.setCancelled(true);
            return;
        }
        if (config.isBlockShops()
                && (matchesClass(holderClass, config.getShopHolderClasses())
                    || matchesTitle(title, config.getShopInventoryTitles()))
                && restrictions.denyIfBlocked(player, BlockedAction.SHOP, title)) {
            event.setCancelled(true);
        }
    }

    private static String plainTitle(InventoryOpenEvent event) {
        try {
            return PlainTextComponentSerializer.plainText()
                    .serialize(event.getView().title())
                    .toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean matchesClass(String holderClass, Set<String> configured) {
        if (holderClass.isEmpty()) {
            return false;
        }
        for (String entry : configured) {
            if (!entry.isBlank() && holderClass.contains(entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesTitle(String title, Set<String> configured) {
        if (title.isEmpty()) {
            return false;
        }
        for (String entry : configured) {
            if (!entry.isBlank() && title.contains(entry)) {
                return true;
            }
        }
        return false;
    }
}
