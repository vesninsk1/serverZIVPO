package com.example.server.signature;

import lombok.Getter;

@Getter
public class SignatureException extends RuntimeException {
    private final ErrorCode errorCode;

    public SignatureException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SignatureException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public enum ErrorCode {
        KEY_SOURCE_UNAVAILABLE,
        AUTH_FAILED,
        KEY_NOT_FOUND,
        KEY_FORMAT_INVALID,
        CANONICALIZATION_ERROR,
        INPUT_INVALID,
        OUTPUT_ENCODING_FAILED,
        SIGN_OPERATION_FAILED,
        KEY_PROVIDER_ERROR
    }
}