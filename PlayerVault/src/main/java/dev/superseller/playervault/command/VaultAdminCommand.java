package dev.superseller.playervault.command;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.playervault.config.Messages;
import dev.superseller.playervault.config.Settings;
import dev.superseller.playervault.gui.VaultGui;
import dev.superseller.playervault.model.VaultData;
import dev.superseller.playervault.service.VaultService;
import dev.superseller.playervault.util.Numbers;

/**
 * {@code /playervaultadmin} — staff management of any player's vault.
 *
 * <p>Every sub-command has a short alias: {@code o}, {@code r}, {@code ar},
 * {@code x}, {@code c}, {@code i}, {@code p}, {@code rl}, {@code h}.
 *
 * <p>Row changes work on offline players too. When the target is online, a stale GUI
 * is closed and any items that no longer fit are dropped at their feet instead of
 * being deleted.
 */
public final class VaultAdminCommand implements CommandExecutor {

    private final Messages messages;
    private final VaultService service;
    private final VaultGui gui;
    private final Supplier<Settings> settings;
    private final Runnable reloadHook;

    public VaultAdminCommand(JavaPlugin plugin, Messages messages, VaultService service, VaultGui gui,
                             Supplier<Settings> settings, Runnable reloadHook) {
        this.messages = messages;
        this.service = service;
        this.gui = gui;
        this.settings = settings;
        this.reloadHook = reloadHook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "open", "o", "view" -> withTarget(sender, args, 1, "playervault.admin.open", this::open);
            case "rows", "setrows", "r" -> rows(sender, args);
            case "addrows", "give", "ar" -> addRows(sender, args);
            case "reset", "x" -> withTarget(sender, args, 1, "playervault.admin.reset", this::reset);
            case "clear", "empty", "c" -> withTarget(sender, args, 1, "playervault.admin.clear", this::clear);
            case "info", "i", "inspect" -> withTarget(sender, args, 1, "playervault.admin.info", this::info);
            case "price", "cost", "p" -> price(sender, args);
            case "reload", "rl" -> {
                if (sender.hasPermission("playervault.reload")) {
                    reloadHook.run();
                    messages.send(sender, "reload");
                } else {
                    messages.send(sender, "no-permission");
                }
            }
            case "help", "h", "?" -> help(sender);
            default -> messages.send(sender, "unknown-subcommand", "%arg%", args[0]);
        }
        return true;
    }

    // ── sub-commands ─────────────────────────────────────────────────────────

    private void open(CommandSender sender, OfflinePlayer target) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        gui.openAdmin(player, target.getUniqueId(), 0);
        messages.send(sender, "admin-messages.opened", "%player%", String.valueOf(target.getName()));
    }

    private void rows(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playervault.admin.rows")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 3 || !Numbers.isPositiveInt(args[2])) {
            messages.send(sender, args.length < 3 ? "unknown-subcommand" : "invalid-number",
                    "%arg%", args.length < 3 ? "rows" : args[2]);
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", "%player%", args[1]);
            return;
        }
        service.setRows(target, Integer.parseInt(args[2]), sender);
    }

    private void addRows(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playervault.admin.rows")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "unknown-subcommand", "%arg%", "addrows");
            return;
        }
        int count = args.length >= 3 ? Numbers.integer(args[2], 1) : 1;
        if (count == 0) {
            messages.send(sender, "negative-number");
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", "%player%", args[1]);
            return;
        }
        service.addRows(target, count, sender);
    }

    private void reset(CommandSender sender, OfflinePlayer target) {
        service.reset(target, sender);
    }

    private void clear(CommandSender sender, OfflinePlayer target) {
        service.clear(target, sender);
    }

    private void info(CommandSender sender, OfflinePlayer target) {
        VaultData data = service.vaultById(target.getUniqueId());
        messages.sendPlain(sender, "admin-messages.info-header", "%player%", String.valueOf(target.getName()));
        messages.sendPlain(sender, "info.rows",
                "%rows%", String.valueOf(data.rows()),
                "%slots%", String.valueOf(data.capacity()));
        messages.sendPlain(sender, "info.used",
                "%used%", String.valueOf(data.usedSlots()),
                "%slots%", String.valueOf(data.capacity()),
                "%free%", String.valueOf(data.capacity() - data.usedSlots()));
        messages.sendPlain(sender, "info.spent", "%spent%", service.money(data.totalSpent()));
        messages.sendPlain(sender, "info.next", "%price%", service.money(service.nextPrice(data)));
    }

    /**
     * Shows the ladder from the very first purchase, which is what an admin needs in
     * order to judge whether the configured base price and multiplier feel right.
     */
    private void price(CommandSender sender, String[] args) {
        int count = 1;
        if (args.length >= 2) {
            if (!Numbers.isPositiveInt(args[1])) {
                messages.send(sender, "invalid-number", "%arg%", args[1]);
                return;
            }
            count = Math.min(Integer.parseInt(args[1]), 25);
        }
        int startingRows = settings.get().vault().startingRows();
        messages.sendPlain(sender, "price.header", "%rows%", String.valueOf(startingRows));
        List<Double> prices = service.pricing().breakdown(0, count);
        for (int i = 0; i < prices.size(); i++) {
            messages.sendPlain(sender, "price.line",
                    "%row%", String.valueOf(startingRows + i + 1),
                    "%price%", service.money(prices.get(i)));
        }
        if (count > 1) {
            messages.sendPlain(sender, "price.total",
                    "%count%", String.valueOf(count),
                    "%total%", service.money(service.pricing().total(0, count)));
        }
    }

    private void help(CommandSender sender) {
        messages.sendPlain(sender, "admin.help.header");
        messages.sendPlain(sender, "admin.help.open");
        messages.sendPlain(sender, "admin.help.rows");
        messages.sendPlain(sender, "admin.help.addrows");
        messages.sendPlain(sender, "admin.help.reset");
        messages.sendPlain(sender, "admin.help.clear");
        messages.sendPlain(sender, "admin.help.info");
        messages.sendPlain(sender, "admin.help.price");
        messages.sendPlain(sender, "admin.help.reload");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void withTarget(CommandSender sender, String[] args, int index, String permission,
                            java.util.function.BiConsumer<CommandSender, OfflinePlayer> action) {
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length <= index) {
            messages.send(sender, "unknown-subcommand", "%arg%", args[0]);
            return;
        }
        OfflinePlayer target = resolve(args[index]);
        if (target == null) {
            messages.send(sender, "player-not-found", "%player%", args[index]);
            return;
        }
        action.accept(sender, target);
    }

    /**
     * Resolves a name to a player without ever blocking on the Mojang session
     * service.
     *
     * <p>Online players are matched first. For everybody else Paper's cached lookup
     * is called reflectively: it is part of the Paper API but not of the Bukkit API,
     * and reaching it reflectively keeps the plugin compiling against either while
     * degrading to "player must be online" on a server that lacks it.
     */
    public static OfflinePlayer resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        try {
            Method method = Server.class.getMethod("getOfflinePlayerIfCached", String.class);
            Object value = method.invoke(Bukkit.getServer(), name);
            return value instanceof OfflinePlayer offline ? offline : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }
}
