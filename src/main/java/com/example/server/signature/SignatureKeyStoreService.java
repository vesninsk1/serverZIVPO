package com.example.server.signature;

import com.example.server.config.SignatureProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;

@Slf4j
@Service
public class SignatureKeyStoreService implements KeyProvider {
    private final SignatureProperties properties;
    private final ResourceLoader resourceLoader;
    
    private PrivateKey privateKey;
    private Certificate certificate;
    
    public SignatureKeyStoreService(SignatureProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        loadKeys();
    }
    
    private synchronized void loadKeys() {
        String keyStorePath = properties.getKeyStorePath();
        String keyStoreType = properties.getKeyStoreType();
        String keyStorePassword = properties.getKeyStorePassword();
        String keyAlias = properties.getKeyAlias();
        String keyPassword = properties.getKeyPassword();
        
        if (keyPassword == null || keyPassword.isEmpty()) {
            keyPassword = keyStorePassword;
        }
        
        try (InputStream is = loadKeyStoreInputStream(keyStorePath)) {
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(is, keyStorePassword.toCharArray());
            
            if (!keyStore.containsAlias(keyAlias)) {
                throw new SignatureException(
                    SignatureException.ErrorCode.KEY_NOT_FOUND,
                    "Alias '" + keyAlias + "' not found in keystore"
                );
            }
            
            privateKey = (PrivateKey) keyStore.getKey(keyAlias, keyPassword.toCharArray());
            certificate = keyStore.getCertificate(keyAlias);
            
            if (privateKey == null) {
                throw new SignatureException(
                    SignatureException.ErrorCode.KEY_FORMAT_INVALID,
                    "Private key is null for alias: " + keyAlias
                );
            }
            
            log.info("Successfully loaded signing key from keystore, alias: {}", keyAlias);
            
        } catch (SignatureException e) {
            throw e;
        } catch (Exception e) {
            throw new SignatureException(
                SignatureException.ErrorCode.KEY_SOURCE_UNAVAILABLE,
                "Failed to load keystore: " + e.getMessage(),
                e
            );
        }
    }
    
    private InputStream loadKeyStoreInputStream(String path) {
        try {
            if (path.startsWith("classpath:")) {
                Resource resource = resourceLoader.getResource(path);
                return resource.getInputStream();
            }
            return new java.io.FileInputStream(path);
        } catch (Exception e) {
            throw new SignatureException(
                SignatureException.ErrorCode.KEY_SOURCE_UNAVAILABLE,
                "Cannot open keystore file: " + path,
                e
            );
        }
    }
    
    @Override
    public PrivateKey getSigningKey() {
        if (privateKey == null) {
            throw new SignatureException(
                SignatureException.ErrorCode.KEY_NOT_FOUND,
                "Signing key not initialized"
            );
        }
        return privateKey;
    }
    
    @Override
    public Certificate getCertificate() {
        return certificate;
    }
    
    @Override
    public byte[] getPublicKeyBytes() {
        if (certificate == null) {
            return null;
        }
        return certificate.getPublicKey().getEncoded();
    }
    
    public String getPublicKeyBase64() {
        byte[] pubKeyBytes = getPublicKeyBytes();
        return pubKeyBytes != null ? Base64.getEncoder().encodeToString(pubKeyBytes) : null;
    }
}