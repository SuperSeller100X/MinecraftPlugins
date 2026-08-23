package dev.superseller.teleportsigns.command;

import org.bukkit.permissions.Permissible;

public final class Permissions {

    public static final String USE = "teleportsigns.use";
    public static final String CREATE = "teleportsigns.create";
    public static final String REMOVE = "teleportsigns.remove";
    public static final String INFO = "teleportsigns.info";
    public static final String LIST = "teleportsigns.list";
    public static final String COST = "teleportsigns.cost";
    public static final String RELOAD = "teleportsigns.reload";
    public static final String BYPASS_COOLDOWN = "teleportsigns.bypass.cooldown";
    public static final String BYPASS_WARMUP = "teleportsigns.bypass.warmup";
    public static final String BYPASS_COST = "teleportsigns.bypass.cost";
    public static final String BYPASS_SAFETY = "teleportsigns.bypass.safety";
    public static final String ADMIN = "teleportsigns.admin";
    public static final String ALL = "teleportsigns.*";

    private Permissions() {
    }

    public static boolean has(Permissible permissible, String permission) {
        if (permissible == null || permission == null) {
            return false;
        }
        return permissible.hasPermission(permission)
                || permissible.hasPermission(ADMIN)
                || permissible.hasPermission(ALL);
    }
}
