package dev.superseller.gifty.util;

/**
 * Legacy color code translator. Converts ampersand codes and hex codes
 * (&#RRGGBB) into section-sign formatted strings understood by the client.
 * No external libraries required.
 */
public final class Colors {

    private Colors() {
    }

    /**
     * Translates '&'-based color codes ('&a', '&l', ...) and hex codes
     * ('&#RRGGBB') into legacy section-sign strings.
     *
     * @param input the raw string
     * @return the translated string, never null
     */
    public static String parse(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 8);
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '&') {
                if (i + 1 >= input.length()) {
                    out.append(c);
                    break;
                }
                char n = input.charAt(i + 1);
                if (n == '&') {
                    out.append('&');
                    i += 2;
                    continue;
                }
                if (n == '#' && i + 7 < input.length() + 1 && i + 7 <= input.length()) {
                    // & followed by #RRGGBB
                    if (i + 7 <= input.length()) {
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
                }
                if ("0123456789abcdefABCDEFkKlLmMnNoOrR".indexOf(n) >= 0) {
                    out.append('§').append(Character.toLowerCase(n));
                    i += 2;
                    continue;
                }
                out.append(c);
                i++;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * Strips legacy color codes and returns the plain text.
     */
    public static String strip(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("(?i)§[0-9a-fk-orx](§[0-9a-fk-or]){0,6}", "");
    }
}
