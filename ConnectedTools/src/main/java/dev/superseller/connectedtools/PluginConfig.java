package dev.superseller.connectedtools;

public class PluginConfig {
    private static boolean bindingNotifications = true;
    private static int maxConnectionsPerPlayer = 50;
    private static int pulseDurationTicks = 2;

    public static boolean isBindingNotifications() {
        return bindingNotifications;
    }

    public static int getMaxConnectionsPerPlayer() {
        return maxConnectionsPerPlayer;
    }

    public static int getPulseDurationTicks() {
        return pulseDurationTicks;
    }

    public static void reload() {
        bindingNotifications = ConnectedToolsPlugin.getInstance().getConfig().getBoolean("notifications", true);
        maxConnectionsPerPlayer = ConnectedToolsPlugin.getInstance().getConfig().getInt("max-connections", 50);
        pulseDurationTicks = ConnectedToolsPlugin.getInstance().getConfig().getInt("pulse-duration-ticks", 2);
    }
}
