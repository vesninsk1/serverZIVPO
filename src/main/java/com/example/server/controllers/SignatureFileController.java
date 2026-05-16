package com.example.server.controllers;

import com.example.server.services.MinioService;
import com.example.server.services.MalwareSignatureService;
import com.example.server.entities.MalwareSignature;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/signatures/files")
@RequiredArgsConstructor
public class SignatureFileController {

    private final MinioService minioService;
    private final MalwareSignatureService signatureService;

    // Эндпоинт 1: загрузка файла, расчёт сигнатуры, сохранение в БД + MinIO
    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> uploadSignatureFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("description") String description
    ) {
        try {
            // Рассчитать SHA-256 хэш файла
            byte[] bytes = file.getBytes();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            String hashHex = HexFormat.of().formatHex(hash);

            // Сохранить метаданные в БД
            MalwareSignature signature = signatureService.createSignatureFromFile(name, description, hashHex);

            // Загрузить файл в MinIO
            String objectKey = "signatures/" + signature.getId() + "/" + file.getOriginalFilename();
            minioService.uploadFile(objectKey, file);

            // Сохранить ключ файла в сигнатуре
            signatureService.updateFileKey(signature.getId(), objectKey);

            return ResponseEntity.ok(Map.of(
                    "id", signature.getId(),
                    "hash", hashHex,
                    "fileKey", objectKey
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Эндпоинт 2: получение списка pre-signed URL по списку ID
    @PostMapping("/presigned-urls")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getPresignedUrls(@RequestBody Map<String, List<UUID>> request) {
        List<UUID> ids = request.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Field 'ids' is required and must not be empty"));
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (UUID id : ids) {
            try {
                MalwareSignature sig = signatureService.getSignatureById(id);
                if (sig.getFileKey() != null) {
                    String url = minioService.getPresignedUrl(sig.getFileKey());
                    result.put(id.toString(), url);
                } else {
                    result.put(id.toString(), null);
                }
            } catch (NoSuchElementException e) {
                result.put(id.toString(), null);
            }
        }
        return ResponseEntity.ok(result);
    }
}