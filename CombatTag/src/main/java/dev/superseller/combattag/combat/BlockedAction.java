package dev.superseller.combattag.combat;

/** Actions CombatTag can prevent while a player is combat tagged. */
public enum BlockedAction {
    /** Opening a shop GUI or running a shop command. */
    SHOP("combattag.bypass.shop", "shop"),
    /** Any teleport: /home, /warp, /tpa, /spawn, /back, ender pearls ... */
    TELEPORT("combattag.bypass.teleport", "teleport"),
    /** EasyMending XP repairs. */
    EASYMENDING("combattag.bypass.easymending", "easymending"),
    /** A blacklisted command. */
    COMMAND("combattag.bypass.command", "command"),
    /** Ender pearl / chorus fruit throwing. */
    PEARL("combattag.bypass.teleport", "pearl");

    private final String bypassPermission;
    private final String id;

    BlockedAction(String bypassPermission, String id) {
        this.bypassPermission = bypassPermission;
        this.id = id;
    }

    public String bypassPermission() {
        return bypassPermission;
    }

    public String id() {
        return id;
    }
}
