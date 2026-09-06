package dev.superseller.hourglass.api;

import java.util.ArrayList;
import java.util.List;

import dev.superseller.hourglass.data.Milestone;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player crosses a configured playtime milestone.
 *
 * <p>Cancelling suppresses the chat message, the broadcast, the sound, the title
 * and the configured commands — the milestone is still recorded as reached, so a
 * cancelled event does not cause the player to be awarded again later. The award
 * itself is what other plugins should listen for.
 *
 * <p>Runs on the global region thread (Paper: the main thread). Handlers must
 * not touch another player's world state directly; schedule it yourself.
 */
public class PlayerMilestoneReachEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final java.util.UUID playerId;
    private final Milestone milestone;
    private final long seconds;
    private boolean cancelled;
    private boolean broadcast;
    private final List<String> commands;

    public PlayerMilestoneReachEvent(@Nullable Player player, @NotNull Milestone milestone,
                                     long seconds, boolean broadcast, @NotNull List<String> commands) {
        this.player = player;
        this.playerId = player == null ? null : player.getUniqueId();
        this.milestone = milestone;
        this.seconds = seconds;
        this.broadcast = broadcast;
        this.commands = new ArrayList<>(commands);
    }

    /**
     * The player who reached the milestone, or {@code null} when the award was
     * calculated for an offline player (for example after an admin edit).
     */
    @Nullable
    public Player getPlayer() {
        return player;
    }

    /** The offline-safe id of the player. */
    @Nullable
    public java.util.UUID getPlayerId() {
        return playerId;
    }

    /** The milestone definition from {@code config.yml}. */
    @NotNull
    public Milestone getMilestone() {
        return milestone;
    }

    /** The player's playtime that triggered the award. */
    public long getSeconds() {
        return seconds;
    }

    /** Whether every online player hears about it. */
    public boolean isBroadcast() {
        return broadcast;
    }

    public void setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    /** Mutable copy of the console commands that will run. */
    @NotNull
    public List<String> getCommands() {
        return commands;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
