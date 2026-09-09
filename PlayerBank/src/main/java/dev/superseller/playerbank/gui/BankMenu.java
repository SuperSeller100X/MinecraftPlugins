package dev.superseller.playerbank.gui;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.bank.TransferResult;
import dev.superseller.playerbank.config.BankConfig;
import dev.superseller.playerbank.util.Sounds;
import org.bukkit.entity.Player;

/**
 * Front door for every bank menu: resolves which style a player gets
 * (per-player preference when allowed, otherwise the configured type with
 * AUTO falling back to chest inventories when native dialogs are missing),
 * opens it, and centralises post-transfer feedback.
 */
public final class BankMenu {

    private final PlayerBankPlugin plugin;
    private final ChestMenu chest;
    private final DialogMenu dialogs;

    public BankMenu(PlayerBankPlugin plugin) {
        this.plugin = plugin;
        this.chest = new ChestMenu(plugin);
        this.dialogs = new DialogMenu(plugin);
    }

    /** Opens the player's resolved menu. */
    public void open(Player player) {
        open(player, resolve(player));
    }

    public void open(Player player, MenuStyle style) {
        if (style == MenuStyle.DIALOG && !MenuStyle.dialogsSupported()) {
            style = MenuStyle.CHEST;
        }
        if (style == MenuStyle.DIALOG) {
            dialogs.openMain(player);
        } else {
            chest.openMain(player);
        }
    }

    /** Which style this player sees right now. */
    public MenuStyle resolve(Player player) {
        BankConfig cfg = plugin.bankConfig();
        if (cfg.guiPlayerChoice()) {
            MenuStyle preference = MenuStyle.parse(plugin.storage().menuPreference(player.getUniqueId()));
            if (preference != null && (preference != MenuStyle.DIALOG || MenuStyle.dialogsSupported())) {
                return preference;
            }
        }
        return MenuStyle.resolveType(cfg.guiType());
    }

    /**
     * Stores a personal style choice. Returns false (and changes nothing) when
     * the server fixes the style for everyone.
     */
    public boolean setPlayerStyle(Player player, MenuStyle style) {
        if (!plugin.bankConfig().guiPlayerChoice()) {
            return false;
        }
        plugin.storage().setMenuPreference(player.getUniqueId(), style.key());
        return true;
    }

    /** Sends a transfer result as chat feedback plus the matching sound. */
    public void feedback(Player player, TransferResult result, boolean deposit) {
        plugin.messages().send(player, result.messageKey(), result.placeholders());
        Sounds.play(plugin.bankConfig(), player, result.ok() ? (deposit ? "deposit" : "withdraw") : "error");
    }

    public ChestMenu chest() {
        return chest;
    }

    public DialogMenu dialogs() {
        return dialogs;
    }
}
