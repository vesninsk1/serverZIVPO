package com.example.server.signature;

import java.security.PrivateKey;
import java.security.cert.Certificate;

public interface KeyProvider {
    PrivateKey getSigningKey();
    Certificate getCertificate();
    byte[] getPublicKeyBytes();
}