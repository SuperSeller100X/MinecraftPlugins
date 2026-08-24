package org.bukkit.command;
import net.kyori.adventure.text.Component;
public interface CommandSender {
    void sendMessage(Component message);
    boolean hasPermission(String permission);
    String getName();
}
