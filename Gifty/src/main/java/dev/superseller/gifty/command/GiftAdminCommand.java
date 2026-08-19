package dev.superseller.gifty.command;

import dev.superseller.gifty.GiftyPlugin;
import dev.superseller.gifty.GuiSessionManager;
import dev.superseller.gifty.config.GiftyConfig;
import dev.superseller.gifty.economy.EconomyHook;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /giftadmin — staff commands: reload, view, clear, expire, info.
 */
public final class GiftAdminCommand implements CommandExecutor, TabCompleter {

    private final GiftyPlugin plugin;
    private final GiftyConfig config;
    private final GuiSessionManager sessions;
    private final EconomyHook economy;

    public GiftAdminCommand(GiftyPlugin plugin, GiftyConfig config, GuiSessionManager sessions,
                            EconomyHook economy) {
        this.plugin = plugin;
        this.config = config;
        this.sessions = sessions;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gifty.admin")) {
            sender.sendMessage(config.format("errors.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(config.format("admin.usage"));
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                if (!sender.hasPermission("gifty.admin.reload")) {
                    sender.sendMessage(config.format("errors.no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(config.format("admin.reload"));
                break;
            case "view":
                if (!sender.hasPermission("gifty.admin.view")) {
                    sender.sendMessage(config.format("errors.no-permission"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(config.format("errors.players-only"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(config.format("admin.view-usage"));
                    return true;
                }
                OfflinePlayer target = resolve(args[1]);
                if (target == null) {
                    sender.sendMessage(config.format("send.not-found", "player", args[1]));
                    return true;
                }
                int page = 1;
                if (args.length >= 3) {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                sessions.openInboxForAdmin((Player) sender, target.getUniqueId(), page);
                break;
            case "clear":
                if (!sender.hasPermission("gifty.admin.clear")) {
                    sender.sendMessage(config.format("errors.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(config.format("admin.clear-usage"));
                    return true;
                }
                OfflinePlayer clearTarget = resolve(args[1]);
                if (clearTarget == null) {
                    sender.sendMessage(config.format("send.not-found", "player", args[1]));
                    return true;
                }
                int count = sessions.clearInbox(clearTarget.getUniqueId());
                sender.sendMessage(config.format("admin.cleared", "player", clearTarget.getName(), "count", count));
                break;
            case "expire":
                if (!sender.hasPermission("gifty.admin.expire")) {
                    sender.sendMessage(config.format("errors.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(config.format("admin.expire-usage"));
                    return true;
                }
                OfflinePlayer expTarget = resolve(args[1]);
                if (expTarget == null) {
                    sender.sendMessage(config.format("send.not-found", "player", args[1]));
                    return true;
                }
                int refunded = sessions.refundInbox(expTarget.getUniqueId());
                sender.sendMessage(config.format("admin.expired", "player", expTarget.getName(), "count", refunded));
                break;
            case "info":
                sender.sendMessage(config.format("admin.info-version", "version", plugin.version()));
                sender.sendMessage(config.format("admin.info-economy", "provider", economy.isEnabled()
                        ? economy.providerName() : "disabled"));
                sender.sendMessage(config.format("admin.info-slots", "slots", config.sendSlots()));
                sender.sendMessage(config.format("admin.info-capacity", "capacity", config.maxPendingPerPlayer()));
                sender.sendMessage(config.format("admin.info-pages", "pages", config.inboxPages()));
                break;
            default:
                sender.sendMessage(config.format("admin.usage"));
                break;
        }
        return true;
    }

    private OfflinePlayer resolve(String name) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(name);
        if (p == null || p.getName() == null || !p.hasPlayedBefore()) {
            return null;
        }
        return p;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : new String[]{"reload", "view", "clear", "expire", "info"}) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("view")
                || args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("expire"))) {
            String prefix = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
