package dev.superseller.hourglass.command;

import java.util.List;
import java.util.Locale;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * One sub-command of {@code /playtime} or {@code /playtimeadmin}: its names, the
 * permission it needs, what it does and how to tab-complete its arguments.
 *
 * <p>Built with {@link Builder} so the command classes stay a readable table of
 * behaviour instead of a wall of boilerplate. Every sub-command is given a
 * short alias (the user asked for that explicitly, and it is checked by
 * {@code tools/check_consistency.py}).
 */
public final class SubCommand {

    /** What the sub-command does with the arguments after its own name. */
    @FunctionalInterface
    public interface Executor {
        void run(CommandSender sender, String label, String[] args) throws Exception;
    }

    /** Tab completion for {@code args} (already stripped of the sub-command). */
    @FunctionalInterface
    public interface Completer {
        List<String> complete(CommandSender sender, String[] args, String last);
    }

    /** Builds a sub-command. */
    public static final class Builder {
        private final String name;
        private List<String> aliases = List.of();
        private String permission = null;
        private String usage = "";
        private boolean playersOnly = false;
        private Executor executor = (sender, label, args) -> {
        };
        private Completer completer = (sender, args, last) -> List.of();

        private Builder(String name) {
            this.name = name;
        }

        public Builder aliases(String... values) {
            this.aliases = List.of(values);
            return this;
        }

        public Builder permission(String node) {
            this.permission = node;
            return this;
        }

        public Builder usage(String text) {
            this.usage = text;
            return this;
        }

        public Builder playersOnly() {
            this.playersOnly = true;
            return this;
        }

        public Builder onExecute(Executor executor) {
            this.executor = executor;
            return this;
        }

        public Builder onTab(Completer completer) {
            this.completer = completer;
            return this;
        }

        public SubCommand build() {
            return new SubCommand(name, aliases, permission, usage, playersOnly, executor, completer);
        }
    }

    public static Builder of(String name) {
        return new Builder(name.toLowerCase(Locale.US));
    }

    private final String name;
    private final List<String> aliases;
    private final String permission;
    private final String usage;
    private final boolean playersOnly;
    private final Executor executor;
    private final Completer completer;

    private SubCommand(String name, List<String> aliases, String permission, String usage, boolean playersOnly,
                       Executor executor, Completer completer) {
        this.name = name;
        this.aliases = aliases;
        this.permission = permission;
        this.usage = usage;
        this.playersOnly = playersOnly;
        this.executor = executor;
        this.completer = completer;
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public String permission() {
        return permission;
    }

    public String usage() {
        return usage;
    }

    public boolean playersOnly() {
        return playersOnly;
    }

    public Executor executor() {
        return executor;
    }

    public Completer completer() {
        return completer;
    }

    /** {@code true} when {@code token} is this sub-command's name or an alias. */
    public boolean matches(String token, boolean allowAliases) {
        if (token == null) {
            return false;
        }
        String candidate = token.toLowerCase(Locale.US);
        if (name.equals(candidate)) {
            return true;
        }
        if (!allowAliases) {
            return false;
        }
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** {@code true} when it starts with {@code prefix} (name or alias). */
    public boolean startsWith(String prefix, boolean allowAliases) {
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        String candidate = prefix.toLowerCase(Locale.US);
        if (name.startsWith(candidate)) {
            return true;
        }
        if (!allowAliases) {
            return false;
        }
        for (String alias : aliases) {
            if (alias.startsWith(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this sender may run it at all. */
    public boolean allowed(CommandSender sender) {
        return permission == null || permission.isBlank() || sender.hasPermission(permission);
    }

    /** Completion options for this sub-command's own arguments. */
    public List<String> complete(CommandSender sender, String[] args, String last) {
        try {
            List<String> options = completer.complete(sender, args, last);
            return options == null ? List.of() : options;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** Runs the sub-command; exceptions are reported by the caller. */
    public void run(CommandSender sender, String label, String[] args) throws Exception {
        if (playersOnly && !(sender instanceof Player)) {
            throw new PlayersOnlyException();
        }
        executor.run(sender, label, args);
    }

    /** Thrown when a player-only sub-command is used from the console. */
    public static final class PlayersOnlyException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PlayersOnlyException() {
            super("players only");
        }
    }
}
