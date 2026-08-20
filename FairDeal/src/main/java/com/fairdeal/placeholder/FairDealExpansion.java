package com.fairdeal.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import com.fairdeal.FairDealPlugin;

/** Optional PlaceholderAPI bridge. */
public final class FairDealExpansion extends PlaceholderExpansion {
  private final FairDealPlugin plugin;
  public FairDealExpansion(FairDealPlugin plugin){this.plugin=plugin;}
  @Override public String getIdentifier(){return "fairdeal";}
  @Override public String getAuthor(){return "super_seller";}
  @Override public String getVersion(){return plugin.getDescription().getVersion();}
  @Override public boolean persist(){return true;}
  @Override public String onRequest(OfflinePlayer player,String params){
    if(player==null)return "0";
    return switch(params.toLowerCase()) { case "active" -> String.valueOf(plugin.isTrading(player.getUniqueId())); case "inbox" -> String.valueOf(plugin.recoveryCount(player.getUniqueId())); case "completed" -> String.valueOf(plugin.tradeCount(player.getUniqueId(), "COMPLETED")); case "cancelled" -> String.valueOf(plugin.tradeCount(player.getUniqueId(), "CANCELLED")); case "money_sent" -> String.valueOf(plugin.moneyStat(player.getUniqueId(), true)); case "money_received" -> String.valueOf(plugin.moneyStat(player.getUniqueId(), false)); default -> null; };
  }
}
