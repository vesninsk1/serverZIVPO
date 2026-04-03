package com.example.server.signature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class JsonCanonicalizer implements Canonicalizer {
    
    private final ObjectMapper objectMapper;
    
    public JsonCanonicalizer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, false);
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    @Override
    public byte[] canonicalize(Object payload) {
        try {
            JsonNode rootNode;
            
            if (payload instanceof String) {
                rootNode = objectMapper.readTree((String) payload);
            } else {
                rootNode = objectMapper.valueToTree(payload);
            }
            
            String canonicalJson = serialize(rootNode);
            return canonicalJson.getBytes(StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new SignatureException(
                SignatureException.ErrorCode.INPUT_INVALID,
                "Invalid JSON input: " + e.getMessage(),
                e
            );
        }
    }
    
    private String serialize(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        
        if (node.isBoolean()) {
            return node.asBoolean() ? "true" : "false";
        }
        
        if (node.isNumber()) {
            return serializeNumber(node);
        }
        
        if (node.isTextual()) {
            return serializeString(node.textValue());
        }
        
        if (node.isArray()) {
            return serializeArray(node);
        }
        
        if (node.isObject()) {
            return serializeObject(node);
        }
        
        throw new SignatureException(
            SignatureException.ErrorCode.CANONICALIZATION_ERROR,
            "Unsupported JSON node type: " + node.getNodeType()
        );
    }
    
    private String serializeNumber(JsonNode node) {
        if (node.isInt() || node.isLong()) {
            return Long.toString(node.asLong());
        }
        if (node.isDouble() || node.isFloat()) {
            double val = node.asDouble();
            if (Double.isNaN(val) || Double.isInfinite(val)) {
                throw new SignatureException(
                    SignatureException.ErrorCode.CANONICALIZATION_ERROR,
                    "NaN and Infinity are not allowed in canonical JSON"
                );
            }
            if (val == Math.floor(val)) {
                return Long.toString((long) val);
            }
            return Double.toString(val);
        }
        return node.toString();
    }
    
    private String serializeString(String str) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
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
    
    private String serializeObject(JsonNode object) {
        List<String> fieldNames = new ArrayList<>();
        object.fieldNames().forEachRemaining(fieldNames::add);
        fieldNames.sort((a, b) -> {
            int len = Math.min(a.length(), b.length());
            for (int i = 0; i < len; i++) {
                char ca = a.charAt(i);
                char cb = b.charAt(i);
                if (ca != cb) return ca - cb;
            }
            return a.length() - b.length();
        });
        
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String fieldName : fieldNames) {
            if (!first) sb.append(",");
            first = false;
            sb.append(serializeString(fieldName));
            sb.append(":");
            sb.append(serialize(object.get(fieldName)));
        }
        sb.append("}");
        return sb.toString();
    }
}