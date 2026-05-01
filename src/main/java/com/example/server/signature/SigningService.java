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

    public String sign(Object payload) throws Exception {
        //канонизация
        byte[] canonicalBytes = canonicalization.canonicalize(payload);
        //подпись
        return signBytesToBase64(canonicalBytes);
    }

    public byte[] sign(byte[] data) throws Exception {
         // Получаем приватный ключ из провайдера
        PrivateKey signingKey = keyProvider.getSigningKey();
        // Создаём экземпляр Signature для указанного алгоритма
        Signature signature = Signature.getInstance(algorithm);
         // Инициализируем подпись приватным ключом
        signature.initSign(signingKey);
        // Передаём данные для подписи
        signature.update(data);
        // Вычисляем и возвращаем цифровую подпись
        return signature.sign();
    }
    //Проверяем цифровую подпись для указанного объекта.
    public boolean verify(String signatureBase64, Object payload) throws Exception {
        byte[] canonicalBytes = canonicalization.canonicalize(payload);
        java.security.PublicKey publicKey = keyProvider.getPublicKey();
        Signature verifier = Signature.getInstance(algorithm);
        verifier.initVerify(publicKey);
        verifier.update(canonicalBytes);
        return verifier.verify(Base64.getDecoder().decode(signatureBase64));
    }

    private String signBytesToBase64(byte[] bytes) throws Exception {
        byte[] raw = sign(bytes);
        return Base64.getEncoder().encodeToString(raw);
    }
}