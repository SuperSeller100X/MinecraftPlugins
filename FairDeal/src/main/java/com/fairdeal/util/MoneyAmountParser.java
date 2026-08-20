package com.fairdeal.util;

import java.util.Locale;

/** Parses player-facing FairDeal money amounts without touching Bukkit/Vault APIs. */
public final class MoneyAmountParser {
  private MoneyAmountParser() { }
  public static double parse(String input, double balance, boolean allowAll) {
    if (input == null) return -1;
    try {
      String value = input.trim().toLowerCase(Locale.ROOT).replace(",", "");
      if (allowAll && value.equals("all")) return balance;
      double multiplier = 1;
      if (value.endsWith("k")) { multiplier=1_000; value=value.substring(0,value.length()-1); }
      else if (value.endsWith("m")) { multiplier=1_000_000; value=value.substring(0,value.length()-1); }
      else if (value.endsWith("b")) { multiplier=1_000_000_000; value=value.substring(0,value.length()-1); }
      else if (value.endsWith("t")) { multiplier=1_000_000_000_000d; value=value.substring(0,value.length()-1); }
      double parsed=Double.parseDouble(value)*multiplier;
      return Double.isFinite(parsed) ? parsed : -1;
    } catch (RuntimeException ignored) { return -1; }
  }
}
