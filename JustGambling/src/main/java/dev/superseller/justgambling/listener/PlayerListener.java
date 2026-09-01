package dev.superseller.justgambling.listener;

import dev.superseller.justgambling.game.GameService;
import dev.superseller.justgambling.input.InputManager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Ensures pending prompts and active Mines boards are resolved safely. */
public final class PlayerListener implements Listener {
    private final GameService games;
    private final InputManager input;

    public PlayerListener(GameService games, InputManager input) {
        this.games = games;
        this.input = input;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        input.cancel(event.getPlayer().getUniqueId());
        games.onQuit(event.getPlayer());
    }
}
