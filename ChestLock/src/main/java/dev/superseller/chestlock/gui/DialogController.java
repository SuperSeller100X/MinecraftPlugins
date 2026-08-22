package dev.superseller.chestlock.gui;

import dev.superseller.chestlock.ChestLockPlugin;
import dev.superseller.chestlock.config.PluginConfig;
import dev.superseller.chestlock.message.Feedback;
import dev.superseller.chestlock.message.Messages;
import dev.superseller.chestlock.model.BlockRef;
import dev.superseller.chestlock.model.LockData;
import dev.superseller.chestlock.model.PlayerSettings;
import dev.superseller.chestlock.security.AttemptLimiter;
import dev.superseller.chestlock.security.PasscodeHasher;
import dev.superseller.chestlock.security.PasscodePolicy;
import dev.superseller.chestlock.security.SessionManager;
import dev.superseller.chestlock.service.KeyService;
import dev.superseller.chestlock.storage.LockStore;
import dev.superseller.chestlock.storage.PlayerSettingsStore;
import dev.superseller.chestlock.util.DurationFormatter;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;

/** Native Minecraft dialog workflows. Passcodes never enter chat or command arguments. */
public final class DialogController implements Listener {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private static final Key LOCK_SUBMIT = Key.key("chestlock:lock_submit");
    private static final Key UNLOCK_SUBMIT = Key.key("chestlock:unlock_submit");
    private static final Key CHANGE_SUBMIT = Key.key("chestlock:change_submit");
    private static final Key REMOVE_SUBMIT = Key.key("chestlock:remove_submit");
    private static final Key KEY_SUBMIT = Key.key("chestlock:key_submit");
    private static final Key SETTINGS_SUBMIT = Key.key("chestlock:settings_submit");
    private static final Key FORCE_REMOVE_SUBMIT = Key.key("chestlock:force_remove_submit");
    private static final Key MENU_LOCK = Key.key("chestlock:menu_lock");
    private static final Key MENU_UNLOCK = Key.key("chestlock:menu_unlock");
    private static final Key MENU_INFO = Key.key("chestlock:menu_info");
    private static final Key MENU_SETTINGS = Key.key("chestlock:menu_settings");
    private static final Key MENU_HELP = Key.key("chestlock:menu_help");

    private final ChestLockPlugin plugin;
    private final LockStore lockStore;
    private final PlayerSettingsStore settingsStore;
    private final Messages messages;
    private final Feedback feedback;
    private final KeyService keyService;
    private final PasscodeHasher hasher;
    private final AttemptLimiter attempts;
    private final SessionManager sessions;
    private final ConcurrentMap<UUID, PendingAction> pending = new ConcurrentHashMap<>();

    public DialogController(
            ChestLockPlugin plugin,
            LockStore lockStore,
            PlayerSettingsStore settingsStore,
            Messages messages,
            Feedback feedback,
            KeyService keyService,
            PasscodeHasher hasher,
            AttemptLimiter attempts,
            SessionManager sessions
    ) {
        this.plugin = plugin;
        this.lockStore = lockStore;
        this.settingsStore = settingsStore;
        this.messages = messages;
        this.feedback = feedback;
        this.keyService = keyService;
        this.hasher = hasher;
        this.attempts = attempts;
        this.sessions = sessions;
    }

    public void openMain(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        if (player.hasPermission("chestlock.lock")) {
            actions.add(button("Lock", "Lock the container you are looking at", MENU_LOCK));
        }
        if (player.hasPermission("chestlock.unlock")) {
            actions.add(button("Unlock", "Enter a passcode for the targeted lock", MENU_UNLOCK));
        }
        if (player.hasPermission("chestlock.info")) {
            actions.add(button("Information", "Inspect the targeted lock", MENU_INFO));
        }
        if (player.hasPermission("chestlock.settings")) {
            actions.add(button("My settings", "Change personal defaults and feedback", MENU_SETTINGS));
        }
        actions.add(button("Help", "Show commands and short forms", MENU_HELP));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("ChestLock", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Look at a chest, trapped chest, barrel, or placed shulker box, then choose an action.",
                                NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.multiAction(actions, closeButton(), 2)));
        player.showDialog(dialog);
    }

    public void beginLock(Player player) {
        if (!require(player, "chestlock.lock")) return;
        TargetResult target = target(player);
        if (target == null) return;
        if (!lockStore.canCreateLock(target.block().getType(), plugin.runtimeConfig())) {
            feedback.failure(player, "unsupported-container", Map.of());
            return;
        }
        LockStore.Lookup lookup = lockStore.lookup(target.block());
        if (lookup.corrupt()) {
            feedback.failure(player, "corrupt-lock", Map.of());
            return;
        }
        if (lookup.data() != null) {
            feedback.failure(player, "already-locked", Map.of());
            return;
        }
        PlayerSettings settings = settingsStore.get(player.getUniqueId());
        putPending(player, Action.LOCK, target.ref(), null);
        PluginConfig config = plugin.runtimeConfig();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Create ChestLock", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "The passcode stays in this private dialog and is stored only as a salted hash.",
                                NamedTextColor.GRAY))))
                        .inputs(List.of(
                                DialogInput.text("passcode", Component.text("Passcode"))
                                        .width(320).maxLength(config.maxPasscodeLength()).build(),
                                DialogInput.text("confirmation", Component.text("Confirm passcode"))
                                        .width(320).maxLength(config.maxPasscodeLength()).build(),
                                DialogInput.numberRange("duration", Component.text("Unlock duration"),
                                                config.minDurationSeconds(), config.maxDurationSeconds())
                                        .initial((float) settings.defaultDurationSeconds())
                                        .step(5f).width(320).labelFormat("%s: %s seconds").build(),
                                DialogInput.bool("create_key", Component.text("Create a transferable physical key"),
                                        settings.defaultCreateKey(), "true", "false")
                        ))
                        .build())
                .type(DialogType.confirmation(submitButton("Create lock", LOCK_SUBMIT), closeButton())));
        player.showDialog(dialog);
    }

    public void beginUnlock(Player player) {
        if (!require(player, "chestlock.unlock")) return;
        LockedTarget target = lockedTarget(player, false);
        if (target == null) return;
        long remaining = attempts.remainingCooldown(player.getUniqueId(), target.data().lockId(),
                System.currentTimeMillis());
        if (remaining > 0) {
            cooldown(player, remaining);
            return;
        }
        putPending(player, Action.UNLOCK, target.ref(), target.data());
        player.showDialog(passcodeDialog("Unlock container",
                "A correct passcode authorizes opening and physical breaking for "
                        + DurationFormatter.formatMillis(target.data().unlockDurationMillis()) + '.',
                "Unlock", UNLOCK_SUBMIT));
    }

    public void showInfo(Player player) {
        if (!require(player, "chestlock.info")) return;
        LockedTarget target = lockedTarget(player, true);
        if (target == null) return;
        LockData data = target.data();
        long remaining = sessions.remaining(player.getUniqueId(), data.lockId(), System.currentTimeMillis());
        Component body = Component.text()
                .append(Component.text("Owner: ", NamedTextColor.GRAY))
                .append(Component.text(data.ownerName(), NamedTextColor.WHITE)).appendNewline()
                .append(Component.text("Unlock duration: ", NamedTextColor.GRAY))
                .append(Component.text(DurationFormatter.formatMillis(data.unlockDurationMillis()), NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("Created: ", NamedTextColor.GRAY))
                .append(Component.text(DATE_TIME.format(Instant.ofEpochMilli(data.createdAtMillis())), NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("Lock ID: ", NamedTextColor.GRAY))
                .append(Component.text(data.lockId().toString().substring(0, 8), NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("Container blocks: ", NamedTextColor.GRAY))
                .append(Component.text(Integer.toString(target.lookup().group().blocks().size()), NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text("Your current session: ", NamedTextColor.GRAY))
                .append(Component.text(remaining > 0 ? DurationFormatter.formatMillis(remaining) : "locked",
                        remaining > 0 ? NamedTextColor.GREEN : NamedTextColor.RED))
                .build();
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("ChestLock information", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(body))).build())
                .type(DialogType.notice(closeButton()))));
    }

    public void beginChange(Player player) {
        if (!require(player, "chestlock.manage")) return;
        LockedTarget target = ownedTarget(player);
        if (target == null) return;
        putPending(player, Action.CHANGE, target.ref(), target.data());
        PluginConfig config = plugin.runtimeConfig();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Change passcode", NamedTextColor.GOLD))
                        .inputs(List.of(
                                DialogInput.text("old_passcode", Component.text("Current passcode"))
                                        .width(320).maxLength(config.maxPasscodeLength()).build(),
                                DialogInput.text("new_passcode", Component.text("New passcode"))
                                        .width(320).maxLength(config.maxPasscodeLength()).build(),
                                DialogInput.text("confirmation", Component.text("Confirm new passcode"))
                                        .width(320).maxLength(config.maxPasscodeLength()).build()
                        )).build())
                .type(DialogType.confirmation(submitButton("Change", CHANGE_SUBMIT), closeButton())));
        player.showDialog(dialog);
    }

    public void beginRemove(Player player) {
        if (!require(player, "chestlock.manage")) return;
        LockedTarget target = ownedTarget(player);
        if (target == null) return;
        putPending(player, Action.REMOVE, target.ref(), target.data());
        PluginConfig config = plugin.runtimeConfig();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Remove lock", NamedTextColor.RED))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "This permanently removes passcode protection and invalidates all keys.",
                                NamedTextColor.YELLOW))))
                        .inputs(List.of(
                                DialogInput.text("passcode", Component.text("Current passcode"))
                                        .width(320).maxLength(config.maxPasscodeLength()).build(),
                                DialogInput.bool("confirmed", Component.text("I understand"), false, "true", "false")
                        )).build())
                .type(DialogType.confirmation(submitButton("Remove", REMOVE_SUBMIT), closeButton())));
        player.showDialog(dialog);
    }

    public void beginKey(Player player) {
        if (!require(player, "chestlock.key")) return;
        LockedTarget target = ownedTarget(player);
        if (target == null) return;
        putPending(player, Action.KEY, target.ref(), target.data());
        PluginConfig config = plugin.runtimeConfig();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Issue physical key", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "The key is transferable, opens only this lock, and never permits breaking.",
                                NamedTextColor.GRAY))))
                        .inputs(List.of(
                                DialogInput.text("passcode", Component.text("Current passcode"))
                                        .width(320).maxLength(config.maxPasscodeLength()).build(),
                                DialogInput.bool("revoke", Component.text("Revoke every previously issued key"),
                                        false, "true", "false")
                        )).build())
                .type(DialogType.confirmation(submitButton("Issue key", KEY_SUBMIT), closeButton())));
        player.showDialog(dialog);
    }

    public void openSettings(Player player) {
        if (!require(player, "chestlock.settings")) return;
        PlayerSettings settings = settingsStore.get(player.getUniqueId());
        PluginConfig config = plugin.runtimeConfig();
        putPending(player, Action.SETTINGS, null, null);
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("My ChestLock settings", NamedTextColor.GOLD))
                        .inputs(List.of(
                                DialogInput.numberRange("duration", Component.text("Default unlock duration"),
                                                config.minDurationSeconds(), config.maxDurationSeconds())
                                        .initial((float) settings.defaultDurationSeconds()).step(5f).width(320)
                                        .labelFormat("%s: %s seconds").build(),
                                DialogInput.bool("create_key", Component.text("Create a key by default"),
                                        settings.defaultCreateKey(), "true", "false"),
                                DialogInput.bool("auto_open", Component.text("Auto-open after successful unlock"),
                                        settings.autoOpen(), "true", "false"),
                                DialogInput.bool("sounds", Component.text("Sounds"), settings.sounds(), "true", "false"),
                                DialogInput.bool("particles", Component.text("Particles"), settings.particles(), "true", "false"),
                                DialogInput.bool("action_bar", Component.text("Action-bar feedback"),
                                        settings.actionBar(), "true", "false"),
                                DialogInput.bool("break_confirmation", Component.text("Require a second break action"),
                                        settings.breakConfirmation(), "true", "false"),
                                DialogInput.bool("attempt_notifications", Component.text("Notify me of wrong attempts"),
                                        settings.attemptNotifications(), "true", "false")
                        )).build())
                .type(DialogType.confirmation(submitButton("Save", SETTINGS_SUBMIT), closeButton())));
        player.showDialog(dialog);
    }

    public void showHelp(Player player) {
        Component body = Component.text()
                .append(Component.text("/cl lock", NamedTextColor.GOLD)).append(Component.text("  /cl l", NamedTextColor.DARK_GRAY))
                .append(Component.text(" — create a lock", NamedTextColor.GRAY)).appendNewline()
                .append(Component.text("/cl unlock", NamedTextColor.GOLD)).append(Component.text("  /cl u", NamedTextColor.DARK_GRAY))
                .append(Component.text(" — begin a timed session", NamedTextColor.GRAY)).appendNewline()
                .append(Component.text("/cl info", NamedTextColor.GOLD)).append(Component.text("  /cl i", NamedTextColor.DARK_GRAY))
                .append(Component.text(" — inspect a lock", NamedTextColor.GRAY)).appendNewline()
                .append(Component.text("/cl change", NamedTextColor.GOLD)).append(Component.text("  /cl c", NamedTextColor.DARK_GRAY))
                .append(Component.text(" — change your passcode", NamedTextColor.GRAY)).appendNewline()
                .append(Component.text("/cl remove", NamedTextColor.GOLD)).append(Component.text("  /cl r", NamedTextColor.DARK_GRAY))
                .append(Component.text(" — remove your lock", NamedTextColor.GRAY)).appendNewline()
                .append(Component.text("/cl key", NamedTextColor.GOLD)).append(Component.text("  /cl k", NamedTextColor.DARK_GRAY))
                .append(Component.text(" — issue/revoke keys", NamedTextColor.GRAY)).appendNewline()
                .append(Component.text("/cl settings", NamedTextColor.GOLD)).append(Component.text("  /cl s", NamedTextColor.DARK_GRAY))
                .append(Component.text(" — personal defaults", NamedTextColor.GRAY))
                .build();
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("ChestLock help", NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(body))).build())
                .type(DialogType.notice(closeButton()))));
    }

    public void beginForceRemove(Player player) {
        if (!require(player, "chestlock.admin.forceremove")) return;
        TargetResult target = target(player);
        if (target == null) return;
        LockStore.Lookup lookup = lockStore.lookup(target.block());
        if (!lookup.secured()) {
            feedback.message(player, "not-locked");
            return;
        }
        putPending(player, Action.FORCE_REMOVE, target.ref(), lookup.data());
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Force-remove lock", NamedTextColor.RED))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Administrative recovery: removes this lock without its passcode.", NamedTextColor.YELLOW))))
                        .inputs(List.of(DialogInput.bool("confirmed", Component.text("Force-remove this lock"),
                                false, "true", "false"))).build())
                .type(DialogType.confirmation(submitButton("Force remove", FORCE_REMOVE_SUBMIT), closeButton())));
        player.showDialog(dialog);
    }

    @EventHandler
    public void onDialogClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        Key identifier = event.getIdentifier();
        if (handleMenuAction(player, identifier)) {
            return;
        }
        Action submitted = Action.fromKey(identifier);
        if (submitted == null) {
            return;
        }
        PendingAction context = pending.remove(player.getUniqueId());
        if (context == null || context.action() != submitted) {
            feedback.message(player, "dialog-expired");
            return;
        }
        if (context.expiresAtMillis() < System.currentTimeMillis()) {
            feedback.message(player, "dialog-expired");
            return;
        }
        DialogResponseView view = event.getDialogResponseView();
        if (view == null) {
            feedback.message(player, "dialog-expired");
            return;
        }
        switch (submitted) {
            case LOCK -> submitLock(player, context, view);
            case UNLOCK -> submitUnlock(player, context, view);
            case CHANGE -> submitChange(player, context, view);
            case REMOVE -> submitRemove(player, context, view);
            case KEY -> submitKey(player, context, view);
            case SETTINGS -> submitSettings(player, view);
            case FORCE_REMOVE -> submitForceRemove(player, context, view);
        }
    }

    private boolean handleMenuAction(Player player, Key identifier) {
        Runnable action = null;
        if (identifier.equals(MENU_LOCK)) action = () -> beginLock(player);
        else if (identifier.equals(MENU_UNLOCK)) action = () -> beginUnlock(player);
        else if (identifier.equals(MENU_INFO)) action = () -> showInfo(player);
        else if (identifier.equals(MENU_SETTINGS)) action = () -> openSettings(player);
        else if (identifier.equals(MENU_HELP)) action = () -> showHelp(player);
        if (action == null) return false;
        Runnable selected = action;
        player.getScheduler().run(plugin, task -> selected.run(), null);
        return true;
    }

    private void submitLock(Player player, PendingAction context, DialogResponseView view) {
        if (!require(player, "chestlock.lock")) return;
        String passcode = view.getText("passcode");
        String confirmation = view.getText("confirmation");
        Float rawDuration = view.getFloat("duration");
        Boolean createKey = view.getBoolean("create_key");
        PluginConfig config = plugin.runtimeConfig();
        if (!PasscodePolicy.isValid(passcode, config.minPasscodeLength(), config.maxPasscodeLength())) {
            invalidPasscode(player, config);
            return;
        }
        if (!passcode.equals(confirmation)) {
            feedback.failure(player, "passcodes-differ", Map.of());
            return;
        }
        if (rawDuration == null || createKey == null) {
            feedback.failure(player, "operation-failed", Map.of());
            return;
        }
        int duration = Math.round(rawDuration);
        if (duration < config.minDurationSeconds() || duration > config.maxDurationSeconds()) {
            feedback.failure(player, "operation-failed", Map.of());
            return;
        }
        int iterations = config.hashIterations();
        String ownerName = player.getName();
        UUID ownerId = player.getUniqueId();
        char[] secret = passcode.toCharArray();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                PasscodeHasher.PasswordHash passwordHash = hasher.hash(secret, iterations);
                LockData data = new LockData(UUID.randomUUID(), ownerId, ownerName, passwordHash.salt(),
                        passwordHash.hash(), iterations, duration * 1_000L, UUID.randomUUID(),
                        System.currentTimeMillis());
                onRegion(context.target(), player, block -> {
                    LockStore.Lookup lookup = lockStore.lookup(block);
                    if (!lookup.supported() || lookup.secured()
                            || !lockStore.canCreateLock(block.getType(), plugin.runtimeConfig())) {
                        feedback.failure(player, "target-changed", Map.of());
                        return;
                    }
                    if (!lockStore.create(lookup.group(), data)) {
                        feedback.failure(player, "operation-failed", Map.of());
                        return;
                    }
                    PlayerSettings previous = settingsStore.get(ownerId);
                    settingsStore.put(ownerId, new PlayerSettings(duration, createKey, previous.autoOpen(),
                            previous.sounds(), previous.particles(), previous.actionBar(),
                            previous.breakConfirmation(), previous.attemptNotifications()));
                    if (createKey) keyService.give(player, data);
                    feedback.success(player, "locked-created",
                            Map.of("duration", DurationFormatter.formatMillis(data.unlockDurationMillis())));
                    plugin.audit(ownerName + " created lock " + shortId(data.lockId()) + " at "
                            + locationText(context.target()));
                });
            } catch (GeneralSecurityException exception) {
                plugin.getLogger().severe("Could not hash a new passcode: " + exception.getMessage());
                feedback.failure(player, "operation-failed", Map.of());
            } finally {
                Arrays.fill(secret, '\0');
            }
        });
    }

    private void submitUnlock(Player player, PendingAction context, DialogResponseView view) {
        if (!require(player, "chestlock.unlock")) return;
        String passcode = view.getText("passcode");
        if (passcode == null || context.snapshot() == null) {
            feedback.failure(player, "operation-failed", Map.of());
            return;
        }
        LockData snapshot = context.snapshot();
        long now = System.currentTimeMillis();
        long remaining = attempts.remainingCooldown(player.getUniqueId(), snapshot.lockId(), now);
        if (remaining > 0) {
            cooldown(player, remaining);
            return;
        }
        verifyAsync(passcode, snapshot, verified -> {
            if (!verified) {
                failedAttempt(player, context, snapshot);
                return;
            }
            attempts.recordSuccess(player.getUniqueId(), snapshot.lockId());
            onRegion(context.target(), player, block -> {
                LockStore.Lookup lookup = lockStore.lookup(block);
                if (lookup.data() == null || lookup.corrupt()
                        || !snapshot.sameSecurityVersion(lookup.data())) {
                    feedback.failure(player, "target-changed", Map.of());
                    return;
                }
                long expires = System.currentTimeMillis() + lookup.data().unlockDurationMillis();
                sessions.authorize(player.getUniqueId(), lookup.data().lockId(), expires);
                scheduleRelock(player, lookup.data().lockId(), lookup.data().unlockDurationMillis());
                feedback.success(player, "unlocked", Map.of("duration",
                        DurationFormatter.formatMillis(lookup.data().unlockDurationMillis())));
                plugin.audit(player.getName() + " unlocked " + shortId(lookup.data().lockId()) + " at "
                        + locationText(context.target()));
                if (settingsStore.get(player.getUniqueId()).autoOpen()) {
                    tryAutoOpen(player, context.target(), lookup.data().lockId());
                }
            });
        });
    }

    private void submitChange(Player player, PendingAction context, DialogResponseView view) {
        if (!require(player, "chestlock.manage")) return;
        String oldPasscode = view.getText("old_passcode");
        String newPasscode = view.getText("new_passcode");
        String confirmation = view.getText("confirmation");
        PluginConfig config = plugin.runtimeConfig();
        if (oldPasscode == null || context.snapshot() == null) {
            feedback.failure(player, "operation-failed", Map.of());
            return;
        }
        if (!PasscodePolicy.isValid(newPasscode, config.minPasscodeLength(), config.maxPasscodeLength())) {
            invalidPasscode(player, config);
            return;
        }
        if (!newPasscode.equals(confirmation)) {
            feedback.failure(player, "passcodes-differ", Map.of());
            return;
        }
        LockData snapshot = context.snapshot();
        verifyAsync(oldPasscode, snapshot, verified -> {
            if (!verified) {
                failedAttempt(player, context, snapshot);
                return;
            }
            char[] replacement = newPasscode.toCharArray();
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    PasscodeHasher.PasswordHash hash = hasher.hash(replacement, config.hashIterations());
                    LockData updated = snapshot.withPassword(hash.salt(), hash.hash(), config.hashIterations());
                    onRegion(context.target(), player, block -> {
                        LockStore.Lookup lookup = lockStore.lookup(block);
                        if (!canManageCurrent(player, snapshot, lookup)) return;
                        if (!lockStore.update(lookup.group(), updated)) {
                            feedback.failure(player, "operation-failed", Map.of());
                            return;
                        }
                        attempts.recordSuccess(player.getUniqueId(), snapshot.lockId());
                        sessions.clearLock(snapshot.lockId());
                        closeUnauthorizedViewers(lookup.group().inventory(), snapshot.lockId());
                        feedback.success(player, "passcode-changed", Map.of());
                        plugin.audit(player.getName() + " changed passcode for " + shortId(snapshot.lockId()));
                    });
                } catch (GeneralSecurityException exception) {
                    plugin.getLogger().severe("Could not hash a replacement passcode: " + exception.getMessage());
                    feedback.failure(player, "operation-failed", Map.of());
                } finally {
                    Arrays.fill(replacement, '\0');
                }
            });
        });
    }

    private void submitRemove(Player player, PendingAction context, DialogResponseView view) {
        if (!require(player, "chestlock.manage")) return;
        String passcode = view.getText("passcode");
        Boolean confirmed = view.getBoolean("confirmed");
        LockData snapshot = context.snapshot();
        if (passcode == null || snapshot == null || !Boolean.TRUE.equals(confirmed)) {
            return;
        }
        verifyAsync(passcode, snapshot, verified -> {
            if (!verified) {
                failedAttempt(player, context, snapshot);
                return;
            }
            onRegion(context.target(), player, block -> {
                LockStore.Lookup lookup = lockStore.lookup(block);
                if (!canManageCurrent(player, snapshot, lookup)) return;
                if (!lockStore.clear(lookup.group())) {
                    feedback.failure(player, "operation-failed", Map.of());
                    return;
                }
                sessions.clearLock(snapshot.lockId());
                attempts.recordSuccess(player.getUniqueId(), snapshot.lockId());
                feedback.success(player, "lock-removed", Map.of());
                plugin.audit(player.getName() + " removed lock " + shortId(snapshot.lockId()));
            });
        });
    }

    private void submitKey(Player player, PendingAction context, DialogResponseView view) {
        if (!require(player, "chestlock.key")) return;
        String passcode = view.getText("passcode");
        Boolean revoke = view.getBoolean("revoke");
        LockData snapshot = context.snapshot();
        if (passcode == null || revoke == null || snapshot == null) {
            feedback.failure(player, "operation-failed", Map.of());
            return;
        }
        verifyAsync(passcode, snapshot, verified -> {
            if (!verified) {
                failedAttempt(player, context, snapshot);
                return;
            }
            onRegion(context.target(), player, block -> {
                LockStore.Lookup lookup = lockStore.lookup(block);
                if (!canManageCurrent(player, snapshot, lookup)) return;
                LockData current = lookup.data();
                LockData keyData = revoke ? current.withKeyToken(UUID.randomUUID()) : current;
                if (revoke && !lockStore.update(lookup.group(), keyData)) {
                    feedback.failure(player, "operation-failed", Map.of());
                    return;
                }
                if (revoke) {
                    closeUnauthorizedViewers(lookup.group().inventory(), snapshot.lockId());
                }
                keyService.give(player, keyData);
                attempts.recordSuccess(player.getUniqueId(), snapshot.lockId());
                feedback.success(player, revoke ? "keys-revoked" : "key-issued", Map.of());
                plugin.audit(player.getName() + (revoke ? " revoked and reissued keys for " : " issued a key for ")
                        + shortId(snapshot.lockId()));
            });
        });
    }

    private void submitSettings(Player player, DialogResponseView view) {
        if (!require(player, "chestlock.settings")) return;
        Float duration = view.getFloat("duration");
        Boolean createKey = view.getBoolean("create_key");
        Boolean autoOpen = view.getBoolean("auto_open");
        Boolean sounds = view.getBoolean("sounds");
        Boolean particles = view.getBoolean("particles");
        Boolean actionBar = view.getBoolean("action_bar");
        Boolean breakConfirmation = view.getBoolean("break_confirmation");
        Boolean attemptNotifications = view.getBoolean("attempt_notifications");
        if (duration == null || createKey == null || autoOpen == null || sounds == null || particles == null
                || actionBar == null || breakConfirmation == null || attemptNotifications == null) {
            feedback.failure(player, "operation-failed", Map.of());
            return;
        }
        PluginConfig config = plugin.runtimeConfig();
        int seconds = Math.round(duration);
        if (seconds < config.minDurationSeconds() || seconds > config.maxDurationSeconds()) {
            feedback.failure(player, "operation-failed", Map.of());
            return;
        }
        settingsStore.put(player.getUniqueId(), new PlayerSettings(seconds, createKey, autoOpen, sounds,
                particles, actionBar, breakConfirmation, attemptNotifications));
        feedback.success(player, "settings-saved", Map.of());
    }

    private void submitForceRemove(Player player, PendingAction context, DialogResponseView view) {
        if (!player.hasPermission("chestlock.admin.forceremove")
                || !Boolean.TRUE.equals(view.getBoolean("confirmed"))) {
            return;
        }
        onRegion(context.target(), player, block -> {
            LockStore.Lookup lookup = lockStore.lookup(block);
            if (!lookup.secured()) {
                feedback.message(player, "not-locked");
                return;
            }
            UUID lockId = lookup.data() == null ? null : lookup.data().lockId();
            if (!lockStore.clear(lookup.group())) {
                feedback.failure(player, "operation-failed", Map.of());
                return;
            }
            if (lockId != null) sessions.clearLock(lockId);
            feedback.success(player, "force-removed", Map.of());
            plugin.audit(player.getName() + " force-removed lock at " + locationText(context.target()));
        });
    }

    private void verifyAsync(String passcode, LockData snapshot, java.util.function.Consumer<Boolean> completion) {
        char[] secret = passcode.toCharArray();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            boolean verified = false;
            try {
                verified = hasher.verify(secret, snapshot.salt(), snapshot.hash(), snapshot.iterations());
            } catch (GeneralSecurityException exception) {
                plugin.getLogger().severe("Could not verify a passcode: " + exception.getMessage());
            } finally {
                Arrays.fill(secret, '\0');
            }
            completion.accept(verified);
        });
    }

    private void failedAttempt(Player player, PendingAction context, LockData snapshot) {
        PluginConfig config = plugin.runtimeConfig();
        long now = System.currentTimeMillis();
        long cooldown = attempts.recordFailure(player.getUniqueId(), snapshot.lockId(), now,
                config.attemptsBeforeCooldown(), config.attemptWindowMillis(), config.baseCooldownMillis(),
                config.maxCooldownMillis());
        if (cooldown > 0) cooldown(player, cooldown);
        else feedback.failure(player, "wrong-passcode", Map.of());
        notifyOwner(player, context.target(), snapshot);
        plugin.audit(player.getName() + " entered a wrong passcode for " + shortId(snapshot.lockId())
                + " at " + locationText(context.target()));
    }

    private void notifyOwner(Player attacker, BlockRef target, LockData lock) {
        Player owner = Bukkit.getPlayer(lock.ownerId());
        if (owner == null || owner.getUniqueId().equals(attacker.getUniqueId())
                || !settingsStore.get(owner.getUniqueId()).attemptNotifications()) {
            return;
        }
        feedback.message(owner, "owner-attempt-alert", Map.of(
                "player", attacker.getName(), "location", locationText(target)));
    }

    private void closeUnauthorizedViewers(Inventory inventory, UUID lockId) {
        List<Player> viewers = inventory.getViewers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .toList();
        for (Player viewer : viewers) {
            viewer.getScheduler().run(plugin, task -> {
                Inventory top = viewer.getOpenInventory().getTopInventory();
                Block block = lockStore.blockForInventory(top);
                if (block == null) return;
                LockStore.Lookup current = lockStore.lookup(block);
                if (current.data() != null && current.data().lockId().equals(lockId)
                        && !sessions.isAuthorized(viewer.getUniqueId(), lockId, System.currentTimeMillis())
                        && !keyService.playerHoldsValidKey(viewer, current.data())
                        && !sessions.hasBypass(viewer.getUniqueId())) {
                    viewer.closeInventory();
                }
            }, null);
        }
    }

    private void scheduleRelock(Player player, UUID lockId, long durationMillis) {
        long delayTicks = Math.max(1, (durationMillis + 49) / 50);
        player.getScheduler().runDelayed(plugin, task -> {
            if (sessions.isAuthorized(player.getUniqueId(), lockId, System.currentTimeMillis())) {
                return; // A newer unlock extended the session; its own task will close the view later.
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            Block block = lockStore.blockForInventory(top);
            if (block == null) return;
            LockStore.Lookup lookup = lockStore.lookup(block);
            if (lookup.data() != null && lookup.data().lockId().equals(lockId)
                    && !keyService.playerHoldsValidKey(player, lookup.data())
                    && !sessions.hasBypass(player.getUniqueId())) {
                player.closeInventory();
            }
        }, null, delayTicks);
    }

    private void tryAutoOpen(Player player, BlockRef target, UUID lockId) {
        player.getScheduler().run(plugin, task -> {
            World world = target.world();
            if (world == null || !player.getWorld().getUID().equals(target.worldId())) {
                feedback.message(player, "auto-open-skipped");
                return;
            }
            Location targetLocation = target.location(world).add(0.5, 0.5, 0.5);
            double maximum = plugin.runtimeConfig().targetDistance() + 2.0;
            if (player.getLocation().distanceSquared(targetLocation) > maximum * maximum
                    || !plugin.getServer().isOwnedByCurrentRegion(targetLocation)) {
                feedback.message(player, "auto-open-skipped");
                return;
            }
            LockStore.Lookup lookup = lockStore.lookup(world.getBlockAt(target.x(), target.y(), target.z()));
            if (lookup.data() == null || !lookup.data().lockId().equals(lockId)
                    || !sessions.isAuthorized(player.getUniqueId(), lockId, System.currentTimeMillis())) {
                feedback.message(player, "auto-open-skipped");
                return;
            }
            player.openInventory(lookup.group().inventory());
        }, null);
    }

    private boolean canManageCurrent(Player player, LockData snapshot, LockStore.Lookup lookup) {
        if (lookup.data() == null || lookup.corrupt() || !snapshot.sameSecurityVersion(lookup.data())) {
            feedback.failure(player, "target-changed", Map.of());
            return false;
        }
        if (!lookup.data().ownerId().equals(player.getUniqueId())) {
            feedback.failure(player, "not-owner", Map.of());
            return false;
        }
        return true;
    }

    private void onRegion(BlockRef target, Player player, java.util.function.Consumer<Block> operation) {
        if (target == null) {
            feedback.failure(player, "operation-failed", Map.of());
            return;
        }
        World world = target.world();
        if (world == null) {
            feedback.failure(player, "world-unavailable", Map.of());
            return;
        }
        plugin.getServer().getRegionScheduler().execute(plugin, target.location(world),
                () -> operation.accept(world.getBlockAt(target.x(), target.y(), target.z())));
    }

    private LockedTarget ownedTarget(Player player) {
        LockedTarget target = lockedTarget(player, false);
        if (target == null) return null;
        if (!target.data().ownerId().equals(player.getUniqueId())) {
            feedback.failure(player, "not-owner", Map.of());
            return null;
        }
        long remaining = attempts.remainingCooldown(player.getUniqueId(), target.data().lockId(),
                System.currentTimeMillis());
        if (remaining > 0) {
            cooldown(player, remaining);
            return null;
        }
        return target;
    }

    private LockedTarget lockedTarget(Player player, boolean allowCorruptMessage) {
        TargetResult target = target(player);
        if (target == null) return null;
        LockStore.Lookup lookup = lockStore.lookup(target.block());
        if (lookup.corrupt()) {
            feedback.failure(player, "corrupt-lock", Map.of());
            return null;
        }
        if (lookup.data() == null) {
            feedback.message(player, "not-locked");
            return null;
        }
        return new LockedTarget(target.ref(), lookup, lookup.data());
    }

    private TargetResult target(Player player) {
        Block block = player.getTargetBlockExact(plugin.runtimeConfig().targetDistance());
        if (block == null || !lockStore.isContainer(block.getType())) {
            feedback.failure(player, "no-target",
                    Map.of("distance", Integer.toString(plugin.runtimeConfig().targetDistance())));
            return null;
        }
        return new TargetResult(BlockRef.of(block), block);
    }

    private boolean require(Player player, String permission) {
        if (player.hasPermission(permission)) return true;
        feedback.failure(player, "permission-denied", Map.of());
        return false;
    }

    private void putPending(Player player, Action action, BlockRef target, LockData snapshot) {
        pending.put(player.getUniqueId(), new PendingAction(action, target, snapshot,
                System.currentTimeMillis() + plugin.runtimeConfig().dialogTimeoutMillis()));
    }

    private Dialog passcodeDialog(String title, String explanation, String submitLabel, Key action) {
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.GOLD))
                        .body(List.of(DialogBody.plainMessage(Component.text(explanation, NamedTextColor.GRAY))))
                        .inputs(List.of(DialogInput.text("passcode", Component.text("Passcode"))
                                .width(320).maxLength(plugin.runtimeConfig().maxPasscodeLength()).build()))
                        .build())
                .type(DialogType.confirmation(submitButton(submitLabel, action), closeButton())));
    }

    private static ActionButton button(String label, String tooltip, Key action) {
        return ActionButton.create(Component.text(label, NamedTextColor.GOLD), Component.text(tooltip), 150,
                DialogAction.customClick(action, null));
    }

    private static ActionButton submitButton(String label, Key action) {
        return ActionButton.create(Component.text(label, NamedTextColor.GREEN), null, 120,
                DialogAction.customClick(action, null));
    }

    private static ActionButton closeButton() {
        return ActionButton.create(Component.text("Cancel", NamedTextColor.RED), null, 120, null);
    }

    private void invalidPasscode(Player player, PluginConfig config) {
        feedback.failure(player, "invalid-passcode", Map.of(
                "min", Integer.toString(config.minPasscodeLength()),
                "max", Integer.toString(config.maxPasscodeLength())));
    }

    private void cooldown(Player player, long millis) {
        feedback.failure(player, "cooldown", Map.of("seconds", Long.toString(Math.max(1, (millis + 999) / 1_000))));
    }

    private String locationText(BlockRef target) {
        World world = target.world();
        return (world == null ? target.worldId().toString() : world.getName()) + " @ " + target.display();
    }

    private static String shortId(UUID lockId) {
        return lockId.toString().substring(0, 8);
    }

    public void clearPlayer(UUID playerId) {
        pending.remove(playerId);
    }

    public void clearAll() {
        pending.clear();
    }

    private enum Action {
        LOCK(LOCK_SUBMIT), UNLOCK(UNLOCK_SUBMIT), CHANGE(CHANGE_SUBMIT), REMOVE(REMOVE_SUBMIT),
        KEY(KEY_SUBMIT), SETTINGS(SETTINGS_SUBMIT), FORCE_REMOVE(FORCE_REMOVE_SUBMIT);

        private final Key key;

        Action(Key key) {
            this.key = key;
        }

        static Action fromKey(Key key) {
            for (Action action : values()) {
                if (action.key.equals(key)) return action;
            }
            return null;
        }
    }

    private record PendingAction(Action action, BlockRef target, LockData snapshot, long expiresAtMillis) {
    }

    private record TargetResult(BlockRef ref, Block block) {
    }

    private record LockedTarget(BlockRef ref, LockStore.Lookup lookup, LockData data) {
    }
}
