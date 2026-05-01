package com.example.server.signature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class Canonicalization {

    private final ObjectMapper objectMapper;

    public Canonicalization() {
        this.objectMapper = new ObjectMapper();
        //чтобы даты не превращались в миллисекунды а были ISO-8601 (строки год-месяц-число)
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    //Преобразует объект в канонический JSON и возвращает как UTF-8 байты
    public byte[] canonicalize(Object payload) throws Exception {
        try {
            JsonNode rootNode = objectMapper.valueToTree(payload);
            String canonicalJson = serialize(rootNode);
            return canonicalJson.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new Exception("Unexpected error during canonicalization", e);
        }
    }
    //Определяет тип узла и делегирует специализированному методу
    private String serialize(JsonNode node) {
        //null преобразуется в литерал "null", а не в пустую строку
        if (node == null || node.isNull()) return "null";
        //Булевы строго "true" или "false"
        if (node.isBoolean()) return node.asBoolean() ? "true" : "false";
        // Числа требуют специальной обработки (целые без точки, дробные по ECMAScript)
        if (node.isNumber()) return serializeNumber(node);
        //экранирование управляющих символов, проверка суррогатных пар
        if (node.isTextual()) return serializeString(node.textValue());
        //рекурсивная обработка каждого элемента, порядок сохраняется
        if (node.isArray()) return serializeArray(node);
        //рекурсивная обработка объектов, ключи сортируются лексикографически
        if (node.isObject()) return serializeObject(node);
        throw new IllegalStateException("Unsupported JSON node type: " + node.getNodeType());
    }
    /*Каноническая сериализация числа по правилам RFC 8785 и ECMAScript
     1. Целые числа без десятичной точки ("42", а не "42.0")
     2. NaN и Infinity запрещены
     3. Дробные числа по формату ECMAScript (например, "1.2e+5") */
    private String serializeNumber(JsonNode node) {
         // Целочисленные типы (int, long) - простое преобразование
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
    /*
     1. Целые числа (1.0, 2.0) без дробной части ("1", "2")
     2. Экспоненциальная форма всегда с одним знаком после e: "1.2e+5", "1.2e-5"
     3. Целые числа до 1e21 представляются без экспоненты*/
    private String doubleToEcmaString(double val) {
        if (val == Math.floor(val) && !Double.isInfinite(val)
                && Math.abs(val) < 1e21) {
            // Целое число - возвращаем без десятичной точки
            return Long.toString((long) val);
        }

        String s = Double.toString(val);
        // Ищем экспоненциальную часть (символ 'E')
        int eIdx = s.indexOf('E');
        if (eIdx >= 0) {
             // Разделяем мантиссу и экспоненту
            String mantissa = s.substring(0, eIdx);
            String exp = s.substring(eIdx + 1);
            int expVal = Integer.parseInt(exp);
            //Добавляем + для положительной экспоненты
            String expSign = expVal >= 0 ? "+" : "";
            return mantissa + "e" + expSign + expVal;
        }

        return s;
    }

    private String serializeString(String str) {
        // Начинаем с открывающей кавычки
        StringBuilder sb = new StringBuilder("\"");
        char[] chars = str.toCharArray();
        int i = 0;
        while (i < chars.length) {
            char c = chars[i];
            // Обработка суррогатных пар UTF-16 (диапазон U+D800 до U+DFFF
            if (c >= 0xD800 && c <= 0xDFFF) {
                // Проверяем, что это старший суррогат (0xD800-0xDBFF)
                if (c >= 0xD800 && c <= 0xDBFF) {
                    // Смотрим, есть ли следующий символ
                    if (i + 1 < chars.length) {
                        char next = chars[i + 1];
                        // Проверяем, что следующий - младший суррогат (0xDC00-0xDFFF)
                        if (next >= 0xDC00 && next <= 0xDFFF) {

                            sb.append(c);
                            sb.append(next);
                            i += 2;
                            continue;
                        }
                    }
                    // Одинокий суррогат - ошибка 
                    throw new IllegalStateException(
                            "Lone surrogate U+" + String.format("%04X", (int) c)
                                    + " is not allowed in RFC 8785");
                } else {
                    throw new IllegalStateException(
                            "Lone surrogate U+" + String.format("%04X", (int) c)
                                    + " is not allowed in RFC 8785");
                }
            }
            // Экранирование специальных символов
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    // Управляющие символы ASCII 0x00-0x1F (кроме уже обработанных)
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
            // Добавляем запятую между элементами (но не перед первым)
            if (i > 0) sb.append(",");
            // Рекурсивно сериализуем каждый элемент
            sb.append(serialize(array.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeObject(JsonNode object) {
        // Собираем все имена полей
        List<String> fieldNames = new ArrayList<>();
        object.fieldNames().forEachRemaining(fieldNames::add);

        // Сравнение идет посимвольно по Unicode кодам
        // При равенстве строк до минимальной длины, побеждает более короткая
        fieldNames.sort((a, b) -> {
            int len = Math.min(a.length(), b.length());
            for (int i = 0; i < len; i++) {

                int diff = Character.compare(a.charAt(i), b.charAt(i));
                if (diff != 0) return diff;
            }
            return Integer.compare(a.length(), b.length());
        });

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String fieldName : fieldNames) {
            // Добавляем запятую между парами
            if (!first) sb.append(",");
            first = false;
            // Ключ сериализуем как строку (с кавычками и экранированием)
            sb.append(serializeString(fieldName))
              .append(":")
              .append(serialize(object.get(fieldName)));
        }
        sb.append("}");
        return sb.toString();
    }
}