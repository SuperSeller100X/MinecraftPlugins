package dev.superseller.minecraftwiki.config;

import org.bukkit.Material;

/**
 * One wiki category as declared in {@code categories.yml}.
 *
 * @param id          unique id, also the {@code minecraftwiki.category.<id>} suffix
 * @param title       display title, MiniMessage formatted
 * @param description one line description, MiniMessage formatted
 * @param icon        tile material on the home screen
 * @param enabled     whether the category is shown at all
 * @param order       sort weight on the home screen, lower first
 * @param permission  permission node override, or null to use the generated one
 * @param glowing     whether the tile gets an enchantment glint
 */
public record WikiCategory(
        String id,
        String title,
        String description,
        Material icon,
        boolean enabled,
        int order,
        String permission,
        boolean glowing
) {

    public WikiCategory {
        icon = icon == null ? Material.PAPER : icon;
        title = title == null || title.isBlank() ? id : title;
        description = description == null ? "" : description;
        permission = permission == null || permission.isBlank() ? null : permission;
    }

    public boolean hidden() {
        return !enabled;
    }
}
