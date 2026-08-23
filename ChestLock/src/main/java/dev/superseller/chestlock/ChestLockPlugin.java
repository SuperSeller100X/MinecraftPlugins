package dev.superseller.chestlock;

import dev.superseller.chestlock.command.ChestLockCommand;
import dev.superseller.chestlock.config.PluginConfig;
import dev.superseller.chestlock.gui.DialogController;
import dev.superseller.chestlock.listener.ProtectionListener;
import dev.superseller.chestlock.message.Feedback;
import dev.superseller.chestlock.message.Messages;
import dev.superseller.chestlock.security.AttemptLimiter;
import dev.superseller.chestlock.security.PasscodeHasher;
import dev.superseller.chestlock.security.SessionManager;
import dev.superseller.chestlock.service.KeyService;
import dev.superseller.chestlock.storage.LockStore;
import dev.superseller.chestlock.storage.PlayerSettingsStore;
import java.io.File;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** ChestLock entry point. Platform state is accessed only on owning entity/region schedulers. */
public final class ChestLockPlugin extends JavaPlugin {
    private volatile PluginConfig runtimeConfig;
    private LockStore lockStore;
    private PlayerSettingsStore settingsStore;
    private Messages messages;
    private Feedback feedback;
    private SessionManager sessions;
    private AttemptLimiter attempts;
    private DialogController dialogs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").isFile()) {
            saveResource("messages.yml", false);
        }
        runtimeConfig = PluginConfig.load(getConfig(), getLogger()::warning);

        lockStore = new LockStore(this);
        settingsStore = new PlayerSettingsStore(this);
        messages = new Messages(this);
        sessions = new SessionManager();
        attempts = new AttemptLimiter();
        feedback = new Feedback(this, messages, settingsStore);
        KeyService keyService = new KeyService(this, lockStore);
        dialogs = new DialogController(this, lockStore, settingsStore, messages, feedback,
                keyService, new PasscodeHasher(), attempts, sessions);

        messages.reload();
        settingsStore.load();

        getServer().getPluginManager().registerEvents(dialogs, this);
        getServer().getPluginManager().registerEvents(
                new ProtectionListener(this, lockStore, settingsStore, feedback, keyService, sessions, attempts, dialogs),
                this);

        ChestLockCommand commandHandler = new ChestLockCommand(this, dialogs, sessions, feedback);
        PluginCommand command = Objects.requireNonNull(getCommand("chestlock"), "chestlock command missing");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getLogger().info("ChestLock 1.0.0 enabled for Paper/Purpur/Folia 26.2.");
    }

    @Override
    public void onDisable() {
        if (dialogs != null) {
            dialogs.clearAll();
        }
        if (sessions != null) {
            sessions.clearAll();
        }
        if (settingsStore != null) {
            settingsStore.saveNow();
        }
    }

    public void reloadChestLock() {
        reloadConfig();
        runtimeConfig = PluginConfig.load(getConfig(), getLogger()::warning);
        messages.reload();
    }

    public PluginConfig runtimeConfig() {
        return runtimeConfig;
    }

    public Feedback feedback() {
        return feedback;
    }

    public void audit(String message) {
        if (runtimeConfig.auditActions()) {
            getLogger().info("[AUDIT] " + message);
        }
    }
}
