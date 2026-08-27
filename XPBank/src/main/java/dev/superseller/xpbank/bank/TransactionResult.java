package dev.superseller.xpbank.bank;

/**
 * Outcome of a bank operation. Carries the effective amount that moved and the
 * player's resulting XP-in-hand and banked balance so callers can render a
 * message without re-reading state.
 */
public record TransactionResult(Status status, long amount, long onHand, long banked) {

    public enum Status {
        OK,
        NOT_ENOUGH_XP,
        NOT_ENOUGH_BANK,
        BELOW_MINIMUM,
        ZERO,
        DISABLED,
        SELF,
        TARGET_UNKNOWN
    }

    public boolean ok() {
        return status == Status.OK;
    }
}
