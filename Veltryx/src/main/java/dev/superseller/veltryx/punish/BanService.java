package dev.superseller.veltryx.punish;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.model.BanEntry;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Ids;
import dev.superseller.veltryx.util.TimeUtil;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * The custom ban system behind {@code /offend}, {@code /unban} and the
 * anti-cheat's automatic bans.
 *
 * <p>Bans live in Veltryx storage (not the vanilla ban list) so every ban has
 * a category (e.g. {@code xray-esp}, {@code hacking}), a short ban id shown on
 * the custom ban screen, an expiry (or permanent), the issuing staff member
 * and the anti-cheat flags that justified it.
 */
public final class BanService implements Listener {

    private final DataStore store;
    private final Messages messages;
    private final VeltryxConfig config;

    public BanService(DataStore store, Messages messages, VeltryxConfig config) {
        this.store = store;
        this.messages = messages;
        this.config = config;
    }

    /** Issues a ban and enforces it immediately when the player is online. */
    public BanEntry ban(UUID uuid, String name, String category, String reason, String details,
                        String staff, long expiresMillis, boolean auto) {
        BanEntry entry = new BanEntry(Ids.next(), uuid, name, category, reason, details,
                staff, System.currentTimeMillis(), expiresMillis, true, auto);
        store.saveBan(entry);
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && online.getUniqueId().equals(uuid)) {
            kickWithBanScreen(online, entry);
        }
        return entry;
    }

    /** Lifts an active ban by ban id or (most recent) player name. */
    public Optional<BanEntry> unban(String idOrName) {
        long now = System.currentTimeMillis();
        for (BanEntry entry : store.allBans()) {
            if (entry.id().equalsIgnoreCase(idOrName) && entry.isActiveAt(now)) {
                store.setBanActive(entry.id(), false);
                return Optional.of(entry);
            }
        }
        Optional<BanEntry> latest = store.allBans().stream()
                .filter(b -> b.name().equalsIgnoreCase(idOrName) && b.isActiveAt(now))
                .max(Comparator.comparingLong(BanEntry::issued));
        if (latest.isPresent()) {
            store.setBanActive(latest.get().id(), false);
        }
        return latest;
    }

    /** The ban currently in force for a player, if any. */
    public Optional<BanEntry> activeBan(UUID uuid) {
        long now = System.currentTimeMillis();
        return store.allBans().stream()
                .filter(b -> b.uuid().equals(uuid) && b.isActiveAt(now))
                .max(Comparator.comparingLong(BanEntry::issued));
    }

    /** Every ban on record for a player, newest first. */
    public List<BanEntry> history(UUID uuid) {
        return store.allBans().stream()
                .filter(b -> b.uuid().equals(uuid))
                .sorted(Comparator.comparingLong(BanEntry::issued).reversed())
                .toList();
    }

    /** Every ban currently in force, newest first. */
    public List<BanEntry> activeBans() {
        long now = System.currentTimeMillis();
        return store.allBans().stream()
                .filter(b -> b.isActiveAt(now))
                .sorted(Comparator.comparingLong(BanEntry::issued).reversed())
                .toList();
    }

    /** Sends the custom ban screen and disconnects the player. */
    public void kickWithBanScreen(Player player, BanEntry entry) {
        player.kick(banScreen(entry));
    }

    /** Builds the custom ban screen for a ban entry. */
    public Component banScreen(BanEntry entry) {
        String expires = entry.expires() == TimeUtil.NEVER
                ? "never (permanent)"
                : TimeUtil.formatTimestamp(entry.expires());
        Component title = messages.render("ban-screen.header");
        Component body = messages.render("ban-screen.body", Map.of(
                "reason", entry.reason().isEmpty() ? entry.category() : entry.reason(),
                "category", entry.category(),
                "id", entry.id(),
                "expires", expires,
                "staff", entry.staff(),
                "appeal", config.appealUrl()));
        return title.append(Component.text("\n")).append(body);
    }

    /** Multiline text form of the ban screen (for /baninfo-style output). */
    public List<String> banScreenLines(BanEntry entry) {
        String expires = entry.expires() == TimeUtil.NEVER
                ? "never (permanent)"
                : TimeUtil.formatTimestamp(entry.expires());
        return List.of(
                entry.name() + " — " + entry.category(),
                "Reason: " + (entry.reason().isEmpty() ? entry.category() : entry.reason()),
                "Ban ID: " + entry.id(),
                "Expires: " + expires,
                "Staff: " + entry.staff());
    }

    /** Disconnects a banned player at login with the custom ban screen. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Optional<BanEntry> ban = activeBan(event.getUniqueId());
        if (ban.isEmpty()) {
            return;
        }
        Component screen = banScreen(ban.get());
        String legacy = LegacyComponentSerializer.legacySection().serialize(screen);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, legacy);
    }

}
