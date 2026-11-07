package dev.superseller.veltryx.staff;

import dev.superseller.veltryx.config.Messages;
import dev.superseller.veltryx.model.NoteEntry;
import dev.superseller.veltryx.storage.DataStore;
import dev.superseller.veltryx.util.Ids;
import dev.superseller.veltryx.util.Players;
import dev.superseller.veltryx.util.TimeUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /notes <player> add <text> | list | clear [id]} — staff-only notes
 * attached to a player (history, context, watch-list hints).
 */
public final class NotesCommand implements CommandExecutor, TabCompleter {

    private final Messages messages;
    private final DataStore store;

    public NotesCommand(Messages messages, DataStore store) {
        this.messages = messages;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("veltryx.moderation.notes")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "notes.usage", Map.of("command", "/" + label));
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null) {
            messages.send(sender, "general.player-not-found", Map.of("input", args[0]));
            return true;
        }
        String name = Players.name(target, args[0]);
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "add" -> {
                if (args.length < 3) {
                    messages.send(sender, "notes.usage", Map.of("command", "/" + label));
                    return true;
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                NoteEntry entry = new NoteEntry(Ids.next(), target.getUniqueId(), name,
                        text, sender.getName(), System.currentTimeMillis());
                store.saveNote(entry);
                messages.send(sender, "notes.added", Map.of(
                        "player", name, "id", entry.id(), "note", text));
            }
            case "list" -> {
                List<NoteEntry> notes = notesFor(target.getUniqueId());
                if (notes.isEmpty()) {
                    messages.send(sender, "notes.none", Map.of("player", name));
                    return true;
                }
                messages.send(sender, "notes.header",
                        Map.of("player", name, "count", String.valueOf(notes.size())));
                for (NoteEntry entry : notes) {
                    sender.sendMessage(messages.prefixed("notes.entry", Map.of(
                            "id", entry.id(),
                            "staff", entry.staff(),
                            "issued", TimeUtil.formatTimestamp(entry.issued()),
                            "note", entry.note())));
                }
            }
            case "clear" -> {
                List<NoteEntry> notes = notesFor(target.getUniqueId());
                if (args.length >= 3) {
                    store.removeNote(args[2]);
                    messages.send(sender, "notes.cleared-one", Map.of("id", args[2]));
                } else {
                    for (NoteEntry entry : notes) {
                        store.removeNote(entry.id());
                    }
                    messages.send(sender, "notes.cleared", Map.of(
                            "player", name, "count", String.valueOf(notes.size())));
                }
            }
            default -> messages.send(sender, "notes.usage", Map.of("command", "/" + label));
        }
        return true;
    }

    private List<NoteEntry> notesFor(java.util.UUID uuid) {
        List<NoteEntry> notes = new ArrayList<>();
        for (NoteEntry entry : store.allNotes()) {
            if (entry.uuid().equals(uuid)) {
                notes.add(entry);
            }
        }
        notes.sort(Comparator.comparingLong(NoteEntry::issued).reversed());
        return notes;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            String lower = args[0].toLowerCase(java.util.Locale.ROOT);
            names.removeIf(name -> !name.toLowerCase(java.util.Locale.ROOT).startsWith(lower));
            return names;
        }
        if (args.length == 2) {
            List<String> actions = new ArrayList<>(List.of("add", "list", "clear"));
            String lower = args[1].toLowerCase(java.util.Locale.ROOT);
            actions.removeIf(action -> !action.startsWith(lower));
            return actions;
        }
        return List.of();
    }
}
