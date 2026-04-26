package com.example.server.signature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class Canonicalization {

    private final ObjectMapper objectMapper;

    public Canonicalization() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public byte[] canonicalize(Object payload) throws Exception {
        try {
            JsonNode rootNode = objectMapper.valueToTree(payload);
            String canonicalJson = serialize(rootNode);
            return canonicalJson.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new Exception("Unexpected error during canonicalization", e);
        }
    }

    private String serialize(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        if (node.isBoolean()) return node.asBoolean() ? "true" : "false";
        if (node.isNumber()) return serializeNumber(node);
        if (node.isTextual()) return serializeString(node.textValue());
        if (node.isArray()) return serializeArray(node);
        if (node.isObject()) return serializeObject(node);
        throw new IllegalStateException("Unsupported JSON node type: " + node.getNodeType());
    }

    private String serializeNumber(JsonNode node) {
        if (node.isInt() || node.isLong()) {
            return Long.toString(node.asLong());
        }
        if (node.isDouble() || node.isFloat() || node.isBigDecimal()) {
            double val = node.asDouble();
            if (Double.isNaN(val) || Double.isInfinite(val)) {
                throw new IllegalStateException("NaN and Infinity are not allowed in RFC 8785");
            }
            return doubleToEcmaString(val);
        }
        return node.toString();
    }

    /**
     * Сериализация double по правилам ECMAScript (RFC 8785 §3.2.2).
     * Использует алгоритм Grisu/Ryu-подобный через Double.toString(),
     * но приводит формат к ECMAScript-виду:
     * - убирает суффикс ".0" для целых (1.0 → 1 уже обработан через isIntegral)
     * - заменяет Java-экспоненту "E" → "e+" / "e-"
     */
    private String doubleToEcmaString(double val) {
        // Целые значения — без дробной части
        if (val == Math.floor(val) && !Double.isInfinite(val)
                && Math.abs(val) < 1e21) {
            return Long.toString((long) val);
        }

        String s = Double.toString(val);

        // Java пишет "E10", ECMAScript ожидает "e+10" или "e-10"
        int eIdx = s.indexOf('E');
        if (eIdx >= 0) {
            String mantissa = s.substring(0, eIdx);
            String exp = s.substring(eIdx + 1);
            int expVal = Integer.parseInt(exp);
            String expSign = expVal >= 0 ? "+" : "";
            return mantissa + "e" + expSign + expVal;
        }

        return s;
    }

    private String serializeString(String str) {
        StringBuilder sb = new StringBuilder("\"");
        char[] chars = str.toCharArray();
        int i = 0;
        while (i < chars.length) {
            char c = chars[i];

            // Проверяем суррогаты
            if (c >= 0xD800 && c <= 0xDFFF) {
                // Старший суррогат — ожидаем следующий младший
                if (c >= 0xD800 && c <= 0xDBFF) {
                    if (i + 1 < chars.length) {
                        char next = chars[i + 1];
                        if (next >= 0xDC00 && next <= 0xDFFF) {
                            // Валидная суррогатная пара — пишем как есть
                            sb.append(c);
                            sb.append(next);
                            i += 2;
                            continue;
                        }
                    }
                    // Одиночный старший суррогат — ошибка
                    throw new IllegalStateException(
                            "Lone surrogate U+" + String.format("%04X", (int) c)
                                    + " is not allowed in RFC 8785");
                } else {
                    // Одиночный младший суррогат — ошибка
                    throw new IllegalStateException(
                            "Lone surrogate U+" + String.format("%04X", (int) c)
                                    + " is not allowed in RFC 8785");
                }
            }

            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
            i++;
        }
        sb.append("\"");
        return sb.toString();
    }

    private String serializeArray(JsonNode array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(serialize(array.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Сортировка свойств объекта по RFC 8785 §3.2.3:
     * сравнение по UTF-16 code units как беззнаковым значениям.
     * В Java char беззнаковый (0..65535), поэтому Character.compare
     * даёт корректное беззнаковое сравнение.
     */
    private String serializeObject(JsonNode object) {
        List<String> fieldNames = new ArrayList<>();
        object.fieldNames().forEachRemaining(fieldNames::add);

        // Сортировка по UTF-16 code units как беззнаковым (RFC 8785 §3.2.3)
        fieldNames.sort(Comparator.comparingInt(String::length)
                .thenComparing((a, b) -> {
                    for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
                        int diff = Character.compare(a.charAt(i), b.charAt(i));
                        if (diff != 0) return diff;
                    }
                    return Integer.compare(a.length(), b.length());
                }));

        // Правильная сортировка: лексикографически по UTF-16 code units
        fieldNames.sort((a, b) -> {
            int len = Math.min(a.length(), b.length());
            for (int i = 0; i < len; i++) {
                // Сравниваем как беззнаковые (char в Java уже беззнаковый 0..65535)
                int diff = Character.compare(a.charAt(i), b.charAt(i));
                if (diff != 0) return diff;
            }
            return Integer.compare(a.length(), b.length());
        });

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String fieldName : fieldNames) {
            if (!first) sb.append(",");
            first = false;
            sb.append(serializeString(fieldName))
              .append(":")
              .append(serialize(object.get(fieldName)));
        }
        sb.append("}");
        return sb.toString();
    }
}