package org.bukkit.command;
import net.kyori.adventure.text.Component;
import org.bukkit.permissions.Permissible;
public interface CommandSender extends Permissible {
    void sendMessage(String message);
    void sendMessage(Component message);
}
