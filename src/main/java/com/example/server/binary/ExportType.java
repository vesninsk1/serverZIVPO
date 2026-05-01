package com.example.server.binary;

public enum ExportType {
    FULL(1),
    INCREMENT(2),
    BY_IDS(3);

    public final int code;

    ExportType(int code) {
        this.code = code;
    }
}