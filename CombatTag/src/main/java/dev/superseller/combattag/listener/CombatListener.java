package dev.superseller.combattag.listener;

import dev.superseller.combattag.combat.CombatCause;
import dev.superseller.combattag.combat.CombatManager;
import dev.superseller.combattag.config.PluginConfig;
import java.util.UUID;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Applies combat tags whenever a player damages, or is damaged by, another player. */
public final class CombatListener implements Listener {

    private final PluginConfig config;
    private final CombatManager combat;

    public CombatListener(PluginConfig config, CombatManager combat) {
        this.config = config;
        this.combat = combat;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Entity damager = event.getDamager();

        Player attacker = null;
        CombatCause cause = CombatCause.MELEE;

        if (damager instanceof Player direct) {
            attacker = direct;
        } else if (damager instanceof Projectile projectile) {
            if (!config.isTagOnProjectile()) {
                return;
            }
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player shooterPlayer) {
                attacker = shooterPlayer;
                cause = CombatCause.PROJECTILE;
            }
        } else if (damager instanceof Tameable tameable && config.isTagOnPetDamage()) {
            if (tameable.getOwner() instanceof Player owner) {
                attacker = owner;
                cause = CombatCause.PET;
            }
        }

        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        applyPair(victim, attacker, cause);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!config.isTagOnSplashPotion()) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player thrower)) {
            return;
        }
        for (org.bukkit.entity.LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player victim && !victim.getUniqueId().equals(thrower.getUniqueId())) {
                if (event.getIntensity(affected) > 0.0D) {
                    applyPair(victim, thrower, CombatCause.POTION);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLingering(AreaEffectCloudApplyEvent event) {
        if (!config.isTagOnSplashPotion()) {
            return;
        }
        AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player thrower)) {
            return;
        }
        for (org.bukkit.entity.LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player victim && !victim.getUniqueId().equals(thrower.getUniqueId())) {
                applyPair(victim, thrower, CombatCause.POTION);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (config.isClearTagOnDeath()) {
            UUID id = event.getEntity().getUniqueId();
            combat.untag(id);
        }
    }

    private void applyPair(Player victim, Player attacker, CombatCause cause) {
        if (config.isTagVictim()) {
            combat.tag(victim, attacker.getUniqueId(), cause);
        }
        if (config.isTagAttacker()) {
            combat.tag(attacker, victim.getUniqueId(), cause);
        }
    }
}
