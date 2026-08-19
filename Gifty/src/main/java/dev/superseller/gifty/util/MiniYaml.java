package dev.superseller.gifty.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny YAML-subset parser/writer used for config files and per-player
 * inbox storage. Supports the subset Gifty produces and consumes:
 * scalars (strings, ints, doubles, booleans), nested maps and lists of
 * maps/scalars, '#' comments and single-quoted strings.
 *
 * The output is valid YAML, so files can also be hand-edited.
 */
public final class MiniYaml {

    private MiniYaml() {
    }

    // ---------------------------------------------------------------- parse

    public static Map<String, Object> parse(String text) {
        List<Line> lines = new ArrayList<>();
        for (String raw : text.split("\r?\n")) {
            String stripped = raw.replace("\t", "  ");
            if (stripped.trim().isEmpty() || stripped.trim().startsWith("#")) {
                continue;
            }
            int indent = 0;
            while (indent < stripped.length() && stripped.charAt(indent) == ' ') {
                indent++;
            }
            lines.add(new Line(indent, stripped.substring(indent)));
        }
        Parser parser = new Parser(lines);
        return parser.parseMap(0);
    }

    private static final class Line {
        final int indent;
        final String content;

        Line(int indent, String content) {
            this.indent = indent;
            this.content = content;
        }
    }

    private static final class Parser {
        final List<Line> lines;
        int pos;

        Parser(List<Line> lines) {
            this.lines = lines;
        }

        Map<String, Object> parseMap(int indent) {
            Map<String, Object> map = new LinkedHashMap<>();
            while (pos < lines.size()) {
                Line line = lines.get(pos);
                if (line.indent < indent) {
                    break;
                }
                if (line.indent > indent) {
                    pos++;
                    continue;
                }
                String content = line.content;
                if (content.startsWith("- ")) {
                    break; // list handled by parseList
                }
                int colon = findKeyColon(content);
                if (colon < 0) {
                    pos++;
                    continue;
                }
                String key = content.substring(0, colon).trim();
                String rest = content.substring(colon + 1).trim();
                pos++;
                if (rest.isEmpty()) {
                    // nested map or list
                    if (pos < lines.size() && lines.get(pos).indent > indent) {
                        if (lines.get(pos).content.trim().startsWith("-")) {
                            map.put(key, parseList(lines.get(pos).indent));
                        } else {
                            map.put(key, parseMap(lines.get(pos).indent));
                        }
                    } else {
                        map.put(key, "");
                    }
                } else {
                    map.put(key, parseScalar(rest));
                }
            }
            return map;
        }

        List<Object> parseList(int indent) {
            List<Object> list = new ArrayList<>();
            while (pos < lines.size()) {
                Line line = lines.get(pos);
                if (line.indent < indent) {
                    break;
                }
                if (line.indent > indent) {
                    pos++;
                    continue;
                }
                String content = line.content.trim();
                if (!content.startsWith("-")) {
                    break;
                }
                String rest = content.substring(1).trim();
                pos++;
                int colon = findKeyColon(rest);
                if (colon > 0) {
                    // map item
                    Map<String, Object> map = new LinkedHashMap<>();
                    String key = rest.substring(0, colon).trim();
                    String value = rest.substring(colon + 1).trim();
                    if (value.isEmpty()) {
                        if (pos < lines.size() && lines.get(pos).indent > indent) {
                            if (lines.get(pos).content.trim().startsWith("-")) {
                                map.put(key, parseList(lines.get(pos).indent));
                            } else {
                                map.put(key, parseMap(lines.get(pos).indent));
                            }
                        } else {
                            map.put(key, "");
                        }
                    } else {
                        map.put(key, parseScalar(value));
                    }
                    // absorb continuation lines at deeper indent
                    while (pos < lines.size() && lines.get(pos).indent > indent) {
                        Line sub = lines.get(pos);
                        int subColon = findKeyColon(sub.content.trim());
                        if (subColon > 0) {
                            String k = sub.content.trim().substring(0, subColon).trim();
                            String v = sub.content.trim().substring(subColon + 1).trim();
                            pos++;
                            if (v.isEmpty()) {
                                if (pos < lines.size() && lines.get(pos).indent > sub.indent) {
                                    if (lines.get(pos).content.trim().startsWith("-")) {
                                        map.put(k, parseList(lines.get(pos).indent));
                                    } else {
                                        map.put(k, parseMap(lines.get(pos).indent));
                                    }
                                } else {
                                    map.put(k, "");
                                }
                            } else {
                                map.put(k, parseScalar(v));
                            }
                        } else {
                            pos++;
                        }
                    }
                    list.add(map);
                } else {
                    list.add(parseScalar(rest));
                }
            }
            return list;
        }
    }

    private static int findKeyColon(String line) {
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
            } else if (c == ':' && !inQuote) {
                return i;
            }
        }
        return -1;
    }

    private static Object parseScalar(String raw) {
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1).replace("''", "'").replace("\\n", "\n").replace("\\r", "\r");
        }
        int comment = s.indexOf(" #");
        if (comment >= 0) {
            s = s.substring(0, comment).trim();
        }
        if (s.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (s.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (s.matches("-?\\d+")) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return s;
            }
        }
        if (s.matches("-?\\d+\\.\\d+")) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return s;
            }
        }
        return s;
    }

    // ---------------------------------------------------------------- dump

    public static String dump(Map<String, Object> root) {
        StringBuilder sb = new StringBuilder(256);
        for (Map.Entry<String, Object> e : root.entrySet()) {
            writeEntry(sb, e.getKey(), e.getValue(), 0);
        }
        return sb.toString();
    }

    private static void writeEntry(StringBuilder sb, String key, Object value, int indent) {
        pad(sb, indent);
        if (value instanceof Map) {
            sb.append(key).append(":\n");
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                writeEntry(sb, String.valueOf(e.getKey()), e.getValue(), indent + 2);
            }
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            sb.append(key).append(":\n");
            for (Object item : list) {
                if (item instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) item;
                    boolean first = true;
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        if (first) {
                            pad(sb, indent + 2);
                            sb.append("- ").append(e.getKey()).append(":");
                            first = false;
                            Object v = e.getValue();
                            if (v instanceof Map || v instanceof List) {
                                sb.append("\n");
                                if (v instanceof Map) {
                                    for (Map.Entry<?, ?> sub : ((Map<?, ?>) v).entrySet()) {
                                        writeEntry(sb, String.valueOf(sub.getKey()), sub.getValue(), indent + 6);
                                    }
                                } else {
                                    for (Object subItem : (List<?>) v) {
                                        writeListItem(sb, subItem, indent + 4);
                                    }
                                }
                            } else {
                                sb.append(" ").append(scalar(v)).append("\n");
                            }
                        } else {
                            writeEntry(sb, String.valueOf(e.getKey()), e.getValue(), indent + 4);
                        }
                    }
                } else {
                    pad(sb, indent + 2);
                    sb.append("- ").append(scalar(item)).append("\n");
                }
            }
        } else {
            sb.append(key).append(": ").append(scalar(value)).append("\n");
        }
    }

    private static void writeListItem(StringBuilder sb, Object value, int indent) {
        pad(sb, indent);
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (first) {
                    sb.append("- ").append(e.getKey()).append(": ").append(scalar(e.getValue())).append("\n");
                    first = false;
                } else {
                    writeEntry(sb, String.valueOf(e.getKey()), e.getValue(), indent + 2);
                }
            }
        } else {
            sb.append("- ").append(scalar(value)).append("\n");
        }
    }

    private static String scalar(Object value) {
        if (value == null) {
            return "''";
        }
        String s = String.valueOf(value);
        s = s.replace("\\n", "\\\\n").replace("\n", "\\n").replace("\r", "\\r");
        if (s.isEmpty() || s.contains(":") || s.contains("#") || s.startsWith(" ")
                || s.endsWith(" ") || s.startsWith("-") || s.startsWith("'") || s.contains("\\n")) {
            return "'" + s.replace("'", "''") + "'";
        }
        return s;
    }

    private static void pad(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
    }
}
