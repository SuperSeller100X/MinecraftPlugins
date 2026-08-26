package dev.superseller.playervault.command;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.superseller.playervault.config.Messages;
import dev.superseller.playervault.config.Settings;
import dev.superseller.playervault.gui.GuiLayout;
import dev.superseller.playervault.gui.VaultGui;
import dev.superseller.playervault.model.VaultData;
import dev.superseller.playervault.service.VaultService;
import dev.superseller.playervault.util.Numbers;

/**
 * {@code /playervault} — the player facing command.
 *
 * <p>Every sub-command has a one or two letter alias so it can be used without
 * leaving the keyboard: {@code u}, {@code p}, {@code i}, {@code s}, {@code rl},
 * {@code h}. Running the command with no sub-command opens the vault.
 */
public final class VaultCommand implements CommandExecutor {

    private static final Set<String> CONFIRM_WORDS = Set.of("yes", "y", "confirm", "true");

    private final Messages messages;
    private final VaultService service;
    private final VaultGui gui;
    private final Supplier<Settings> settings;
    private final Runnable reloadHook;

    public VaultCommand(JavaPlugin plugin, Messages messages, VaultService service, VaultGui gui,
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
            requirePlayer(sender, player -> open(player, null));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "upgrade", "up", "u", "buy", "b" -> requirePlayer(sender, player -> upgrade(player, args));
            case "price", "cost", "p" -> requirePlayer(sender, player -> price(player, args));
            case "info", "i", "stats" -> requirePlayer(sender, this::info);
            case "sort", "s", "compact" -> requirePlayer(sender, this::sort);
            case "reload", "rl" -> reload(sender);
            case "help", "h", "?" -> help(sender);
            default -> {
                if (Numbers.isPositiveInt(sub)) {
                    requirePlayer(sender, player -> open(player, sub));
                } else {
                    messages.send(sender, "unknown-subcommand", "%arg%", args[0]);
                }
            }
        }
        return true;
    }

    private void open(Player player, String pageArgument) {
        if (!player.hasPermission("playervault.use")) {
            messages.send(player, "no-permission");
            return;
        }
        int page = -1;
        if (pageArgument != null) {
            if (!Numbers.isPositiveInt(pageArgument)) {
                messages.send(player, "invalid-number", "%arg%", pageArgument);
                return;
            }
            page = Integer.parseInt(pageArgument) - 1;
        }
        gui.open(player, page);
    }

    private void upgrade(Player player, String[] args) {
        if (!player.hasPermission("playervault.upgrade")) {
            messages.send(player, "no-permission");
            return;
        }
        int count = 1;
        if (args.length >= 2) {
            if (CONFIRM_WORDS.contains(args[1].toLowerCase(Locale.ROOT))) {
                service.confirm(player);
                return;
            }
            if (!Numbers.isPositiveInt(args[1])) {
                messages.send(player, "invalid-number", "%arg%", args[1]);
                return;
            }
            count = Integer.parseInt(args[1]);
        }
        boolean confirmed = args.length >= 3 && CONFIRM_WORDS.contains(args[2].toLowerCase(Locale.ROOT));
        if (!confirmed && service.hasPending(player)) {
            service.confirm(player);
            return;
        }
        service.upgrade(player, count, confirmed);
    }

    private void price(Player player, String[] args) {
        if (!player.hasPermission("playervault.price")) {
            messages.send(player, "no-permission");
            return;
        }
        int count = 1;
        if (args.length >= 2) {
            if (!Numbers.isPositiveInt(args[1])) {
                messages.send(player, "invalid-number", "%arg%", args[1]);
                return;
            }
            count = Math.min(Integer.parseInt(args[1]), 25);
        }
        VaultData data = service.vault(player);
        messages.sendPlain(player, "price.header", "%rows%", String.valueOf(data.rows()));
        if (gui.isMaxed(player, data)) {
            messages.sendPlain(player, "price.maxed");
            return;
        }
        List<Double> prices = service.pricing().breakdown(data.purchasedRows(), count);
        for (int i = 0; i < prices.size(); i++) {
            messages.sendPlain(player, "price.line",
                    "%row%", String.valueOf(data.rows() + i + 1),
                    "%price%", service.money(prices.get(i)));
        }
        if (count > 1) {
            messages.sendPlain(player, "price.total",
                    "%count%", String.valueOf(count),
                    "%total%", service.money(service.pricing().total(data.purchasedRows(), count)));
        }
    }

    private void info(Player player) {
        if (!player.hasPermission("playervault.info")) {
            messages.send(player, "no-permission");
            return;
        }
        VaultData data = service.vault(player);
        int pages = new GuiLayout(settings.get().gui().pageRows(), data.rows()).pageCount();
        messages.sendPlain(player, "info.header");
        messages.sendPlain(player, "info.rows",
                "%rows%", String.valueOf(data.rows()),
                "%slots%", String.valueOf(data.capacity()));
        messages.sendPlain(player, "info.used",
                "%used%", String.valueOf(data.usedSlots()),
                "%slots%", String.valueOf(data.capacity()),
                "%free%", String.valueOf(data.capacity() - data.usedSlots()));
        messages.sendPlain(player, "info.pages", "%pages%", String.valueOf(pages));
        messages.sendPlain(player, "info.next", "%price%", service.money(service.nextPrice(data)));
        messages.sendPlain(player, "info.spent", "%spent%", service.money(data.totalSpent()));
        messages.sendPlain(player, "info.economy",
                "%provider%", service.economy().isEnabled() ? service.economy().providerName() : "none");
    }

    private void sort(Player player) {
        if (!player.hasPermission("playervault.sort")) {
            messages.send(player, "no-permission");
            return;
        }
        VaultData data = service.vault(player);
        int used = data.sort();
        service.saveAsync(data);
        gui.repaint(player);
        messages.send(player, "sort.done",
                "%used%", String.valueOf(used),
                "%slots%", String.valueOf(data.capacity()));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("playervault.reload")) {
            messages.send(sender, "no-permission");
            return;
        }
        reloadHook.run();
        messages.send(sender, "reload");
    }

    private void help(CommandSender sender) {
        messages.sendPlain(sender, "help.header");
        messages.sendPlain(sender, "help.open");
        messages.sendPlain(sender, "help.upgrade");
        messages.sendPlain(sender, "help.price");
        messages.sendPlain(sender, "help.info");
        messages.sendPlain(sender, "help.sort");
        if (sender.hasPermission("playervault.reload")) {
            messages.sendPlain(sender, "help.reload");
        }
        messages.sendPlain(sender, "help.footer");
    }

    private void requirePlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
            return;
        }
        messages.send(sender, "players-only");
    }
}
