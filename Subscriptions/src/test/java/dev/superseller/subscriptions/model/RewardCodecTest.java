package dev.superseller.subscriptions.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RewardCodecTest {

    @Test
    void roundTripsEveryType() {
        assertEquals("MONEY|12.5", Reward.money(12.5d).encode());
        assertEquals(12.5d, Reward.decode("MONEY|12.5").amount(), 1e-9);
        assertTrue(Reward.decode("COMMAND|CONSOLE|say hi {player}").consoleCommand());
        assertEquals("vip", Reward.decode("GROUP|vip").data());
        assertEquals("kit.node", Reward.decode("PERMISSION|kit.node").data());
        assertEquals(RewardType.MESSAGE, Reward.decode("MESSAGE|&aThanks!").type());
        assertEquals(4, Reward.decode("ITEM|abc|4").intAmount());
    }

    @Test
    void planEncodesRewardList() {
        Plan plan = new Plan("demo");
        plan.rewards().add(Reward.money(10d));
        plan.rewards().add(Reward.group("vip"));
        plan.decodeRewards(plan.encodeRewards());
        assertEquals(2, plan.rewards().size());
        assertEquals(RewardType.GROUP, plan.rewards().get(1).type());
    }
}
