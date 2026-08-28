package dev.superseller.easymending.model;

import java.util.Locale;

/**
 * Represents the scope/target items for an EasyMending repair action.
 */
public enum RepairScope {
    HAND("Hand", "hand", "h", "main", "mainhand"),
    OFFHAND("Offhand", "offhand", "oh", "off"),
    ARMOR("Armor", "armor", "a", "armour"),
    HOTBAR("Hotbar", "hotbar", "hb", "hot"),
    ALL("All Inventory", "all", "*", "inv", "inventory");

    private final String displayName;
    private final String[] aliases;

    RepairScope(String displayName, String... aliases) {
        this.displayName = displayName;
        this.aliases = aliases;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String[] getAliases() {
        return aliases;
    }

    /**
     * Resolves a string input to a RepairScope, checking name and all aliases.
     *
     * @param input raw input string
     * @return matching RepairScope, or null if no match found
     */
    public static RepairScope fromString(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String clean = input.trim().toLowerCase(Locale.ROOT);
        for (RepairScope scope : values()) {
            if (scope.name().equalsIgnoreCase(clean)) {
                return scope;
            }
            for (String alias : scope.aliases) {
                if (alias.equalsIgnoreCase(clean)) {
                    return scope;
                }
            }
        }
        return null;
    }
}
