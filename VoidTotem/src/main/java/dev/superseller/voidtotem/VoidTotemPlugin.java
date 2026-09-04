package dev.superseller.voidtotem;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.voidtotem.command.VoidTotemCommand;
import dev.superseller.voidtotem.config.VoidConfig;
import dev.superseller.voidtotem.listener.VoidTotemListener;

/**
 * VoidTotem - a custom Void / Shard Totem that saves a player from the void.
 *
 * Hooks into the ShardTools shard shop: on startup it drops a "void_totem"
 * entry into ShardTools' config (price from config) that, when bought, runs
 * "voidtotem give %player% 1" - which hands out the modeled totem. The rescue
 * itself is handled entirely here.
 */
public final class VoidTotemPlugin extends JavaPlugin {

    private VoidConfig config;
    private VoidTotemItem item;
    private VoidTotemListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new VoidConfig();
        config.load(getConfig());
        item = new VoidTotemItem(this);

        listener = new VoidTotemListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        VoidTotemCommand command = new VoidTotemCommand(this);
        PluginCommand cmd = getCommand("voidtotem");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        } else {
            getLogger().severe("Command 'voidtotem' missing from plugin.yml");
        }

        registerInShardShop();

        getLogger().info("VoidTotem enabled. Custom model id: " + config.itemModel());
    }

    @Override
    public void onDisable() {
        getLogger().info("VoidTotem disabled");
    }

    /** Reloads config.yml and refreshes the in-memory settings. */
    public void reloadConfiguration() {
        reloadConfig();
        config.load(getConfig());
        getLogger().info("VoidTotem configuration reloaded");
    }

    /**
     * Best-effort registration of the void_totem item into the ShardTools
     * shard shop. We add the catalog entry to ShardTools' live config and
     * rebuild its catalog so it appears in /st shop without a manual reload.
     */
    private void registerInShardShop() {
        if (!config.autoRegister()) {
            getLogger().info("ShardTools auto-registration is disabled in config.yml.");
            return;
        }
        JavaPlugin shard = (JavaPlugin) getServer().getPluginManager().getPlugin("ShardTools");
        if (shard == null) {
            getLogger().warning("ShardTools not found - the Void Totem will not appear in the shard "
                    + "shop automatically. Install ShardTools or add the entry manually (see the "
                    + "VoidTotem README).");
            return;
        }
        try {
            FileConfiguration cfg = shard.getConfig();
            if (cfg.contains("items.void_totem")) {
                getLogger().info("Void Totem already present in the ShardTools shop config.");
                return;
            }
            cfg.set("items.void_totem.material", "TOTEM_OF_UNDYING");
            cfg.set("items.void_totem.name", "<gradient:#7a00ff:#00e5ff>Void Totem</gradient>");
            cfg.set("items.void_totem.price", config.shopPrice());
            cfg.set("items.void_totem.lifetime-hours", 0);
            cfg.set("items.void_totem.behavior", "NONE");
            cfg.set("items.void_totem.enchants", List.of());
            cfg.set("items.void_totem.lore", List.of(
                    "<gray>A shard-forged charm that catches you",
                    "<gray>at the edge of the void and drags you back.</gray>",
                    "<dark_gray>Breaks the void once per totem.</dark_gray>"));
            cfg.set("items.void_totem.command", "voidtotem give %player% 1");
            shard.saveConfig();
            try {
                Method rebuild = shard.getClass().getMethod("rebuildCatalog");
                rebuild.invoke(shard);
                getLogger().info("Registered the Void Totem in the ShardTools shop for "
                        + config.shopPrice() + " shards.");
            } catch (ReflectiveOperationException ex) {
                getLogger().warning("Added void_totem to the ShardTools config; run /sta reload "
                        + "(or /st reload) to load it into the shop.");
            }
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Could not auto-register the Void Totem in ShardTools", ex);
        }
    }

    public VoidConfig config() { return config; }
    public VoidTotemItem item() { return item; }
    public VoidTotemListener listener() { return listener; }
}
