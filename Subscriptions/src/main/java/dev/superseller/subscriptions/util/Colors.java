package dev.superseller.subscriptions.util;

/** {@code &} / {@code &#RRGGBB} color translator. No Adventure required. */
public final class Colors {

    private Colors() {
    }

    public static String parse(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 8);
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                char n = input.charAt(i + 1);
                if (n == '&') {
                    out.append('&');
                    i += 2;
                    continue;
                }
                if (n == '#' && i + 8 <= input.length()) {
                    String hex = input.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        out.append('§').append('x');
                        for (int k = 0; k < 6; k++) {
                            out.append('§').append(Character.toLowerCase(hex.charAt(k)));
                        }
                        i += 8;
                        continue;
                    }
                }
                if ("0123456789abcdefABCDEFkKlLmMnNoOrR".indexOf(n) >= 0) {
                    out.append('§').append(Character.toLowerCase(n));
                    i += 2;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    public static String strip(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("(?i)§[0-9a-fk-orx](§[0-9a-fk-or]){0,6}", "");
    }
}
