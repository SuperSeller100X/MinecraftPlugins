package dev.superseller.subscriptions.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BillingDecisionTest {

    @Test
    void chargesOnlyWhenBothSidesReady() {
        assertEquals(BillingDecision.Action.CHARGE,
                BillingDecision.decide(true, true, ChargePolicy.CANCEL, false));
        assertEquals(BillingDecision.Action.PAUSE,
                BillingDecision.decide(true, false, ChargePolicy.PAUSE, false));
        assertEquals(BillingDecision.Action.SKIP,
                BillingDecision.decide(false, true, ChargePolicy.SKIP, false));
        assertEquals(BillingDecision.Action.CANCEL,
                BillingDecision.decide(false, false, ChargePolicy.CANCEL, false));
    }

    @Test
    void trialStillRequiresPaymentAbilityAndStock() {
        assertEquals(BillingDecision.Action.TRIAL,
                BillingDecision.decide(true, true, ChargePolicy.CANCEL, true));
        assertEquals(BillingDecision.Action.CANCEL,
                BillingDecision.decide(false, true, ChargePolicy.CANCEL, true));
        assertEquals(BillingDecision.Action.PAUSE,
                BillingDecision.decide(true, false, ChargePolicy.PAUSE, true));
    }

    @Test
    void subscriberOverrideWins() {
        assertEquals(ChargePolicy.SKIP,
                BillingDecision.effective(ChargePolicy.PAUSE, ChargePolicy.SKIP));
        assertEquals(ChargePolicy.PAUSE,
                BillingDecision.effective(ChargePolicy.PAUSE, null));
        assertEquals(ChargePolicy.PAUSE,
                BillingDecision.effective(null, null));
    }
}
