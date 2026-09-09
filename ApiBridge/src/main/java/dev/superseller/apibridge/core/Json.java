package dev.superseller.apibridge.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() { }

    public static Object parse(String input) throws JsonParseException {
        Parser parser = new Parser(input == null ? "" : input);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isEnd()) {
            throw new JsonParseException("Trailing content after JSON value");
        }
        return value;
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            writeString(string, out);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                writeString(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out);
                if (it.hasNext()) out.append(',');
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            Iterator<?> it = iterable.iterator();
            while (it.hasNext()) {
                write(it.next(), out);
                if (it.hasNext()) out.append(',');
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) out.append(',');
                write(java.lang.reflect.Array.get(value, i), out);
            }
            out.append(']');
        } else {
            writeString(String.valueOf(value), out);
        }
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input;
        }

        private Object parseValue() throws JsonParseException {
            skipWhitespace();
            if (isEnd()) throw new JsonParseException("Empty JSON body");
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || Character.isDigit(c)) yield parseNumber();
                    throw new JsonParseException("Unexpected JSON token");
                }
            };
        }

        private Map<String, Object> parseObject() throws JsonParseException {
            expect('{');
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return map;
            while (true) {
                skipWhitespace();
                if (peek() != '"') throw new JsonParseException("Object keys must be strings");
                String key = parseString();
                skipWhitespace();
                expect(':');
                map.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) return map;
                expect(',');
            }
        }

        private List<Object> parseArray() throws JsonParseException {
            expect('[');
            ArrayList<Object> list = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return list;
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (consume(']')) return list;
                expect(',');
            }
        }

        private String parseString() throws JsonParseException {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!isEnd()) {
                char c = input.charAt(index++);
                if (c == '"') return out.toString();
                if (c == '\\') {
                    if (isEnd()) throw new JsonParseException("Invalid escape");
                    char e = input.charAt(index++);
                    switch (e) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> out.append(parseUnicode());
                        default -> throw new JsonParseException("Invalid escape");
                    }
                } else {
                    if (c < 0x20) throw new JsonParseException("Control character in string");
                    out.append(c);
                }
            }
            throw new JsonParseException("Unterminated string");
        }

        private char parseUnicode() throws JsonParseException {
            if (index + 4 > input.length()) throw new JsonParseException("Invalid unicode escape");
            String hex = input.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw new JsonParseException("Invalid unicode escape");
            }
        }

        private Number parseNumber() throws JsonParseException {
            int start = index;
            if (consume('-') && isEnd()) throw new JsonParseException("Invalid number");
            if (consume('0')) {
                // single leading zero only
            } else {
                while (!isEnd() && Character.isDigit(peek())) index++;
            }
            if (!isEnd() && peek() == '.') {
                index++;
                if (isEnd() || !Character.isDigit(peek())) throw new JsonParseException("Invalid number");
                while (!isEnd() && Character.isDigit(peek())) index++;
            }
            if (!isEnd() && (peek() == 'e' || peek() == 'E')) {
                index++;
                if (!isEnd() && (peek() == '+' || peek() == '-')) index++;
                if (isEnd() || !Character.isDigit(peek())) throw new JsonParseException("Invalid number");
                while (!isEnd() && Character.isDigit(peek())) index++;
            }
            String raw = input.substring(start, index);
            try {
                if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                    return new BigDecimal(raw);
                }
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                throw new JsonParseException("Invalid number");
            }
        }

        private Object literal(String token, Object value) throws JsonParseException {
            if (!input.startsWith(token, index)) throw new JsonParseException("Invalid literal");
            index += token.length();
            return value;
        }

        private void expect(char expected) throws JsonParseException {
            if (isEnd() || input.charAt(index++) != expected) throw new JsonParseException("Expected '" + expected + "'");
        }

        private boolean consume(char expected) {
            if (!isEnd() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private char peek() { return input.charAt(index); }
        private boolean isEnd() { return index >= input.length(); }
        private void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(input.charAt(index))) index++;
        }
    }

    public static final class JsonParseException extends Exception {
        public JsonParseException(String message) {
            super(message);
        }
    }
}
