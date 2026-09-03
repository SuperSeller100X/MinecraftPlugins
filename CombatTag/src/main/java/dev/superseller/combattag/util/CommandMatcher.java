package dev.superseller.combattag.util;

import java.util.Locale;
import java.util.Set;

/** Matches typed chat commands against configured command lists. */
public final class CommandMatcher {

    private CommandMatcher() {
    }

    /**
     * Extracts the bare command label from a raw chat message, dropping the leading slash,
     * any {@code plugin:} namespace and all arguments.
     *
     * @param rawMessage the full chat line, e.g. {@code "/essentials:home base"}
     * @return the lowercase label, e.g. {@code "home"}; empty when nothing can be parsed
     */
    public static String label(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }
        String s = rawMessage.trim();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        int space = s.indexOf(' ');
        if (space > -1) {
            s = s.substring(0, space);
        }
        int colon = s.indexOf(':');
        if (colon > -1 && colon < s.length() - 1) {
            s = s.substring(colon + 1);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /** @return the first argument of the command, lowercase, or an empty string. */
    public static String firstArgument(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }
        String[] parts = rawMessage.trim().split("\\s+");
        return parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
    }

    /**
     * Tests a raw chat message against a set of configured entries. Entries may be plain
     * labels ({@code home}), namespaced ({@code essentials:home}), sub-command specific
     * ({@code em hand}) or wildcards ({@code tp*}).
     */
    public static boolean matches(String rawMessage, Set<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        String label = label(rawMessage);
        if (label.isEmpty()) {
            return false;
        }
        String arg = firstArgument(rawMessage);
        String labelWithArg = arg.isEmpty() ? label : label + " " + arg;

        for (String entry : entries) {
            String e = entry.toLowerCase(Locale.ROOT).trim();
            int colon = e.indexOf(':');
            if (colon > -1 && colon < e.length() - 1) {
                e = e.substring(colon + 1);
            }
            if (e.endsWith("*")) {
                String prefix = e.substring(0, e.length() - 1);
                if (label.startsWith(prefix) || labelWithArg.startsWith(prefix)) {
                    return true;
                }
                continue;
            }
            if (e.equals(label) || e.equals(labelWithArg)) {
                return true;
            }
        }
        return false;
    }
}
