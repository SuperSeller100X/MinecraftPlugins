package dev.superseller.apibridge.command;

import dev.superseller.apibridge.ApiBridgePlugin;
import dev.superseller.apibridge.api.ApiEndpoint;
import dev.superseller.apibridge.core.ApiErrorLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class ApiBridgeCommand implements CommandExecutor, TabCompleter {
    private final ApiBridgePlugin plugin;

    public ApiBridgeCommand(ApiBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("apibridge.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use ApiBridge diagnostics.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> status(sender);
            case "endpoints" -> endpoints(sender);
            case "metrics" -> metrics(sender);
            case "errors" -> errors(sender);
            case "clients" -> clients(sender);
            case "reload", "rl" -> {
                plugin.reloadBridge();
                sender.sendMessage(ChatColor.GREEN + "ApiBridge reloaded.");
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <status|endpoints|metrics|errors|clients|reload>");
        }
        return true;
    }

    private void status(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "ApiBridge status");
        sender.sendMessage(ChatColor.GRAY + "Enabled: " + plugin.bridgeConfig().enabled());
        sender.sendMessage(ChatColor.GRAY + "Running: " + (plugin.apiManager() != null && plugin.apiManager().isRunning()));
        sender.sendMessage(ChatColor.GRAY + "Bind: " + plugin.bridgeConfig().bindHost() + ':' + plugin.bridgeConfig().port());
        sender.sendMessage(ChatColor.GRAY + "TLS: " + plugin.bridgeConfig().tlsEnabled());
        sender.sendMessage(ChatColor.GRAY + "Registered endpoints: " + plugin.apiManager().endpoints().size());
        sender.sendMessage(ChatColor.GRAY + "Configured clients: " + plugin.apiManager().authManager().configuredClientCount() + " (secrets hidden)");
    }

    private void endpoints(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "ApiBridge endpoints");
        for (ApiEndpoint endpoint : plugin.apiManager().endpoints()) {
            sender.sendMessage(ChatColor.GRAY + endpoint.method() + " " + endpoint.fullPath()
                    + " owner=" + endpoint.owner()
                    + " scopes=" + endpoint.requiredScopes()
                    + " mode=" + endpoint.executionMode());
        }
    }

    private void metrics(CommandSender sender) {
        Map<String, Object> snapshot = plugin.apiManager().metrics().snapshot();
        sender.sendMessage(ChatColor.GOLD + "ApiBridge metrics");
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            if (!"endpointUsage".equals(entry.getKey())) {
                sender.sendMessage(ChatColor.GRAY + entry.getKey() + ": " + entry.getValue());
            }
        }
    }

    private void errors(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Recent ApiBridge errors");
        for (ApiErrorLog.Entry entry : plugin.apiManager().errorLog().snapshot()) {
            sender.sendMessage(ChatColor.GRAY + entry.timestamp() + " " + entry.status() + " " + entry.code()
                    + " requestId=" + entry.requestId() + " route=" + entry.route());
        }
    }

    private void clients(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "ApiBridge clients (secrets hidden)");
        for (String summary : plugin.apiManager().authManager().clientSummaries()) {
            sender.sendMessage(ChatColor.GRAY + summary);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = List.of("status", "endpoints", "metrics", "errors", "clients", "reload");
        List<String> out = new ArrayList<>();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.startsWith(prefix)) out.add(option);
        }
        return out;
    }
}
