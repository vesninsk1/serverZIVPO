package com.example.server.signature;

import com.example.server.config.SignatureProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

@Slf4j
@Service
public class SigningService {
    
    private final Canonicalizer canonicalizer;
    private final KeyProvider keyProvider;
    private final SignatureProperties properties;
    
    public SigningService(Canonicalizer canonicalizer, 
                          KeyProvider keyProvider,
                          SignatureProperties properties) {
        this.canonicalizer = canonicalizer;
        this.keyProvider = keyProvider;
        this.properties = properties;
    }
    
    public String sign(Object payload) {
        try {
            // 1. Canonicalization
            byte[] canonicalBytes;
            try {
                canonicalBytes = canonicalizer.canonicalize(payload);
                log.debug("Canonicalized payload to {} bytes", canonicalBytes.length);
            } catch (SignatureException e) {
                throw e;
            } catch (Exception e) {
                throw new SignatureException(
                    SignatureException.ErrorCode.CANONICALIZATION_ERROR,
                    "Canonicalization failed: " + e.getMessage(),
                    e
                );
            }
            
            // 2. Get signing key
            PrivateKey privateKey;
            try {
                privateKey = keyProvider.getSigningKey();
            } catch (SignatureException e) {
                throw e;
            } catch (Exception e) {
                throw new SignatureException(
                    SignatureException.ErrorCode.KEY_PROVIDER_ERROR,
                    "Failed to get signing key: " + e.getMessage(),
                    e
                );
            }
            
            // 3. Sign
            byte[] signatureBytes;
            try {
                Signature signature = Signature.getInstance(properties.getSignatureAlgorithm());
                signature.initSign(privateKey);
                signature.update(canonicalBytes);
                signatureBytes = signature.sign();
                log.debug("Generated signature of {} bytes", signatureBytes.length);
            } catch (Exception e) {
                throw new SignatureException(
                    SignatureException.ErrorCode.SIGN_OPERATION_FAILED,
                    "Sign operation failed: " + e.getMessage(),
                    e
                );
            }
            
            // 4. Base64 encode
            return Base64.getEncoder().encodeToString(signatureBytes);
            
        } catch (SignatureException e) {
            throw e;
        } catch (Exception e) {
            throw new SignatureException(
                SignatureException.ErrorCode.SIGN_OPERATION_FAILED,
                "Unexpected error during signing: " + e.getMessage(),
                e
            );
        }
    }
}