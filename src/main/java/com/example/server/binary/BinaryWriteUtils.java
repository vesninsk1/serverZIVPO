package com.example.server.binary;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;


public final class BinaryWriteUtils {

    private BinaryWriteUtils() {}

    public static void writeU8(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
    }

    /** uint16 BigEndian — 2 байта */
    public static void writeU16(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    /** uint32 BigEndian — 4 байта */
    public static void writeU32(ByteArrayOutputStream out, long value) {
        out.write((int) ((value >>> 24) & 0xFF));
        out.write((int) ((value >>> 16) & 0xFF));
        out.write((int) ((value >>> 8)  & 0xFF));
        out.write((int) (value          & 0xFF));
    }

    /** int64/uint64 BigEndian — 8 байт */
    public static void writeI64(ByteArrayOutputStream out, long value) {
        out.write((int) ((value >>> 56) & 0xFF));
        out.write((int) ((value >>> 48) & 0xFF));
        out.write((int) ((value >>> 40) & 0xFF));
        out.write((int) ((value >>> 32) & 0xFF));
        out.write((int) ((value >>> 24) & 0xFF));
        out.write((int) ((value >>> 16) & 0xFF));
        out.write((int) ((value >>> 8)  & 0xFF));
        out.write((int) (value          & 0xFF));
    }

    /**
     * UUID: два uint64 BigEndian (most significant bits, then least significant bits).
     */
    public static void writeUUID(ByteArrayOutputStream out, java.util.UUID uuid) {
        writeI64(out, uuid.getMostSignificantBits());
        writeI64(out, uuid.getLeastSignificantBits());
    }

    /**
     * Строка UTF-8: uint32 длина в байтах + сами байты.
     */
    public static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeU32(out, bytes.length);
        out.writeBytes(bytes);
    }

    /**
     * Байтовый массив: uint32 длина + сами байты.
     */
    public static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
        writeU32(out, bytes.length);
        out.writeBytes(bytes);
    }

    /**
     * Декодирование hex-строки ("AB1C...") в сырые байты.
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            result[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return result;
    }
}