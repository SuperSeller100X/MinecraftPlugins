package com.plugins.coderewards.commands;

import com.plugins.coderewards.CodeRewards;
import com.plugins.coderewards.models.Code;
import com.plugins.coderewards.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

public class CodeAdminCommand implements CommandExecutor, TabCompleter {
    private final CodeRewards plugin;

    public CodeAdminCommand(CodeRewards plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("coderewards.admin")) {
            sender.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou don't have permission to do that!")));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(MessageUtils.colorize("&8[&6CodeRewards&8] &eUsage: /codeadmin <create|delete|list|reload> [args]"));
            sender.sendMessage(MessageUtils.colorize("&7Subcommands:"));
            sender.sendMessage(MessageUtils.colorize("  &e/codeadmin create <code> <type> <reward> [maxUses] [expiresInHours]"));
            sender.sendMessage(MessageUtils.colorize("  &e/codeadmin delete <code>"));
            sender.sendMessage(MessageUtils.colorize("  &e/codeadmin list"));
            sender.sendMessage(MessageUtils.colorize("  &e/codeadmin reload"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                return handleCreate(sender, args);
            case "delete":
                return handleDelete(sender, args);
            case "list":
                return handleList(sender);
            case "reload":
                return handleReload(sender);
            default:
                sender.sendMessage(MessageUtils.colorize("&8[&6CodeRewards&8] &cUnknown subcommand! Use /codeadmin for help."));
                return true;
        }
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtils.colorize("&8[&6CodeRewards&8] &eUsage: /codeadmin create <code> <type> <reward> [maxUses] [expiresInHours]"));
            sender.sendMessage(MessageUtils.colorize("&7Types: money, items, commands, xp"));
            sender.sendMessage(MessageUtils.colorize("&7Example: /codeadmin create WELCOME commands say Hello %player%!"));
            return true;
        }

        String codeStr = args[1].toUpperCase();
        String type = args[2].toLowerCase();
        StringBuilder rewardBuilder = new StringBuilder();
        
        for (int i = 3; i < args.length; i++) {
            if (args[i].matches("\\d+") && i == args.length - 2 && args.length > 5) {
                // This might be maxUses, check if next is also a number (expiresInHours)
                if (i < args.length - 1 && args[i + 1].matches("\\d+")) {
                    break;
                }
            }
            if (rewardBuilder.length() > 0) {
                rewardBuilder.append(" ");
            }
            rewardBuilder.append(args[i]);
        }

        List<String> rewards = Arrays.asList(rewardBuilder.toString().split("\\|"));
        
        int maxUses = -1;
        long expiresAt = 0;

        // Parse optional arguments
        for (int i = 3; i < args.length; i++) {
            if (args[i].matches("\\d+")) {
                if (i == args.length - 1 || (i < args.length - 1 && !args[i + 1].matches("\\d+"))) {
                    // Last number or followed by non-number - could be maxUses
                    if (maxUses == -1) {
                        maxUses = Integer.parseInt(args[i]);
                    } else if (expiresAt == 0) {
                        // This is expiresInHours
                        long hours = Long.parseLong(args[i]);
                        expiresAt = System.currentTimeMillis() + (hours * 3600 * 1000);
                    }
                } else if (i < args.length - 1 && args[i + 1].matches("\\d+")) {
                    // Two consecutive numbers - first is maxUses, second is expiresInHours
                    maxUses = Integer.parseInt(args[i]);
                    long hours = Long.parseLong(args[i + 1]);
                    expiresAt = System.currentTimeMillis() + (hours * 3600 * 1000);
                    break;
                }
            }
        }

        // Validate type
        if (!type.equals("money") && !type.equals("items") && !type.equals("commands") && !type.equals("xp")) {
            sender.sendMessage(MessageUtils.colorize("&8[&6CodeRewards&8] &cInvalid type! Use: money, items, commands, or xp"));
            return true;
        }

        // Check if code already exists
        if (plugin.getCodeManager().codeExists(codeStr)) {
            sender.sendMessage(MessageUtils.colorize("&8[&6CodeRewards&8] &cA code with that name already exists!"));
            return true;
        }

        // Create and add the code
        Code code = new Code(codeStr, rewards, type, maxUses, expiresAt);
        
        if (plugin.getCodeManager().addCode(code)) {
            String msg = plugin.getConfig().getString("messages.admin-code-created", "&aCode created successfully: &6%code%");
            msg = msg.replace("%code%", codeStr);
            sender.sendMessage(MessageUtils.colorize(msg));
            
            // Show code details
            sender.sendMessage(MessageUtils.colorize("&7Details:"));
            sender.sendMessage(MessageUtils.colorize("  &7Type: &f" + type));
            sender.sendMessage(MessageUtils.colorize("  &7Reward: &f" + rewardBuilder.toString()));
            if (maxUses > 0) {
                sender.sendMessage(MessageUtils.colorize("  &7Max Uses: &f" + maxUses));
            } else {
                sender.sendMessage(MessageUtils.colorize("  &7Max Uses: &fUnlimited"));
            }
            if (expiresAt > 0) {
                long hours = (expiresAt - System.currentTimeMillis()) / 3600000;
                sender.sendMessage(MessageUtils.colorize("  &7Expires in: &f" + hours + " hours"));
            } else {
                sender.sendMessage(MessageUtils.colorize("  &7Expires: &fNever"));
            }
        } else {
            sender.sendMessage(MessageUtils.colorize("&8[&6CodeRewards&8] &cFailed to create code!"));
        }

        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&8[&6CodeRewards&8] &eUsage: /codeadmin delete <code>"));
            return true;
        }

        String codeStr = args[1].toUpperCase();

        if (plugin.getCodeManager().removeCode(codeStr)) {
            sender.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.admin-code-deleted", "&aCode deleted successfully!")));
        } else {
            sender.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.admin-code-not-found", "&cCode not found!")));
        }

        return true;
    }

    private boolean handleList(CommandSender sender) {
        Collection<Code> allCodes = plugin.getCodeManager().getAllCodes();

        if (allCodes.isEmpty()) {
            sender.sendMessage(MessageUtils.colorize("&8[&6CodeRewards&8] &7No codes have been created yet."));
            return true;
        }

        sender.sendMessage(MessageUtils.colorize("&8[&6CodeRewards&8] &eAll Codes (&7" + allCodes.size() + "&e):"));
        
        int count = 0;
        for (Code code : allCodes) {
            if (count >= 10) {
                sender.sendMessage(MessageUtils.colorize("&7... and " + (allCodes.size() - count) + " more. Use GUI for full list."));
                break;
            }
            
            String status = code.isActive() ? "&aActive" : "&cInactive";
            if (code.isExpired()) {
                status = "&cExpired";
            } else if (code.isMaxUsesReached()) {
                status = "&cMax Uses Reached";
            }
            
            sender.sendMessage(MessageUtils.colorize("  &e" + code.getCode() + " &7(&f" + code.getRewardType() + "&7) - " + status));
            count++;
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getCodeManager().loadCodes();
        
        sender.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("messages.admin-reload", "&aConfiguration reloaded successfully!")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "delete", "list", "reload");
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("create"))) {
            List<String> completions = new ArrayList<>();
            String partial = args[1].toUpperCase();
            
            for (Code code : plugin.getCodeManager().getAllCodes()) {
                if (code.getCode().startsWith(partial)) {
                    completions.add(code.getCode());
                }
            }
            
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return Arrays.asList("money", "items", "commands", "xp");
        }

        return Collections.emptyList();
    }
}
