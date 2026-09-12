package dev.superseller.minecraftwiki.gui;

import org.bukkit.inventory.ItemStack;

/**
 * One clickable slot: the icon shown and what a click does.
 *
 * <p>Only slots that hold a button do anything. Every other slot in a wiki inventory is
 * cancelled by the listener, so nothing can be taken, moved or duplicated.</p>
 */
public record GuiButton(ItemStack icon, Action action) {

    /** What happens when the button is clicked. */
    @FunctionalInterface
    public interface Action {
        void run(ClickInfo click);
    }

    /** A button that does nothing, used for read-only information slots. */
    public static GuiButton display(ItemStack icon) {
        return new GuiButton(icon, click -> { });
    }
}
