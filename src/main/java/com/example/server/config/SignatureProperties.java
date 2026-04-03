package com.example.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "signature")
public class SignatureProperties {
    private String keyStorePath;
    private String keyStoreType = "JKS";
    private String keyStorePassword;
    private String keyAlias;
    private String keyPassword;
    private String signatureAlgorithm = "SHA256withRSA";
}