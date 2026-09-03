package dev.superseller.swifttpa.request;

/** The kind of a teleport request. */
public enum RequestType {

    /** The sender wants to teleport to the target (sent with /tpa). */
    TPA,

    /** The sender wants the target to teleport to them (sent with /tpahere). */
    TPA_HERE;

    /** Display name used by the admin spy feed. */
    public String spyName() {
        return this == TPA ? "tpa" : "tpahere";
    }
}
