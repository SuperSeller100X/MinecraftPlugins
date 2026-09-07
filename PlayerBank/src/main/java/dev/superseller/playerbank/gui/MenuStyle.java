package dev.superseller.playerbank.gui;

import java.util.Locale;

/**
 * The two menu backends: a classic chest inventory and the native Minecraft
 * menu screens (Paper dialogs). Which one is used is configurable
 * ({@code gui.type}) and — if the server allows it — per player.
 */
public enum MenuStyle {
    CHEST,
    DIALOG;

    /** Lower-case name for messages and storage. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Friendly label for messages. */
    public String label() {
        return switch (this) {
            case CHEST -> "chest inventory";
            case DIALOG -> "menu screens";
        };
    }

    /** Parses "chest" / "dialog" (case-insensitive), or null. */
    public static MenuStyle parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("chest".equals(s) || "inventory".equals(s) || "inv".equals(s)) {
            return CHEST;
        }
        if ("dialog".equals(s) || "dialogs".equals(s) || "menu".equals(s)
                || "screens".equals(s) || "screen".equals(s)) {
            return DIALOG;
        }
        return null;
    }

    /**
     * Whether this server build ships the native dialog API. On Paper 26.2 it
     * always does; the check keeps the AUTO fallback honest on exotic builds.
     */
    public static boolean dialogsSupported() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Resolves a configured type: AUTO picks the native menu screens when
     * available and the chest inventory otherwise.
     */
    public static MenuStyle resolveType(String configured) {
        if (DIALOG.name().equalsIgnoreCase(configured)) {
            return DIALOG;
        }
        if (CHEST.name().equalsIgnoreCase(configured)) {
            return CHEST;
        }
        return dialogsSupported() ? DIALOG : CHEST;
    }
}
