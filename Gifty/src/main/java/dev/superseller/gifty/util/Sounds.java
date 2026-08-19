package dev.superseller.gifty.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Sound helpers.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void click(Player player) {
        play(player, Sound.UI_BUTTON_CLICK);
    }

    public static void success(Player player) {
        play(player, Sound.ENTITY_PLAYER_LEVELUP);
    }

    public static void notify(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_PLING);
    }

    public static void error(Player player) {
        play(player, Sound.ENTITY_ITEM_PICKUP);
    }

    private static void play(Player player, Sound sound) {
        try {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // sound support is best-effort
        }
    }
}
