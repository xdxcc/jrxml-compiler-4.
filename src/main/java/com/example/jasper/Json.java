package com.example.jasper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 零依赖轻量 JSON 解析/序列化工具（Web 页面所需子集）。
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) {
            throw new IllegalArgumentException("Unexpected trailing chars at " + p.pos);
        }
        return v;
    }

    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) v;
        return map;
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Boolean) {
            sb.append(Boolean.TRUE.equals(value));
        } else if (value instanceof Number) {
            sb.append(numberToString((Number) value));
        } else if (value instanceof Map) {
            writeMap(sb, (Map<?, ?>) value);
        } else if (value instanceof Iterable) {
            writeArray(sb, (Iterable<?>) value);
        } else if (value instanceof Object[]) {
            writeObjArray(sb, (Object[]) value);
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            writeValue(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Iterable<?> list) {
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(',');
            first = false;
            writeValue(sb, item);
        }
        sb.append(']');
    }

    private static void writeObjArray(StringBuilder sb, Object[] array) {
        sb.append('[');
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(',');
            writeValue(sb, array[i]);
        }
        sb.append(']');
    }

    /** 整数输出为整数形式，避免指数/科学计数歧义。 */
    private static String numberToString(Number n) {
        double d = n.doubleValue();
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return String.valueOf(n.longValue());
        }
        return String.valueOf(d);
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** 递归下降解析器。 */
    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String text) {
            if (text == null) {
                throw new IllegalArgumentException("JSON text cannot be null");
            }
            this.src = text;
        }

        boolean atEnd() {
            return pos >= src.length();
        }

        void skipWs() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWs();
            if (atEnd()) throw error("Unexpected end of input");
            char c = src.charAt(pos);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expectWord("true"); return Boolean.TRUE;
                case 'f': expectWord("false"); return Boolean.FALSE;
                case 'n': expectWord("null"); return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
                    throw error("Unexpected char '" + c + "'");
            }
        }

        private Map<String, Object> parseObject() {
            pos++;
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            skipWs();
            if (!atEnd() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                map.put(key, parseValue());
                skipWs();
                if (atEnd()) throw error("Unterminated object");
                char c = src.charAt(pos);
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; return map; }
                throw error("Expected ',' or '}'");
            }
        }

        private List<Object> parseArray() {
            pos++;
            List<Object> list = new ArrayList<Object>();
            skipWs();
            if (!atEnd() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (atEnd()) throw error("Unterminated array");
                char c = src.charAt(pos);
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; return list; }
                throw error("Expected ',' or ']'");
            }
        }

        private String parseString() {
            pos++;
            StringBuilder sb = new StringBuilder(64);
            while (true) {
                if (atEnd()) throw error("Unterminated string");
                char c = src.charAt(pos);
                if (c == '"') { pos++; return sb.toString(); }
                if (c == '\\') {
                    pos++;
                    if (atEnd()) throw error("Unterminated escape");
                    char e = src.charAt(pos);
                    switch (e) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 >= src.length()) throw error("Bad \\u escape");
                            String hex = src.substring(pos + 1, pos + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException nfe) {
                                throw error("Bad \\u escape: " + hex);
                            }
                            pos += 4;
                            break;
                        default: throw error("Unknown escape '\\" + e + "'");
                    }
                    pos++;
                } else {
                    sb.append(c);
                    pos++;
                }
            }
        }

        private Object parseNumber() {
            int start = pos;
            if (!atEnd() && src.charAt(pos) == '-') pos++;
            while (!atEnd()) {
                char c = src.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E'
                        || c == '+' || c == '-') {
                    pos++;
                } else {
                    break;
                }
            }
            String num = src.substring(start, pos);
            try {
                if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0) {
                    return Long.valueOf(num);
                }
                return Double.valueOf(num);
            } catch (NumberFormatException nfe) {
                throw error("Bad number: " + num);
            }
        }

        private void expect(char c) {
            if (atEnd() || src.charAt(pos) != c) {
                throw error("Expected '" + c + "'");
            }
            pos++;
        }

        private void expectWord(String word) {
            if (pos + word.length() > src.length()
                    || !src.regionMatches(pos, word, 0, word.length())) {
                throw error("Expected " + word);
            }
            pos += word.length();
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " (position " + pos + ")");
        }
    }
}
