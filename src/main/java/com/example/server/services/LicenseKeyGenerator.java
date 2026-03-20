package com.example.server.services;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class LicenseKeyGenerator {
    
    private static final String PREFIX = "LIC-";
    private static final int KEY_LENGTH = 16;
    private static final SecureRandom random = new SecureRandom();
    
    public String generateCode() {
        byte[] bytes = new byte[KEY_LENGTH];
        random.nextBytes(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return PREFIX + encoded;
    }
    
    public String generateCodeWithParts(int parts, int partLength) {
        StringBuilder key = new StringBuilder(PREFIX);
        for (int i = 0; i < parts; i++) {
            if (i > 0) key.append("-");
            key.append(generateRandomString(partLength));
        }
        return key.toString();
    }
    
    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
