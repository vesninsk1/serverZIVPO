package com.example.server.signature;

import com.example.server.config.SignatureProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyProvider {

    private final SignatureProperties properties;
    private final ResourceLoader resourceLoader;

    private volatile PrivateKey cachedPrivateKey;
    private volatile PublicKey cachedPublicKey;

    /**
     * Загружаем ключи при старте приложения.
     * Если keystore недоступен — приложение упадёт сразу, а не при первом запросе.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        loadKeys();
    }

    private synchronized void loadKeys() {
        String keyStorePath     = properties.getKeyStorePath();
        String keyStoreType     = properties.getKeyStoreType();
        String keyStorePassword = properties.getKeyStorePassword();
        String keyAlias         = properties.getKeyAlias();
        String keyPassword      = properties.getKeyPassword();

        // Если пароль ключа не задан — используем пароль хранилища (поведение keytool по умолчанию)
        if (keyPassword == null || keyPassword.isEmpty()) {
            keyPassword = keyStorePassword;
        }

        try (InputStream is = openKeyStore(keyStorePath)) {
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(is, keyStorePassword.toCharArray());

            if (!keyStore.containsAlias(keyAlias)) {
                throw new RuntimeException("Alias '" + keyAlias + "' not found in keystore");
            }

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, keyPassword.toCharArray());
            if (privateKey == null) {
                throw new RuntimeException("Private key is null for alias: " + keyAlias);
            }

            Certificate certificate = keyStore.getCertificate(keyAlias);

            cachedPrivateKey = privateKey;
            cachedPublicKey  = certificate.getPublicKey();

            log.info("Signing keys loaded from keystore, alias: {}", keyAlias);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load keystore: " + e.getMessage(), e);
        }
    }

    private InputStream openKeyStore(String path) {
        try {
            if (path.startsWith("classpath:") || path.startsWith("file:")) {
                Resource resource = resourceLoader.getResource(path);
                return resource.getInputStream();
            }
            return new java.io.FileInputStream(path);
        } catch (Exception e) {
            throw new RuntimeException("Cannot open keystore file: " + path, e);
        }
    }

    public PrivateKey getSigningKey() throws Exception {
        PrivateKey key = cachedPrivateKey;
        if (key == null) {
            throw new IllegalStateException("Private key not initialized");
        }
        return key;
    }

    public PublicKey getPublicKey() throws Exception {
        PublicKey key = cachedPublicKey;
        if (key == null) {
            throw new IllegalStateException("Public key not initialized");
        }
        return key;
    }
}