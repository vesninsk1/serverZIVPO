package com.example.server.signature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.*;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class SigningService {

    private final Canonicalization canonicalization;
    private final KeyProvider keyProvider;
    
    @Value("${signature.signature-algorithm:SHA256withRSA}")
    private String algorithm;

    public String sign(Object payload) throws Exception {
        byte[] canonicalBytes = canonicalization.canonicalize(payload);
        PrivateKey signingKey = keyProvider.getSigningKey();
        return sign(signingKey, canonicalBytes);
    }

    public byte[] sign(byte[] data) throws Exception {
        PrivateKey signingKey = keyProvider.getSigningKey();
        Signature signature = Signature.getInstance(algorithm);
        signature.initSign(signingKey);
        signature.update(data);
        return signature.sign();
    }

    private String sign(PrivateKey key, byte[] bytes) throws Exception {
        Signature signature = Signature.getInstance(algorithm);
        signature.initSign(key);
        signature.update(bytes);
        byte[] signatureBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    public boolean verify(String signature, Object payload) throws Exception {
        byte[] canonicalBytes = canonicalization.canonicalize(payload);
        PublicKey publicKey = keyProvider.getPublicKey();
        Signature verifier = Signature.getInstance(algorithm);
        verifier.initVerify(publicKey);
        verifier.update(canonicalBytes);
        return verifier.verify(Base64.getDecoder().decode(signature));
    }
}