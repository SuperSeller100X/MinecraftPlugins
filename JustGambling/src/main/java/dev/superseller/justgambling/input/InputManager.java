package dev.superseller.justgambling.input;

import dev.superseller.justgambling.config.Messages;
import dev.superseller.justgambling.economy.EconomyService;
import dev.superseller.justgambling.game.GameService;
import dev.superseller.justgambling.model.GameType;
import dev.superseller.justgambling.model.RiskTier;
import dev.superseller.justgambling.scheduler.PlatformScheduler;
import dev.superseller.justgambling.util.Numbers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

/** Chat prompt state used by GUI custom stake entry. */
public final class InputManager {
    private final GameService games;
    private final EconomyService economy;
    private final Messages messages;
    private final ConcurrentHashMap<UUID, PendingAmount> pending = new ConcurrentHashMap<>();

    public InputManager(GameService games, EconomyService economy, Messages messages) {
        this.games = games;
        this.economy = economy;
        this.messages = messages;
    }

    public void beginAmount(Player player, GameType game, RiskTier risk, String option) {
        if (player == null || game == null) {
            return;
        }
        pending.put(player.getUniqueId(), new PendingAmount(game, risk, option));
        player.closeInventory();
        messages.send(player, "amount-prompt", Map.of("game", game.displayName()));
    }

    public boolean hasPending(UUID playerId) {
        return playerId != null && pending.containsKey(playerId);
    }

    public void onChatAsync(Player player, String input) {
        if (player == null) {
            return;
        }
        PendingAmount request = pending.remove(player.getUniqueId());
        if (request == null) {
            return;
        }
        PlatformScheduler.runEntity(player, () -> complete(player, request, input));
    }

    public void cancel(UUID playerId) {
        if (playerId != null) {
            pending.remove(playerId);
        }
    }

    private void complete(Player player, PendingAmount request, String input) {
        if (input != null && input.trim().equalsIgnoreCase("cancel")) {
            messages.send(player, "input-cancelled");
            return;
        }
        double balance = economy.balance(player);
        var amount = Numbers.parseAmount(input, balance);
        if (amount.isEmpty()) {
            messages.send(player, "invalid-amount");
            return;
        }
        games.play(player, request.game, amount.get(), request.risk, request.option);
    }

    private record PendingAmount(GameType game, RiskTier risk, String option) {
    }
}
