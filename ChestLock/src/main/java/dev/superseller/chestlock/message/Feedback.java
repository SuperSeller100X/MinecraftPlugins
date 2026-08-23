package dev.superseller.chestlock.message;

import dev.superseller.chestlock.ChestLockPlugin;
import dev.superseller.chestlock.model.PlayerSettings;
import dev.superseller.chestlock.storage.PlayerSettingsStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Folia-safe player feedback honoring each player's personal visual/audio settings. */
public final class Feedback {
    private final ChestLockPlugin plugin;
    private final Messages messages;
    private final PlayerSettingsStore settingsStore;
    private final Map<UUID, Long> lastLockedNotice = new ConcurrentHashMap<>();

    public Feedback(ChestLockPlugin plugin, Messages messages, PlayerSettingsStore settingsStore) {
        this.plugin = plugin;
        this.messages = messages;
        this.settingsStore = settingsStore;
    }

    public void message(Player player, String key) {
        message(player, key, Map.of());
    }

    public void message(Player player, String key, Map<String, String> values) {
        onPlayer(player, () -> messages.send(player, key, values));
    }

    public void success(Player player, String key, Map<String, String> values) {
        onPlayer(player, () -> {
            PlayerSettings settings = settingsStore.get(player.getUniqueId());
            var component = messages.prefixed(key, values);
            player.sendMessage(component);
            if (settings.actionBar()) {
                player.sendActionBar(messages.render(key, values));
            }
            if (settings.sounds()) {
                player.playSound(player.getLocation(), plugin.runtimeConfig().successSound(), 0.8f, 1.15f);
            }
            if (settings.particles()) {
                Location location = player.getLocation().add(0, 1, 0);
                player.spawnParticle(plugin.runtimeConfig().successParticle(), location, 8, 0.35, 0.4, 0.35, 0.01);
            }
        });
    }

    public void failure(Player player, String key, Map<String, String> values) {
        onPlayer(player, () -> {
            messages.send(player, key, values);
            if (settingsStore.get(player.getUniqueId()).sounds()) {
                player.playSound(player.getLocation(), plugin.runtimeConfig().failureSound(), 0.7f, 0.85f);
            }
        });
    }

    public void locked(Player player) {
        long now = System.currentTimeMillis();
        Long previous = lastLockedNotice.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 900) {
            return;
        }
        onPlayer(player, () -> {
            messages.send(player, "locked-container");
            PlayerSettings settings = settingsStore.get(player.getUniqueId());
            if (settings.actionBar()) {
                player.sendActionBar(messages.render("locked-container"));
            }
            if (settings.sounds()) {
                player.playSound(player.getLocation(), plugin.runtimeConfig().lockedSound(), 0.8f, 1.0f);
            }
        });
    }

    public void clearPlayer(UUID playerId) {
        lastLockedNotice.remove(playerId);
    }

    private void onPlayer(Player player, Runnable operation) {
        player.getScheduler().run(plugin, task -> operation.run(), null);
    }
}
