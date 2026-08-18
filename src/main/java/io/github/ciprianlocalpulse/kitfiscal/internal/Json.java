package io.github.ciprianlocalpulse.kitfiscal.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser și writer JSON minimal, fără dependențe externe.
 *
 * <p>Motivul pentru care nu folosim Jackson/Gson: SDK-ul acesta e gândit
 * pentru integrare în module enterprise (Spring Boot etc.) unde adăugarea
 * unei dependențe transitive de serializare aduce des conflicte de versiune
 * cu ce are deja aplicația gazdă. Suprafața API-ului kitfiscal e mică și
 * stabilă (obiecte plate, fără polimorfism), deci un parser minimal e
 * suficient și reduce suprafața de risc a dependințelor.</p>
 *
 * <p>Nu este un parser JSON complet-conform (nu tratează, de exemplu, toate
 * secvențele de escape Unicode exotice), dar acoperă corect tot ce produce
 * FastAPI/Pydantic în răspunsurile acestui serviciu.</p>
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------
    // Serializare (Map -> String JSON)
    // ------------------------------------------------------------------

    public static String write(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(entry.getKey())).append(':');
            writeValue(sb, entry.getValue());
        }
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append(quote(s));
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append(write((Map<String, Object>) value));
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, item);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Tip neserializabil: " + value.getClass());
        }
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // ------------------------------------------------------------------
    // Parsare (String JSON -> Map/List/valori Java)
    // ------------------------------------------------------------------

    public static Object parse(String json) {
        Parser p = new Parser(json);
        Object result = p.parseValue();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new JsonParseException("Caractere neașteptate după valoarea JSON, la poziția " + p.pos);
        }
        return result;
    }

    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map)) {
            throw new JsonParseException("Se aștepta un obiect JSON la nivel de rădăcină.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    public static final class JsonParseException extends RuntimeException {
        public JsonParseException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new JsonParseException("JSON gol sau trunchiat.");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> parseObjectInternal();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObjectInternal() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char next = s.charAt(pos++);
                if (next == '}') {
                    break;
                } else if (next != ',') {
                    throw new JsonParseException("Se aștepta ',' sau '}' la poziția " + (pos - 1));
                }
            }
            return map;
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char next = s.charAt(pos++);
                if (next == ']') {
                    break;
                } else if (next != ',') {
                    throw new JsonParseException("Se aștepta ',' sau ']' la poziția " + (pos - 1));
                }
            }
            return list;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new JsonParseException("Secvență de escape necunoscută: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new JsonParseException("Literal boolean invalid la poziția " + pos);
        }

        Object parseNull() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new JsonParseException("Literal invalid la poziția " + pos);
        }

        Number parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.'
                    || s.charAt(pos) == 'e' || s.charAt(pos) == 'E' || s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                pos++;
            }
            String numStr = s.substring(start, pos);
            if (numStr.isEmpty()) {
                throw new JsonParseException("Număr invalid la poziția " + start);
            }
            return Double.parseDouble(numStr);
        }

        char peek() {
            return s.charAt(pos);
        }

        void expect(char expected) {
            skipWhitespace();
            if (atEnd() || s.charAt(pos) != expected) {
                throw new JsonParseException("Se aștepta '" + expected + "' la poziția " + pos);
            }
            pos++;
        }
    }
}
