package dev.superseller.subscriptions.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.superseller.subscriptions.config.PluginSettings;
import dev.superseller.subscriptions.model.InboxEntry;
import dev.superseller.subscriptions.storage.Database;
import dev.superseller.subscriptions.util.ItemSerial;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class InboxService {

    private final Database database;
    private final PluginSettings settings;

    public InboxService(Database database, PluginSettings settings) {
        this.database = database;
        this.settings = settings;
    }

    public boolean deliver(UUID owner, ItemStack item, String source) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (database.inboxSize(owner) >= settings.maxInbox()) {
            return false;
        }
        return database.addInbox(owner, ItemSerial.encode(item), source) > 0;
    }

    public List<InboxEntry> list(UUID owner) {
        return database.inbox(owner);
    }

    public int size(UUID owner) {
        return database.inboxSize(owner);
    }

    public boolean claim(Player player, long id) {
        for (InboxEntry entry : database.inbox(player.getUniqueId())) {
            if (entry.id() != id) {
                continue;
            }
            ItemStack item = ItemSerial.decode(entry.itemBase64());
            if (item == null) {
                database.deleteInbox(id, player.getUniqueId());
                return true;
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                return false;
            }
            database.deleteInbox(id, player.getUniqueId());
            return true;
        }
        return false;
    }

    public int claimAll(Player player) {
        int claimed = 0;
        for (InboxEntry entry : new ArrayList<>(database.inbox(player.getUniqueId()))) {
            ItemStack item = ItemSerial.decode(entry.itemBase64());
            if (item == null) {
                database.deleteInbox(entry.id(), player.getUniqueId());
                claimed++;
                continue;
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                break;
            }
            database.deleteInbox(entry.id(), player.getUniqueId());
            claimed++;
        }
        return claimed;
    }
}
