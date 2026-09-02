package dev.superseller.combattag.listener;

import dev.superseller.combattag.combat.BlockedAction;
import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.combat.RestrictionService;
import dev.superseller.combattag.config.PluginConfig;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Blocks EasyMending's optional "sneak + right-click to quick repair" interaction while
 * the player is combat tagged. Runs at LOWEST priority so EasyMending never sees the event.
 */
public final class EasyMendingListener implements Listener {

    private final PluginConfig config;
    private final CombatManager combat;
    private final RestrictionService restrictions;

    public EasyMendingListener(PluginConfig config, CombatManager combat, RestrictionService restrictions) {
        this.config = config;
        this.combat = combat;
        this.restrictions = restrictions;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSneakRepair(PlayerInteractEvent event) {
        if (!config.isBlockEasyMending()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || !event.getAction().isRightClick()) {
            return;
        }
        if (!combat.isTagged(player.getUniqueId())) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isRepairable(item)) {
            return;
        }
        if (restrictions.denyIfBlocked(player, BlockedAction.EASYMENDING, "quick-repair")) {
            event.setCancelled(true);
        }
    }

    private static boolean isRepairable(ItemStack item) {
        if (item == null || item.getType().getMaxDurability() <= 0) {
            return false;
        }
        if (!(item.getItemMeta() instanceof Damageable damageable) || !damageable.hasDamage()) {
            return false;
        }
        try {
            return item.containsEnchantment(Enchantment.MENDING);
        } catch (Throwable ignored) {
            return true;
        }
    }
}
