package org.bukkit.command;
public abstract class PluginCommand extends Command {
    public void setExecutor(CommandExecutor executor) {}
    public void setTabCompleter(TabCompleter completer) {}
}
