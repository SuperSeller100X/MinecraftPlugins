package org.bukkit.potion;
import java.util.List;
import java.util.Locale;
public enum PotionType {
    AWKWARD, FIRE_RESISTANCE, HARMING, HEALING, INFESTED, INVISIBILITY, LEAPING,
    LONG_FIRE_RESISTANCE, LONG_INVISIBILITY, LONG_LEAPING, LONG_NIGHT_VISION, LONG_POISON,
    LONG_REGENERATION, LONG_SLOW_FALLING, LONG_SLOWNESS, LONG_STRENGTH, LONG_SWIFTNESS,
    LONG_TURTLE_MASTER, LONG_WATER_BREATHING, LONG_WEAKNESS, LUCK, MUNDANE, NIGHT_VISION,
    OOZING, POISON, REGENERATION, SLOW_FALLING, SLOWNESS, STRENGTH, STRONG_HARMING,
    STRONG_HEALING, STRONG_LEAPING, STRONG_POISON, STRONG_REGENERATION, STRONG_SLOWNESS,
    STRONG_STRENGTH, STRONG_SWIFTNESS, STRONG_TURTLE_MASTER, SWIFTNESS, THICK,
    TURTLE_MASTER, WATER, WATER_BREATHING, WEAKNESS, WEAVING, WIND_CHARGED,
    JUMP_BOOST, SPEED;

    /** Representative 26.2/26.3 built-in effect data for offline UI tests. */
    public List<PotionEffect> getPotionEffects() {
        String name=name(); boolean extended=name.startsWith("LONG_"), strong=name.startsWith("STRONG_");
        String base=extended ? name.substring(5) : strong ? name.substring(7) : name;
        if (base.equals("WATER") || base.equals("AWKWARD") || base.equals("MUNDANE") || base.equals("THICK")) return List.of();
        if (base.equals("TURTLE_MASTER")) {
            int ticks=extended ? 800 : 400;
            return List.of(new PotionEffect(PotionEffectType.SLOW,ticks,strong ? 5 : 3),
                    new PotionEffect(PotionEffectType.RESISTANCE,ticks,strong ? 3 : 2));
        }
        boolean instant=base.equals("HEALING") || base.equals("HARMING");
        String effect=switch(base) { case "SWIFTNESS" -> "speed"; case "LEAPING" -> "jump_boost";
            case "HEALING" -> "instant_health"; case "HARMING" -> "instant_damage"; default -> base.toLowerCase(Locale.ROOT); };
        int ticks=switch(base) {
            case "HEALING", "HARMING" -> 1;
            case "POISON" -> strong ? 432 : extended ? 1800 : 900;
            case "REGENERATION" -> strong ? 450 : extended ? 1800 : 900;
            case "SLOWNESS" -> strong ? 400 : extended ? 4800 : 1800;
            case "WEAKNESS", "SLOW_FALLING" -> extended ? 4800 : 1800;
            case "LUCK" -> 6000;
            default -> strong ? 1800 : extended ? 9600 : 3600;
        };
        int amplifier=strong ? (base.equals("SLOWNESS") ? 3 : 1) : 0;
        return List.of(new PotionEffect(new PotionEffectType(effect,instant),ticks,amplifier));
    }
}
