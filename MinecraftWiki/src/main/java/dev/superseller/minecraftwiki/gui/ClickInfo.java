package dev.superseller.minecraftwiki.gui;

import org.bukkit.event.inventory.ClickType;

/**
 * The parts of an inventory click a button is allowed to react to.
 *
 * @param type  the raw click type
 * @param shift whether shift was held
 * @param right whether it was a right click
 */
public record ClickInfo(ClickType type, boolean shift, boolean right) {

    public static ClickInfo of(ClickType type) {
        if (type == null) {
            return new ClickInfo(ClickType.UNKNOWN, false, false);
        }
        return new ClickInfo(type, type.isShiftClick(), type.isRightClick());
    }
}
