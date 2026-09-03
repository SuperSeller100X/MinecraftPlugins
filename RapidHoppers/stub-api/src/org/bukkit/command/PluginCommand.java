package org.bukkit.command;
public class PluginCommand extends Command {
    public PluginCommand(String name) { super(name); }
    public void setExecutor(CommandExecutor executor) {}
    public void setTabCompleter(TabCompleter completer) {}
}
