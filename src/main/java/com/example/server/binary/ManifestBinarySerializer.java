package com.example.server.binary;

import com.example.server.entities.MalwareSignature;
import com.example.server.models.MalwareSignatureStatus;
import com.example.server.signature.SigningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

import static com.example.server.binary.BinaryWriteUtils.*;

/* Порядок байт: BigEndian для всех многобайтовых полей (§7 методички).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManifestBinarySerializer {

    private static final String MAGIC   = "MF-DMITRIEVA";
    private static final int    VERSION = 1;

    private final SigningService signingService;


    public byte[] serialize(
            List<MalwareSignature> signatures,
            byte[] dataBytes,
            ExportType exportType,
            long sinceMillis,
            long[] dataOffsets,
            long[] dataLengths) throws Exception {

        // SHA-256 всего data.bin (§4.1)
        byte[] dataSha256 = MessageDigest.getInstance("SHA-256").digest(dataBytes);

        // Собираем неподписанную часть манифеста 
        ByteArrayOutputStream unsigned = new ByteArrayOutputStream();

        // Заголовок
        writeString(unsigned, MAGIC);
        writeU16(unsigned, VERSION);
        writeU8(unsigned, exportType.code);
        writeI64(unsigned, System.currentTimeMillis());
        writeI64(unsigned, sinceMillis);
        writeU32(unsigned, signatures.size());
        unsigned.writeBytes(dataSha256); // ровно 32 байта, без length-префикса 

        // Массив entries
        for (int i = 0; i < signatures.size(); i++) {
            MalwareSignature sig = signatures.get(i);

            // id — UUID как два int64 (§4.2)
            writeUUID(unsigned, sig.getId());

            // statusCode: ACTUAL=1, DELETED=2 (§4.2)
            int statusCode = sig.getStatus() == MalwareSignatureStatus.ACTUAL ? 1 : 2;
            writeU8(unsigned, statusCode);

            // updatedAtEpochMillis
            writeI64(unsigned, sig.getUpdatedAt().toEpochMilli());

            // dataOffset и dataLength из параллельных массивов
            writeI64(unsigned, dataOffsets[i]);
            writeI64(unsigned, dataLengths[i]);

            // Декодируем существующую подпись записи из Base64
            // Подпись записи НЕ пересчитывается — берётся из digitalSignatureBase64
            byte[] recordSigBytes = decodeRecordSignature(sig);
            writeU32(unsigned, recordSigBytes.length);
            unsigned.writeBytes(recordSigBytes);
        }

        byte[] unsignedBytes = unsigned.toByteArray();

        // подписываем неподписанную часть
        // Используем новый метод sign(byte[]) из SigningService
        byte[] manifestSig = signingService.sign(unsignedBytes);

        // неподписанная часть + подпись
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.writeBytes(unsignedBytes);
        writeU32(result, manifestSig.length);
        result.writeBytes(manifestSig);

        return result.toByteArray();
    }

    /**
     * Декодирует Base64-подпись записи в байты.
     * Если подпись отсутствует — возвращает пустой массив (запись создана до внедрения ЭЦП).
     */
    private byte[] decodeRecordSignature(MalwareSignature sig) {
        String b64 = sig.getDigitalSignatureBase64();
        if (b64 == null || b64.isBlank()) {
            log.warn("No digital signature for record {}, using empty bytes", sig.getId());
            return new byte[0];
        }
        return Base64.getDecoder().decode(b64);
    }
}