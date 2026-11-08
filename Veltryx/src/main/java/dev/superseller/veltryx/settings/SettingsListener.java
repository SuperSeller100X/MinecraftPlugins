package dev.superseller.veltryx.settings;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.superseller.veltryx.util.PlatformScheduler;

/** Applies each preference only to the player it belongs to. */
public final class SettingsListener implements Listener {
    private final PreferencesStore store;
    private final org.bukkit.NamespacedKey visionKey;
    public SettingsListener(PreferencesStore store, org.bukkit.plugin.Plugin plugin) {
        this.store = store;
        this.visionKey = new org.bukkit.NamespacedKey(plugin, "settings_night_vision");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNaturalSpawns(PlayerNaturallySpawnCreaturesEvent e) {
        if (store.get(e.getPlayer().getUniqueId()).mobsDisabled()) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhantom(PhantomPreSpawnEvent e) {
        if (e.getSpawningEntity() instanceof Player player) {
            PreferencesStore.Preferences p = store.get(player.getUniqueId());
            if (p.mobsDisabled() || p.phantomsDisabled()) e.setCancelled(true);
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        PlatformScheduler.runForPlayer(e.getPlayer(), () -> updateNightVision(e.getPlayer()));
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) {
        PlatformScheduler.runForPlayer(e.getPlayer(), () -> updateNightVision(e.getPlayer()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffectRemoved(EntityPotionEffectEvent e) {
        if (e.getEntity() instanceof Player player && e.getNewEffect() == null
                && PotionEffectType.NIGHT_VISION.equals(e.getModifiedType())
                && store.get(player.getUniqueId()).nightVision()) {
            PlatformScheduler.runForPlayer(player, () -> updateNightVision(player));
        }
    }
    /** Timer is a fallback for plugins that remove effects without the event. */
    public void startTimer() {
        PlatformScheduler.runTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlatformScheduler.runForPlayer(player, () -> updateNightVision(player));
            }
        }, 40L);
    }
    public void updateNightVision(Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        org.bukkit.persistence.PersistentDataContainer data = player.getPersistentDataContainer();
        boolean owned = data.has(visionKey, org.bukkit.persistence.PersistentDataType.BYTE);
        if (store.get(player.getUniqueId()).nightVision()) {
            // Never overwrite a potion or an effect belonging to another plugin.
            if (current == null) {
                if (player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                        PotionEffect.INFINITE_DURATION, 0, true, false, false))) {
                    data.set(visionKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                }
            } else if (owned && !isOurEffect(current)) {
                // Another plugin replaced our effect; it now owns the effect.
                data.remove(visionKey);
            }
        } else if (owned) {
            if (isOurEffect(current)) player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            data.remove(visionKey);
        }
    }
    private static boolean isOurEffect(PotionEffect effect) {
        return effect != null && effect.getDuration() == PotionEffect.INFINITE_DURATION
                && effect.getAmplifier() == 0 && effect.isAmbient() && !effect.hasParticles() && !effect.hasIcon();
    }
}
