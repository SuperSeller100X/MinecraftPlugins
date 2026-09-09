package dev.superseller.playerbank.listener;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.gui.Amount;
import dev.superseller.playerbank.gui.AmountParser;
import dev.superseller.playerbank.gui.BankMenu;
import dev.superseller.playerbank.gui.MenuStyle;
import dev.superseller.playerbank.util.Sounds;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Routes native menu-screen (dialog) button presses to the bank. Quick-amount
 * buttons encode their amount in the action key; the custom-amount screens
 * submit through the dialog response view. Everything that touches storage or
 * the economy is hopped onto the player's owning thread, mirroring the
 * ChestLock dialog controller.
 */
public final class DialogMenuListener implements Listener {

    private final PlayerBankPlugin plugin;
    private final BankMenu menu;

    public DialogMenuListener(PlayerBankPlugin plugin, BankMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @EventHandler
    public void onDialogClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        String namespace = event.getIdentifier().namespace();
        if (!"playerbank".equals(namespace)) {
            return;
        }
        String path = event.getIdentifier().value();
        DialogResponseView view = event.getDialogResponseView();
        // Amount text is read off the response snapshot before hopping threads.
        String amountText = path.endsWith("_submit") && view != null ? view.getText("amount") : null;
        player.getScheduler().run(plugin, task -> handle(player, path, amountText), null);
    }

    private void handle(Player player, String path, String amountText) {
        if (path.equals("menu")) {
            navigate(player, () -> menu.dialogs().openMain(player));
        } else if (path.equals("deposit_input")) {
            navigate(player, () -> menu.dialogs().openDepositInput(player));
        } else if (path.equals("withdraw_input")) {
            navigate(player, () -> menu.dialogs().openWithdrawInput(player));
        } else if (path.equals("interest")) {
            navigate(player, () -> menu.dialogs().openInterest(player));
        } else if (path.equals("logs")) {
            navigate(player, () -> menu.dialogs().openLogs(player, 1));
        } else if (path.equals("style_chest")) {
            switchStyle(player, MenuStyle.CHEST);
        } else if (path.startsWith("deposit/")) {
            quickAmount(player, path.substring("deposit/".length()), true);
        } else if (path.startsWith("withdraw/")) {
            quickAmount(player, path.substring("withdraw/".length()), false);
        } else if (path.equals("deposit_submit")) {
            customAmount(player, amountText, true);
        } else if (path.equals("withdraw_submit")) {
            customAmount(player, amountText, false);
        } else if (path.startsWith("logs_prev/")) {
            // The key already carries the target page.
            int page = parseInt(path.substring("logs_prev/".length()), 1);
            navigate(player, () -> menu.dialogs().openLogs(player, page));
        } else if (path.startsWith("logs_next/")) {
            int page = parseInt(path.substring("logs_next/".length()), 1);
            navigate(player, () -> menu.dialogs().openLogs(player, page));
        }
    }

    private void navigate(Player player, Runnable action) {
        Sounds.play(plugin.bankConfig(), player, "click");
        action.run();
    }

    private void quickAmount(Player player, String token, boolean depositing) {
        Amount amount = AmountParser.fromToken(token);
        if (amount == null) {
            Sounds.play(plugin.bankConfig(), player, "error");
            plugin.messages().send(player, "invalid-amount");
            menu.dialogs().openMain(player);
            return;
        }
        Sounds.play(plugin.bankConfig(), player, "click");
        menu.feedback(player, depositing
                ? plugin.transactor().deposit(player, amount)
                : plugin.transactor().withdraw(player, amount), depositing);
        menu.dialogs().openMain(player);
    }

    private void customAmount(Player player, String amountText, boolean depositing) {
        Amount amount = AmountParser.parse(amountText);
        if (amount == null) {
            Sounds.play(plugin.bankConfig(), player, "error");
            plugin.messages().send(player, "invalid-amount");
            if (depositing) {
                menu.dialogs().openDepositInput(player);
            } else {
                menu.dialogs().openWithdrawInput(player);
            }
            return;
        }
        menu.feedback(player, depositing
                ? plugin.transactor().deposit(player, amount)
                : plugin.transactor().withdraw(player, amount), depositing);
        menu.dialogs().openMain(player);
    }

    private void switchStyle(Player player, MenuStyle target) {
        Sounds.play(plugin.bankConfig(), player, "click");
        if (!menu.setPlayerStyle(player, target)) {
            plugin.messages().send(player, "gui-style-locked",
                    Map.of("style", menu.resolve(player).label()));
            return;
        }
        plugin.messages().send(player, "gui-style-set", Map.of("style", target.label()));
        menu.open(player, target);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
