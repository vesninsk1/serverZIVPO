package com.example.server.signature;

public interface Canonicalizer {
    byte[] canonicalize(Object payload);
}