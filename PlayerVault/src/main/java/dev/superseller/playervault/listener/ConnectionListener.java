package dev.superseller.playervault.listener;

import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import dev.superseller.playervault.config.Settings;
import dev.superseller.playervault.gui.GuiListener;
import dev.superseller.playervault.gui.VaultGui;
import dev.superseller.playervault.gui.VaultHolder;
import dev.superseller.playervault.model.VaultData;
import dev.superseller.playervault.scheduler.PlatformScheduler;
import dev.superseller.playervault.service.VaultService;

/**
 * Keeps the cache in step with players coming and going.
 *
 * <p>Joining starts an asynchronous load so the vault is already in memory by the
 * time the player runs {@code /pv}, and leaving writes the vault back and drops it
 * from the cache a little later — long enough that an immediate reconnect reuses the
 * cached copy instead of racing a pending write.
 */
public final class ConnectionListener implements Listener {

    /** Ticks to wait before evicting a disconnected player's vault from the cache. */
    private static final long EVICT_DELAY_TICKS = 200L;

    private final VaultService service;
    private final GuiListener guiListener;
    private final PlatformScheduler scheduler;
    private final Supplier<Settings> settings;

    public ConnectionListener(VaultService service, GuiListener guiListener, PlatformScheduler scheduler,
                              Supplier<Settings> settings) {
        this.service = service;
        this.guiListener = guiListener;
        this.scheduler = scheduler;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.preload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID owner = player.getUniqueId();

        // A vault left open must be read back before it is written, otherwise the
        // items the player moved during their last seconds would be lost.
        Inventory open = player.getOpenInventory().getTopInventory();
        VaultHolder holder = VaultGui.holder(open);
        if (holder != null) {
            guiListener.collect(holder, open);
        }

        if (settings.get().storage().saveOnQuit()) {
            VaultData data = service.vaultById(owner);
            service.saveAsync(data);
        }
        scheduler.runGlobalLater(() -> {
            // Only evict if they are still gone. Evicting a player who reconnected
            // inside the delay would drop the live cache entry while the save above
            // is still in flight, and a second VaultData could then be loaded.
            if (Bukkit.getPlayer(owner) == null) {
                service.forget(owner);
            }
        }, EVICT_DELAY_TICKS);
    }
}
