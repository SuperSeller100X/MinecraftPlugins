package org.bukkit.command;

public final class PluginCommand extends Command {
    private CommandExecutor executor;
    private TabCompleter tabCompleter;

    public PluginCommand(String name) {
        super(name);
    }

    public void setExecutor(CommandExecutor executor) {
        this.executor = executor;
    }

    public CommandExecutor getExecutor() {
        return executor;
    }

    public void setTabCompleter(TabCompleter tabCompleter) {
        this.tabCompleter = tabCompleter;
    }

    public TabCompleter getTabCompleter() {
        return tabCompleter;
    }
}
