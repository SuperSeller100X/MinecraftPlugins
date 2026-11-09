package dev.superseller.veltryx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.superseller.veltryx.anticheat.CheckType;
import org.junit.jupiter.api.Test;

class CheckTypeTest {

    @Test
    void configPathsMatchConfigYml() {
        assertEquals("movement.fly", CheckType.FLY.configPath());
        assertEquals("combat.autoclicker", CheckType.AUTOCLICKER.configPath());
        assertEquals("interact.interact-reach", CheckType.INTERACT_REACH.configPath());
        assertEquals("xray.xray", CheckType.XRAY.configPath());
    }

    @Test
    void planOrderingIsSane() {
        CheckType.Plan plan = new CheckType.Plan(true, 5, 10, 20, 60L, "r");
        assertTrue(plan.alertVl() < plan.kickVl());
        assertTrue(plan.kickVl() < plan.banVl());
    }
}
