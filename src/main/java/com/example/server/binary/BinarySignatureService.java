package com.example.server.binary;

import com.example.server.entities.MalwareSignature;
import com.example.server.models.MalwareSignatureStatus;
import com.example.server.repositories.MalwareSignatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Сервисный слой бинарного API.
 * Отвечает за выборку данных и координацию сборки бинарного пакета.
 * Не содержит бизнес-логики управления сигнатурами — только транспортная сборка.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BinarySignatureService {

    private final MalwareSignatureRepository     signatureRepository;
    private final DataBinarySerializer           dataSerializer;
    private final ManifestBinarySerializer       manifestSerializer;
    private final MultipartMixedResponseFactory  responseFactory;

    // ───────── полная база (только ACTUAL) ─────────

    @Transactional(readOnly = true)
    public org.springframework.http.ResponseEntity<?> buildFullResponse() throws Exception {
        List<MalwareSignature> signatures =
                signatureRepository.findByStatus(MalwareSignatureStatus.ACTUAL);
        return buildResponse(signatures, ExportType.FULL, -1L);
    }

    // ───────── инкремент (ACTUAL + DELETED, изменённые после since) ─────────

    @Transactional(readOnly = true)
    public org.springframework.http.ResponseEntity<?> buildIncrementResponse(
            Instant since) throws Exception {
        List<MalwareSignature> signatures =
                signatureRepository.findByUpdatedAtAfter(since);
        return buildResponse(signatures, ExportType.INCREMENT, since.toEpochMilli());
    }

    // ───────── по списку UUID ─────────

    @Transactional(readOnly = true)
    public org.springframework.http.ResponseEntity<?> buildByIdsResponse(
            List<UUID> ids) throws Exception {
        List<MalwareSignature> signatures = signatureRepository.findAllById(ids);
        return buildResponse(signatures, ExportType.BY_IDS, -1L);
    }

    // ───────── общая сборка пакета ─────────

    private org.springframework.http.ResponseEntity<?> buildResponse(
            List<MalwareSignature> signatures,
            ExportType exportType,
            long sinceMillis) throws Exception {

        // 1. Формируем data.bin
        byte[] dataBytes = dataSerializer.serialize(signatures);

        // 2. Вычисляем смещения и длины каждой записи в data.bin
        //    Для этого сериализуем каждую запись отдельно, чтобы знать её размер
        long[] dataOffsets = new long[signatures.size()];
        long[] dataLengths = new long[signatures.size()];
        long currentOffset = 0;

        // Пересчитываем длины отдельных записей (без заголовка data.bin)
        // Заголовок = MAGIC(uint32+bytes) + version(uint16) + recordCount(uint32)
        // Нам нужны смещения только полезной нагрузки записей, offset=0 для первой
        for (int i = 0; i < signatures.size(); i++) {
            byte[] recordBytes = dataSerializer.serializeSingle(signatures.get(i));
            dataOffsets[i] = currentOffset;
            dataLengths[i] = recordBytes.length;
            currentOffset += recordBytes.length;
        }

        // 3. Формируем manifest.bin (с подписью манифеста)
        byte[] manifestBytes = manifestSerializer.serialize(
                signatures, dataBytes, exportType, sinceMillis, dataOffsets, dataLengths);

        log.info("Binary pack built: type={}, records={}, manifest={}b, data={}b",
                exportType, signatures.size(), manifestBytes.length, dataBytes.length);

        // 4. Собираем multipart/mixed ответ
        return responseFactory.create(manifestBytes, dataBytes);
    }
}