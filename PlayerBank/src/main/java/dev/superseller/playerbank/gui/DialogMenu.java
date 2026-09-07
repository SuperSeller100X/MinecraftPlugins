package dev.superseller.playerbank.gui;

import dev.superseller.playerbank.PlayerBankPlugin;
import dev.superseller.playerbank.config.BankConfig;
import dev.superseller.playerbank.model.BankAccount;
import dev.superseller.playerbank.model.BankLogEntry;
import dev.superseller.playerbank.util.Sounds;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

/**
 * The native Minecraft menu screens (Paper dialogs): a main menu with balance
 * summary, one-tap quick deposit/withdraw buttons, custom-amount input
 * screens, an interest notice and a paginated log viewer. All labels live in
 * messages.yml; quick amounts mirror the chest menu's configuration.
 */
public final class DialogMenu {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final Key MENU = Key.key("playerbank", "menu");
    private static final Key DEPOSIT_INPUT = Key.key("playerbank", "deposit_input");
    private static final Key WITHDRAW_INPUT = Key.key("playerbank", "withdraw_input");
    private static final Key DEPOSIT_SUBMIT = Key.key("playerbank", "deposit_submit");
    private static final Key WITHDRAW_SUBMIT = Key.key("playerbank", "withdraw_submit");
    private static final Key INTEREST = Key.key("playerbank", "interest");
    private static final Key LOGS = Key.key("playerbank", "logs");
    private static final Key STYLE_CHEST = Key.key("playerbank", "style_chest");

    private final PlayerBankPlugin plugin;

    public DialogMenu(PlayerBankPlugin plugin) {
        this.plugin = plugin;
    }

    // --- screens -------------------------------------------------------------

    public void openMain(Player player) {
        Sounds.play(plugin.bankConfig(), player, "open");
        BankConfig cfg = plugin.bankConfig();
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());

        List<ActionButton> actions = new ArrayList<>();
        List<Amount> quick = cfg.quickAmounts();
        for (Amount amount : quick) {
            Map<String, String> ph = Map.of("amount", amount.label());
            actions.add(action(
                    amount.isAll() ? "gui.dialog.deposit-all-label" : "gui.dialog.deposit-label",
                    amount.isAll() ? "gui.dialog.deposit-all-tooltip" : "gui.dialog.deposit-tooltip",
                    ph, Key.key("playerbank", "deposit/" + amount.token())));
        }
        for (Amount amount : quick) {
            Map<String, String> ph = Map.of("amount", amount.label());
            actions.add(action(
                    amount.isAll() ? "gui.dialog.withdraw-all-label" : "gui.dialog.withdraw-label",
                    amount.isAll() ? "gui.dialog.withdraw-all-tooltip" : "gui.dialog.withdraw-tooltip",
                    ph, Key.key("playerbank", "withdraw/" + amount.token())));
        }
        actions.add(action("gui.dialog.custom-deposit-label", "gui.dialog.custom-deposit-tooltip",
                Map.of(), DEPOSIT_INPUT));
        actions.add(action("gui.dialog.custom-withdraw-label", "gui.dialog.custom-withdraw-tooltip",
                Map.of(), WITHDRAW_INPUT));
        actions.add(action("gui.dialog.interest-label", "gui.dialog.interest-tooltip",
                Map.of(), INTEREST));
        actions.add(action("gui.dialog.logs-label", "gui.dialog.logs-tooltip", Map.of(), LOGS));
        if (cfg.guiPlayerChoice()) {
            actions.add(action("gui.dialog.style-chest-label", "gui.dialog.style-tooltip",
                    Map.of(), STYLE_CHEST));
        }

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(plugin.messages().gui("gui.dialog.title", Map.of()))
                        .body(List.of(DialogBody.plainMessage(
                                body("gui.dialog.body", placeholders(player, acc)))))
                        .build())
                .type(DialogType.multiAction(actions, cancelButton(), 2)));
        player.showDialog(dialog);
    }

    public void openDepositInput(Player player) {
        openAmountInput(player, true);
    }

    public void openWithdrawInput(Player player) {
        openAmountInput(player, false);
    }

    private void openAmountInput(Player player, boolean depositing) {
        String titleKey = depositing ? "gui.dialog.deposit-title" : "gui.dialog.withdraw-title";
        String bodyKey = depositing ? "gui.dialog.deposit-input-body" : "gui.dialog.withdraw-input-body";
        Key submit = depositing ? DEPOSIT_SUBMIT : WITHDRAW_SUBMIT;
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(plugin.messages().gui(titleKey, Map.of()))
                        .body(List.of(DialogBody.plainMessage(body(bodyKey, Map.of()))))
                        .inputs(List.of(DialogInput.text("amount",
                                        plugin.messages().gui("gui.dialog.amount-label", Map.of()))
                                .width(320).maxLength(32).build()))
                        .build())
                .type(DialogType.confirmation(submitButton(submit), cancelButton())));
        player.showDialog(dialog);
    }

    public void openInterest(Player player) {
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(plugin.messages().gui("gui.dialog.interest-title", Map.of()))
                        .body(List.of(DialogBody.plainMessage(
                                body("gui.dialog.interest-body", placeholders(player, acc)))))
                        .build())
                .type(DialogType.notice(ActionButton.create(
                        plugin.messages().gui("gui.dialog.back-label", Map.of()), null, 150,
                        DialogAction.customClick(MENU, null)))));
        player.showDialog(dialog);
    }

    public void openLogs(Player player, int page) {
        BankAccount acc = plugin.storage().getOrCreate(player.getUniqueId(), player.getName());
        BankConfig cfg = plugin.bankConfig();
        int perPage = cfg.logPageSize();
        int pages = acc.logPages(perPage);
        int current = Math.min(Math.max(1, page), pages);
        List<BankLogEntry> entries = acc.logPage(current, perPage);

        TextComponent.Builder body = Component.text();
        if (entries.isEmpty()) {
            body.append(plugin.messages().gui("logs-empty", Map.of()));
        } else {
            for (BankLogEntry entry : entries) {
                body.append(plugin.messages().gui("gui.dialog.logs-line", Map.of(
                                "time", TIME.format(Instant.ofEpochMilli(entry.time())),
                                "type", entry.type(),
                                "amount", plugin.vault().format(entry.amount()),
                                "note", entry.note())))
                        .append(Component.newline());
            }
        }

        List<ActionButton> actions = new ArrayList<>();
        actions.add(ActionButton.create(
                plugin.messages().gui("gui.dialog.previous-page-label", Map.of()), null, 150,
                DialogAction.customClick(Key.key("playerbank", "logs_prev/" + (current - 1)), null)));
        actions.add(ActionButton.create(
                plugin.messages().gui("gui.dialog.next-page-label", Map.of()), null, 150,
                DialogAction.customClick(Key.key("playerbank", "logs_next/" + (current + 1)), null)));
        actions.add(ActionButton.create(
                plugin.messages().gui("gui.dialog.back-label", Map.of()), null, 150,
                DialogAction.customClick(MENU, null)));

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(plugin.messages().gui("gui.dialog.logs-title", Map.of(
                                "page", String.valueOf(current), "pages", String.valueOf(pages))))
                        .body(List.of(DialogBody.plainMessage(body.build())))
                        .build())
                .type(DialogType.multiAction(actions, cancelButton(), 2)));
        player.showDialog(dialog);
    }

    // --- helpers -------------------------------------------------------------

    private Map<String, String> placeholders(Player player, BankAccount acc) {
        BankConfig cfg = plugin.bankConfig();
        Map<String, String> ph = new HashMap<>();
        ph.put("player", player.getName());
        ph.put("bank", plugin.vault().format(acc.balance()));
        ph.put("wallet", plugin.vault().format(plugin.vault().wallet(player)));
        ph.put("rate", String.valueOf(cfg.ratePercent()));
        ph.put("interval", cfg.intervalDescription());
        ph.put("next", cfg.interestEnabled() ? plugin.interest().nextRunHuman() : "-");
        ph.put("minbal", plugin.vault().format(cfg.minBalance()));
        ph.put("cap", cfg.maxBalance() > 0 ? plugin.vault().format(cfg.maxBalance()) : "∞");
        return ph;
    }

    /** Joins a messages.yml string list into one dialog body component. */
    private Component body(String key, Map<String, String> ph) {
        TextComponent.Builder body = Component.text();
        List<Component> lines = plugin.messages().guiList(key, ph);
        for (int i = 0; i < lines.size(); i++) {
            body.append(lines.get(i));
            if (i < lines.size() - 1) {
                body.append(Component.newline());
            }
        }
        return body.build();
    }

    private ActionButton action(String labelKey, String tooltipKey, Map<String, String> ph, Key target) {
        return ActionButton.create(plugin.messages().gui(labelKey, ph),
                plugin.messages().gui(tooltipKey, ph), 170,
                DialogAction.customClick(target, null));
    }

    private ActionButton submitButton(Key target) {
        return ActionButton.create(plugin.messages().gui("gui.dialog.submit-label", Map.of()),
                null, 130, DialogAction.customClick(target, null));
    }

    private ActionButton cancelButton() {
        return ActionButton.create(plugin.messages().gui("gui.dialog.cancel-label", Map.of()),
                null, 130, null);
    }
}
