package com.example.server.signature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class SigningService {

    private final Canonicalization canonicalization;
    private final KeyProvider      keyProvider;

    @Value("${signature.signature-algorithm:SHA256withRSA}")
    private String algorithm;

    /**
     * Подпись объектного payload — для сигнатур записей.
     * payload → канонизация → UTF-8 байты → подпись → Base64 строка.
     */
    public String sign(Object payload) throws Exception {
        byte[] canonicalBytes = canonicalization.canonicalize(payload);
        return signBytesToBase64(canonicalBytes);
    }

    /**
     * Подпись готового байтового массива — для манифеста (§8 методички).
     * Принимает уже сформированные байты, подписывает приватным ключом,
     * возвращает сырые байты подписи (не Base64 — манифест сам их упакует).
     */
    public byte[] sign(byte[] data) throws Exception {
        PrivateKey signingKey = keyProvider.getSigningKey();
        Signature signature = Signature.getInstance(algorithm);
        signature.initSign(signingKey);
        signature.update(data);
        return signature.sign();
    }

    /**
     * Проверка подписи объектного payload.
     */
    public boolean verify(String signatureBase64, Object payload) throws Exception {
        byte[] canonicalBytes = canonicalization.canonicalize(payload);
        java.security.PublicKey publicKey = keyProvider.getPublicKey();
        Signature verifier = Signature.getInstance(algorithm);
        verifier.initVerify(publicKey);
        verifier.update(canonicalBytes);
        return verifier.verify(Base64.getDecoder().decode(signatureBase64));
    }

    // ── приватный вспомогательный метод ──

    private String signBytesToBase64(byte[] bytes) throws Exception {
        byte[] raw = sign(bytes);
        return Base64.getEncoder().encodeToString(raw);
    }
}