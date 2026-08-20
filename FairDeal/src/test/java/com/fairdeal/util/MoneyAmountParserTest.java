package com.fairdeal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class MoneyAmountParserTest {
  @Test void parsesFriendlySuffixesAndCommas() {
    assertEquals(1_000d, MoneyAmountParser.parse("1k", 0, true));
    assertEquals(1_500_000d, MoneyAmountParser.parse("1.5M", 0, true));
    assertEquals(2_000_000_000d, MoneyAmountParser.parse("2b", 0, true));
    assertEquals(1_500d, MoneyAmountParser.parse("1,500", 0, true));
  }
  @Test void supportsAllAndRejectsInvalidValues() {
    assertEquals(42.5d, MoneyAmountParser.parse("all", 42.5d, true));
    assertTrue(MoneyAmountParser.parse("all", 42.5d, false) < 0);
    assertTrue(MoneyAmountParser.parse("money", 0, true) < 0);
    assertTrue(MoneyAmountParser.parse("1e999", 0, true) < 0);
    assertTrue(MoneyAmountParser.parse("-1k", 0, true) < 0);
    assertTrue(MoneyAmountParser.parse("0", 0, true) == 0);
  }
}
