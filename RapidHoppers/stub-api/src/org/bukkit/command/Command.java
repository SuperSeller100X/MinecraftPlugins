package org.bukkit.command;
public class Command {
    private final String name;
    public Command() { this("rapidhoppers"); }
    public Command(String name) { this.name = name; }
    public String getName() { return name; }
}
