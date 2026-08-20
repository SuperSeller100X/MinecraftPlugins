package com.fairdeal.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired at the security boundaries of a FairDeal trade. */
public final class FairDealTradeEvent extends Event implements Cancellable {
  public enum Stage { REQUEST, ACCEPT, LOCK, UNLOCK, CONFIRM, COMPLETE, CANCEL }
  private static final HandlerList HANDLERS = new HandlerList();
  private final Player first, second; private final Stage stage; private final String reason; private boolean cancelled;
  public FairDealTradeEvent(Player first, Player second, Stage stage, String reason) { this.first=first; this.second=second; this.stage=stage; this.reason=reason; }
  public Player getFirst(){return first;} public Player getSecond(){return second;} public Stage getStage(){return stage;} public String getReason(){return reason;}
  @Override public boolean isCancelled(){return cancelled;} @Override public void setCancelled(boolean cancelled){this.cancelled=cancelled;}
  @Override public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;}
}
