package org.bukkit.potion;
public class PotionEffect {
    public static final int INFINITE_DURATION = -1;
    private final PotionEffectType type;
    private final int duration, amplifier;
    private final boolean ambient, particles, icon;
    public PotionEffect(PotionEffectType type, int duration, int amplifier) { this(type,duration,amplifier,false,true,true); }
    public PotionEffect(PotionEffectType type, int duration, int amplifier, boolean ambient, boolean particles) {
        this(type,duration,amplifier,ambient,particles,particles);
    }
    public PotionEffect(PotionEffectType type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
        this.type=type; this.duration=duration; this.amplifier=amplifier; this.ambient=ambient; this.particles=particles; this.icon=icon;
    }
    public int getDuration() { return duration; }
    public boolean isAmbient() { return ambient; }
    public boolean hasParticles() { return particles; }
    public boolean hasIcon() { return icon; }
    public PotionEffectType getType() { return type; }
    public int getAmplifier() { return amplifier; }
}
