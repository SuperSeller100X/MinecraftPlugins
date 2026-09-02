package dev.superseller.rapidhoppers.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.superseller.rapidhoppers.RapidHoppersPlugin;
import dev.superseller.rapidhoppers.config.Settings;
import dev.superseller.rapidhoppers.config.Settings.ContainerType;
import dev.superseller.rapidhoppers.engine.TransferMath;
import dev.superseller.rapidhoppers.util.Sounds;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /rapidhoppersadmin} — administration.
 *
 * <p>Aliases {@code /rhadmin}, {@code /rha}; every sub-command has a two-letter
 * short form (rl, t, sp, st, w, th, l, d, i, s, g). Each sub-command is guarded
 * by its own permission node and every change is written back to config.yml.</p>
 */
public final class RapidHoppersAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "reload", "rl", "toggle", "t", "speed", "sp", "stack", "st",
            "world", "w", "throttle", "th", "limit", "l", "debug", "d",
            "info", "i", "stats", "s", "gui", "g", "help", "h");

    private static final List<String> THROTTLE_ARGS = List.of("on", "off", "soft", "hard");

    private final RapidHoppersPlugin plugin;
    private final StatusRenderer renderer;

    public RapidHoppersAdminCommand(RapidHoppersPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new StatusRenderer(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.messages().sendHelp(sender, "admin");
            return true;
        }
        Settings settings = plugin.settings();
        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reload", "rl" -> {
                if (denied(sender, Permissions.ADMIN_RELOAD)) {
                    return true;
                }
                long start = System.nanoTime();
                try {
                    plugin.reloadAll();
                    long ms = (System.nanoTime() - start) / 1_000_000L;
                    plugin.messages().send(sender, "general.reloaded", Map.of("ms", String.valueOf(ms)));
                    success(sender);
                } catch (RuntimeException ex) {
                    plugin.messages().send(sender, "general.reload-failed",
                            Map.of("error", String.valueOf(ex.getMessage())));
                    error(sender);
                }
            }
            case "toggle", "t" -> {
                if (denied(sender, Permissions.ADMIN_TOGGLE)) {
                    return true;
                }
                if (args.length >= 2) {
                    ContainerType type = ContainerType.byName(args[1]);
                    if (type == null) {
                        plugin.messages().send(sender, "general.unknown-command", Map.of("label", label));
                        error(sender);
                        return true;
                    }
                    settings.setContainerEnabled(type, !settings.isContainerEnabled(type));
                    plugin.messages().send(sender, "admin.container-toggled", Map.of(
                            "type", type.display(), "state", tag(settings.isContainerEnabled(type))));
                    toggleSound(sender, settings.isContainerEnabled(type));
                } else {
                    settings.setEnabled(!settings.isEnabled());
                    plugin.engine().refresh();
                    plugin.messages().send(sender, "admin.toggled",
                            Map.of("state", tag(settings.isEnabled())));
                    toggleSound(sender, settings.isEnabled());
                }
                plugin.configService().save();
            }
            case "speed", "sp" -> {
                if (denied(sender, Permissions.ADMIN_SPEED)) {
                    return true;
                }
                Integer value = parseInt(sender, args, 1, Settings.MIN_INTERVAL, Settings.MAX_INTERVAL);
                if (value == null) {
                    return true;
                }
                settings.setIntervalTicks(value);
                plugin.engine().refresh();
                plugin.configService().save();
                plugin.messages().send(sender, "admin.speed-set", Map.of(
                        "interval", String.valueOf(settings.getIntervalTicks()),
                        "speed", String.valueOf(TransferMath.round1(settings.speedFactor()))));
                success(sender);
            }
            case "stack", "st" -> {
                if (denied(sender, Permissions.ADMIN_STACK)) {
                    return true;
                }
                Integer value = parseInt(sender, args, 1, Settings.MIN_STACK, Settings.MAX_STACK);
                if (value == null) {
                    return true;
                }
                settings.setItemsPerTransfer(value);
                plugin.configService().save();
                plugin.messages().send(sender, "admin.stack-set",
                        Map.of("stack", String.valueOf(settings.getItemsPerTransfer())));
                success(sender);
            }
            case "world", "w" -> {
                if (denied(sender, Permissions.ADMIN_WORLD)) {
                    return true;
                }
                String worldName;
                if (args.length >= 2) {
                    worldName = args[1];
                } else if (sender instanceof Player player) {
                    worldName = player.getWorld().getName();
                } else {
                    plugin.messages().send(sender, "admin.unknown-world", Map.of("world", "?"));
                    return true;
                }
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.messages().send(sender, "admin.unknown-world", Map.of("world", worldName));
                    error(sender);
                    return true;
                }
                plugin.configService().toggleWorld(world.getName());
                boolean accelerated = settings.appliesToWorld(world.getName());
                plugin.messages().send(sender, "admin.world-toggled",
                        Map.of("world", world.getName(), "state", tag(accelerated)));
                toggleSound(sender, accelerated);
            }
            case "throttle", "th" -> {
                if (denied(sender, Permissions.ADMIN_THROTTLE)) {
                    return true;
                }
                handleThrottle(sender, args, settings);
            }
            case "limit", "l" -> {
                if (denied(sender, Permissions.ADMIN_LIMIT)) {
                    return true;
                }
                Integer value = parseInt(sender, args, 1, 0, 100000);
                if (value == null) {
                    return true;
                }
                settings.setMaxContainersPerChunk(value);
                plugin.configService().save();
                plugin.messages().send(sender, "admin.limit-set",
                        Map.of("limit", String.valueOf(settings.getMaxContainersPerChunk())));
                success(sender);
            }
            case "debug", "d" -> {
                if (denied(sender, Permissions.ADMIN_DEBUG)) {
                    return true;
                }
                settings.setDebug(!settings.isDebug());
                plugin.configService().save();
                plugin.messages().send(sender, "admin.debug-set", Map.of("state", tag(settings.isDebug())));
                toggleSound(sender, settings.isDebug());
            }
            case "info", "i" -> renderer.sendInfo(sender);
            case "stats", "s" -> renderer.sendStats(sender);
            case "gui", "g" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "general.players-only");
                    return true;
                }
                plugin.panel().open(player, false);
            }
            case "help", "h", "?" -> plugin.messages().sendHelp(sender, "admin");
            default -> {
                plugin.messages().send(sender, "general.unknown-command", Map.of("label", label));
                error(sender);
            }
        }
        return true;
    }

    private void handleThrottle(CommandSender sender, String[] args, Settings settings) {
        if (args.length < 2) {
            settings.setThrottleEnabled(!settings.isThrottleEnabled());
        } else {
            String mode = args[1].toLowerCase(Locale.ROOT);
            switch (mode) {
                case "on", "enable", "true" -> settings.setThrottleEnabled(true);
                case "off", "disable", "false" -> settings.setThrottleEnabled(false);
                case "soft", "hard" -> {
                    Double tps = parseDouble(sender, args, 2, 1.0D, 20.0D);
                    if (tps == null) {
                        return;
                    }
                    if ("soft".equals(mode)) {
                        settings.setThrottleSoftTps(tps);
                    } else {
                        settings.setThrottleHardTps(tps);
                    }
                    plugin.configService().save();
                    plugin.throttle().start();
                    plugin.messages().send(sender, "admin.throttle-tps-set", Map.of(
                            "soft", String.valueOf(settings.getThrottleSoftTps()),
                            "hard", String.valueOf(settings.getThrottleHardTps())));
                    success(sender);
                    return;
                }
                default -> {
                    plugin.messages().send(sender, "general.unknown-command", Map.of("label", "rha throttle"));
                    error(sender);
                    return;
                }
            }
        }
        plugin.configService().save();
        plugin.throttle().start();
        plugin.messages().send(sender, "admin.throttle-set",
                Map.of("state", tag(settings.isThrottleEnabled())));
        toggleSound(sender, settings.isThrottleEnabled());
    }

    // --- helpers ------------------------------------------------------------

    private Integer parseInt(CommandSender sender, String[] args, int index, int min, int max) {
        if (args.length <= index) {
            plugin.messages().send(sender, "general.out-of-range",
                    Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
            error(sender);
            return null;
        }
        int value;
        try {
            value = Integer.parseInt(args[index].trim());
        } catch (NumberFormatException ex) {
            plugin.messages().send(sender, "general.invalid-number", Map.of("input", args[index]));
            error(sender);
            return null;
        }
        if (value < min || value > max) {
            plugin.messages().send(sender, "general.out-of-range",
                    Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
            error(sender);
            return null;
        }
        return value;
    }

    private Double parseDouble(CommandSender sender, String[] args, int index, double min, double max) {
        if (args.length <= index) {
            plugin.messages().send(sender, "general.out-of-range",
                    Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
            error(sender);
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(args[index].trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            plugin.messages().send(sender, "general.invalid-number", Map.of("input", args[index]));
            error(sender);
            return null;
        }
        if (value < min || value > max) {
            plugin.messages().send(sender, "general.out-of-range",
                    Map.of("min", String.valueOf(min), "max", String.valueOf(max)));
            error(sender);
            return null;
        }
        return value;
    }

    private String tag(boolean on) {
        return plugin.messages().raw(on ? "status.enabled" : "status.disabled");
    }

    private boolean denied(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission(Permissions.ADMIN)) {
            return false;
        }
        plugin.messages().send(sender, "general.no-permission");
        error(sender);
        return true;
    }

    private void success(CommandSender sender) {
        if (sender instanceof Player player) {
            plugin.sounds().play(player, Sounds.SUCCESS);
        }
    }

    private void error(CommandSender sender) {
        if (sender instanceof Player player) {
            plugin.sounds().play(player, Sounds.ERROR);
        }
    }

    private void toggleSound(CommandSender sender, boolean on) {
        if (sender instanceof Player player) {
            plugin.sounds().playToggle(player, on);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return RapidHoppersCommand.filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "toggle", "t" -> {
                    List<String> types = new ArrayList<>();
                    for (ContainerType type : ContainerType.values()) {
                        types.add(type.path());
                    }
                    return RapidHoppersCommand.filter(types, args[1]);
                }
                case "speed", "sp" -> {
                    return RapidHoppersCommand.filter(List.of("1", "2", "3", "4", "6", "8"), args[1]);
                }
                case "stack", "st" -> {
                    return RapidHoppersCommand.filter(List.of("1", "4", "8", "16", "32", "64"), args[1]);
                }
                case "world", "w" -> {
                    List<String> worlds = new ArrayList<>();
                    for (World world : Bukkit.getWorlds()) {
                        worlds.add(world.getName());
                    }
                    return RapidHoppersCommand.filter(worlds, args[1]);
                }
                case "throttle", "th" -> {
                    return RapidHoppersCommand.filter(THROTTLE_ARGS, args[1]);
                }
                case "limit", "l" -> {
                    return RapidHoppersCommand.filter(List.of("0", "32", "64", "96", "128", "256"), args[1]);
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 3 && ("throttle".equals(sub) || "th".equals(sub))) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if ("soft".equals(mode) || "hard".equals(mode)) {
                return RapidHoppersCommand.filter(List.of("12", "14", "16", "18", "19.5"), args[2]);
            }
        }
        return List.of();
    }
}
