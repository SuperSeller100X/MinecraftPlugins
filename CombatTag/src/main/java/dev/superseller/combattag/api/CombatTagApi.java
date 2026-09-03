package dev.superseller.combattag.api;

import dev.superseller.combattag.combat.CombatManager;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Small, stable façade other plugins can use:
 * {@code CombatTagApi api = CombatTagPlugin.getInstance().getApi();}
 */
public final class CombatTagApi {

    private final CombatManager combat;

    public CombatTagApi(CombatManager combat) {
        this.combat = combat;
    }

    /** @return true when the player is currently combat tagged. */
    public boolean isInCombat(Player player) {
        return combat.isTagged(player);
    }

    /** @return true when the given UUID is currently combat tagged. */
    public boolean isInCombat(UUID uuid) {
        return combat.isTagged(uuid);
    }

    /** @return remaining combat seconds, 0 when not tagged. */
    public int getRemainingSeconds(UUID uuid) {
        return combat.remainingSeconds(uuid);
    }

    /** Tags a player for the given number of seconds. */
    public void tag(Player player, int seconds) {
        combat.tagFor(player, null, seconds);
    }

    /** Removes a player's tag. @return true when a tag was removed. */
    public boolean untag(UUID uuid) {
        return combat.untag(uuid);
    }

    /** @return number of players currently tagged. */
    public int getActiveCount() {
        return combat.getActiveCount();
    }
}
