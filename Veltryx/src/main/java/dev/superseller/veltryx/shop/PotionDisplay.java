package dev.superseller.veltryx.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

/** Human-readable descriptions use the effects supplied by the running server. */
public final class PotionDisplay {
    private PotionDisplay() { }

    /** The standard item's effect-duration scale (splash is maximum on a direct hit). */
    public static double durationScale(String material) {
        return switch (material) {
            case "LINGERING_POTION" -> 0.25;
            case "TIPPED_ARROW" -> 0.125;
            default -> 1.0;
        };
    }

    public static String brewLabel(String key) {
        if (key.startsWith("LONG_")) return "Extended (redstone)";
        if (key.startsWith("STRONG_")) return "Upgraded (glowstone)";
        return "Ordinary";
    }

    /** Format a duration to the tick, preserving half-second and other fractions. */
    public static String duration(int ticks) {
        if (ticks == PotionEffect.INFINITE_DURATION) return "infinite";
        ticks = Math.max(0, ticks);
        int seconds = ticks / 20;
        int hundredths = (ticks % 20) * 5;
        String formatted = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        if (hundredths != 0) {
            formatted += String.format(Locale.ROOT, ".%02d", hundredths);
            if (formatted.endsWith("0")) formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    /** Each line includes effect strength and its actual standard-item duration. */
    public static List<String> effects(String material, String key) {
        PotionType type = ShopExtrasGui.resolvePotion(key);
        if (type == null) return List.of("§cUnavailable on this server");
        List<String> lines = new ArrayList<>();
        try {
            for (PotionEffect effect : type.getPotionEffects()) {
                String effectName = PriceTable.displayName(effect.getType().getKey().getKey().toUpperCase(Locale.ROOT));
                int level = effect.getAmplifier() + 1;
                String length;
                if (effect.getType().isInstant()) length = "instant";
                else if (effect.getDuration() == PotionEffect.INFINITE_DURATION) length = "infinite";
                else {
                    int scaled = Math.max(1, (int) Math.floor(effect.getDuration() * durationScale(material)));
                    length = duration(scaled);
                }
                lines.add("§7" + effectName + " " + level + ": §f" + length);
            }
        } catch (RuntimeException ex) {
            return List.of("§7Effect details unavailable on this server");
        }
        if (lines.isEmpty()) lines.add("§7No status effect");
        if (material.equals("SPLASH_POTION")) lines.add("§8Maximum on direct hit; less with distance.");
        else if (material.equals("LINGERING_POTION")) lines.add("§8Duration per cloud application (¼ of drinkable).");
        else if (material.equals("TIPPED_ARROW")) lines.add("§8Duration on hit (⅛ of drinkable).");
        return lines;
    }
}
