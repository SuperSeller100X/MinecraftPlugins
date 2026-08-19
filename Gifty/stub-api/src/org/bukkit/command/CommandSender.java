package org.bukkit.command;

import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.ServerOperator;

public interface CommandSender extends Permissible, ServerOperator {
    String getName();
    void sendMessage(String message);
}
