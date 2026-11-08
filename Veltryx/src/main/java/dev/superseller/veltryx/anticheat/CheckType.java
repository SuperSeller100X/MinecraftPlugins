package dev.superseller.veltryx.anticheat;

/**
 * Every anti-cheat detection category. The display name is what staff see in
 * alerts, {@code /sus} and punishment reasons; the group name organises the
 * defaults in {@code config.yml}.
 */
public enum CheckType {
    FLY("Fly", "movement"),
    SPEED("Speed", "movement"),
    JESUS("Jesus", "movement"),
    PHASE("Phase", "movement"),
    TIMER("Timer", "movement"),
    AUTOCLICKER("AutoClicker", "combat"),
    REACH("Reach", "combat"),
    MULTIAURA("MultiAura", "combat"),
    AIM("Aim", "combat"),
    FASTPLACE("FastPlace", "interact"),
    FASTUSE("FastUse", "interact"),
    INTERACT_REACH("InteractReach", "interact"),
    XRAY("Xray", "xray");

    private final String display;
    private final String group;

    CheckType(String display, String group) {
        this.display = display;
        this.group = group;
    }

    public String display() {
        return display;
    }

    public String group() {
        return group;
    }

    /** Config path fragment for this check ({@code movement.fly}). */
    public String configPath() {
        return group + "." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /**
     * Escalation plan for one check: which violation levels trigger an alert,
     * a kick and a ban, and how long the automatic ban lasts.
     *
     * @param banSeconds duration of the automatic ban in seconds
     *                  ({@link Long#MAX_VALUE} = permanent)
     */
    public record Plan(boolean enabled, double alertVl, double kickVl, double banVl,
                       long banSeconds, String banReason) {
    }

    /**
     * Spec-mandated auto-ban duration: xray/esp = 30 days, every other check
     * ("hacking": fly, speed, jesus, phase, timer, combat, interact) =
     * permanent.
     */
    public long specBanSeconds() {
        return this == XRAY ? 30L * 24 * 60 * 60 : Long.MAX_VALUE;
    }
}
