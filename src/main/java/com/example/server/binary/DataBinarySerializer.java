package com.example.server.binary;

import com.example.server.entities.MalwareSignature;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static com.example.server.binary.BinaryWriteUtils.*;

@Component
public class DataBinarySerializer {

    private static final String MAGIC   = "DB-DMITRIEVA";
    private static final int    VERSION = 1;

    /** Полный data.bin: заголовок + все записи */
    public byte[] serialize(List<MalwareSignature> signatures) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeString(out, MAGIC);
        writeU16(out, VERSION);
        writeU32(out, signatures.size());

        for (MalwareSignature sig : signatures) {
            out.writeBytes(serializeSingle(sig));
        }

        return out.toByteArray();
    }

    /**
     * Бинарное представление одной записи (без заголовка файла).
     * Используется как для финального data.bin,
     * так и для вычисления dataOffset/dataLength в манифесте.
     */
    public byte[] serializeSingle(MalwareSignature sig) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeString(out, sig.getThreatName());
        writeBytes(out, hexToBytes(sig.getFirstBytesHex()));
        writeBytes(out, hexToBytes(sig.getRemainderHashHex()));
        writeI64(out, sig.getRemainderLength());
        writeString(out, sig.getFileType());
        writeI64(out, sig.getOffsetStart());
        writeI64(out, sig.getOffsetEnd());

        return out.toByteArray();
    }
}