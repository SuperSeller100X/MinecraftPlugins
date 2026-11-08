package dev.superseller.veltryx.home;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.config.VeltryxConfig;
import dev.superseller.veltryx.economy.EconomyService;
import dev.superseller.veltryx.model.HomeLocation;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Money;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Homes: {@code /sethome <name>}, {@code /home <name>}, {@code /homes}.
 * The first homes are free, every extra home costs more than the previous
 * one, and there is a hard cap per player.
 */
public final class HomeService {

    /**
     * Outcome of a home action: whether it worked, the messages.yml template
     * key describing it, and the template values.
     */
    public record Result(boolean ok, String templateKey, Map<String, String> values) {
        static Result ok(String key, Map<String, String> values) {
            return new Result(true, key, values);
        }

        static Result fail(String key, Map<String, String> values) {
            return new Result(false, key, values);
        }
    }

    private final DataStore store;
    private final EconomyService economy;
    private final Messages messages;
    private final VeltryxConfig config;

    public HomeService(DataStore store, EconomyService economy, Messages messages, VeltryxConfig config) {
        this.store = store;
        this.economy = economy;
        this.messages = messages;
        this.config = config;
    }

    /** Homes of a player, sorted by name. */
    public List<HomeLocation> list(UUID owner) {
        return store.homesFor(owner);
    }

    /** One home by name, or {@code null}. */
    public HomeLocation find(UUID owner, String rawName) {
        String name = HomePricing.normalizeName(rawName);
        if (name == null) {
            return null;
        }
        for (HomeLocation home : store.homesFor(owner)) {
            if (home.name().equals(name)) {
                return home;
            }
        }
        return null;
    }

    /** Sets (or moves) a home at the player's position. */
    public Result setHome(Player player, String rawName) {
        String name = HomePricing.normalizeName(rawName);
        if (name == null) {
            return Result.fail("home.invalid-name", Map.of());
        }
        List<HomeLocation> homes = store.homesFor(player.getUniqueId());
        HomeLocation existing = null;
        for (HomeLocation home : homes) {
            if (home.name().equals(name)) {
                existing = home;
                break;
            }
        }
        Location at = player.getLocation();
        HomeLocation updated = new HomeLocation(player.getUniqueId(), name,
                at.getWorld() == null ? "world" : at.getWorld().getName(),
                at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        Map<String, String> base = new HashMap<>();
        base.put("name", name);
        base.put("count", String.valueOf(existing == null ? homes.size() + 1 : homes.size()));
        base.put("max", String.valueOf(config.maxHomes()));
        if (existing != null) {
            store.saveHome(updated);
            return Result.ok("home.moved", base);
        }
        if (homes.size() >= config.maxHomes()) {
            return Result.fail("home.full", Map.of("max", String.valueOf(config.maxHomes())));
        }
        long cost = HomePricing.costCents(homes.size() + 1, config.freeHomes(), config.homePriceStep());
        if (cost > 0) {
            long balance = economy.getBalance(player.getUniqueId());
            if (!economy.has(player.getUniqueId(), cost)) {
                return Result.fail("home.cannot-afford", Map.of(
                        "price", Money.formatCompact(cost),
                        "balance", Money.formatCompact(balance)));
            }
            economy.withdraw(player.getUniqueId(), player.getName(), cost);
            base.put("price", Money.formatCompact(cost));
            store.saveHome(updated);
            return Result.ok("home.paid", base);
        }
        store.saveHome(updated);
        return Result.ok("home.set", base);
    }

    /** Deletes a home. */
    public Result delete(Player player, String rawName) {
        String name = HomePricing.normalizeName(rawName);
        if (name == null || find(player.getUniqueId(), name) == null) {
            return Result.fail("home.not-found", Map.of("name", String.valueOf(rawName)));
        }
        store.removeHome(player.getUniqueId(), name);
        return Result.ok("home.deleted", Map.of("name", name));
    }

    /** Teleports the player to a home. */
    public Result teleport(Player player, String rawName) {
        String name = HomePricing.normalizeName(rawName);
        HomeLocation home = name == null ? null : find(player.getUniqueId(), name);
        if (home == null) {
            return Result.fail("home.not-found", Map.of("name", String.valueOf(rawName)));
        }
        World world = Bukkit.getWorld(home.world());
        if (world == null) {
            return Result.fail("home.world-missing", Map.of("name", name));
        }
        player.teleportAsync(new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch()));
        return Result.ok("home.teleported", Map.of("name", name));
    }

    /** Renames a home. The home keeps its location, icon and slot in the list. */
    public Result rename(Player player, String rawOld, String rawNew) {
        String oldName = HomePricing.normalizeName(rawOld);
        String newName = HomePricing.normalizeName(rawNew);
        if (oldName == null || find(player.getUniqueId(), oldName) == null) {
            return Result.fail("home.not-found", Map.of("name", String.valueOf(rawOld)));
        }
        if (newName == null) {
            return Result.fail("home.invalid-name", Map.of());
        }
        if (!newName.equals(oldName) && find(player.getUniqueId(), newName) != null) {
            return Result.fail("home.name-taken", Map.of("name", newName));
        }
        store.renameHome(player.getUniqueId(), oldName, newName);
        return Result.ok("home.renamed", Map.of("name", newName, "old", oldName));
    }

    /** Sets the icon of a home (any Minecraft block or item); null resets to the default. */
    public Result setIcon(Player player, String rawName, String materialName) {
        String name = HomePricing.normalizeName(rawName);
        if (name == null || find(player.getUniqueId(), name) == null) {
            return Result.fail("home.not-found", Map.of("name", String.valueOf(rawName)));
        }
        if (materialName == null) {
            store.setHomeIcon(player.getUniqueId(), name, null);
            return Result.ok("home.icon-set", Map.of("name", name, "icon", "default"));
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) {
            return Result.fail("home.bad-icon", Map.of("icon", String.valueOf(materialName)));
        }
        store.setHomeIcon(player.getUniqueId(), name, material.name());
        return Result.ok("home.icon-set", Map.of("name", name, "icon", material.name()));
    }

    /** Icon material name of a home, or {@code null} for the default icon. */
    public String icon(UUID owner, String name) {
        return store.homeIcon(owner, name);
    }
}
