package com.example.server.services;

import com.example.server.entities.Signature;
import com.example.server.entities.SignatureAudit;
import com.example.server.entities.SignatureHistory;
import com.example.server.models.SignatureRequest;
import com.example.server.models.SignatureStatus;
import com.example.server.repositories.SignatureAuditRepository;
import com.example.server.repositories.SignatureHistoryRepository;
import com.example.server.repositories.SignatureRepository;
import com.example.server.signature.SigningService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureService {

    private final SignatureRepository signatureRepository;
    private final SignatureHistoryRepository historyRepository;
    private final SignatureAuditRepository auditRepository;
    private final SigningService signingService;
    private final ObjectMapper objectMapper;

    private static final String HEX_PATTERN = "^[0-9A-Fa-f]+$";

    @Transactional(readOnly = true)
    public List<Signature> getAllActiveSignatures() {
        return signatureRepository.findByStatus(SignatureStatus.ACTUAL);
    }

    @Transactional(readOnly = true)
    public List<Signature> getIncrementalSignatures(Instant since) {
        return signatureRepository.findByUpdatedAtAfter(since);
    }

    @Transactional(readOnly = true)
    public List<Signature> getSignaturesByIds(List<UUID> ids) {
        return signatureRepository.findAllById(ids).stream()
                .filter(s -> s.getStatus() == SignatureStatus.ACTUAL)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Signature getSignatureById(UUID id) {
        Signature signature = signatureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Signature not found"));
        
        if (signature.getStatus() == SignatureStatus.DELETED) {
            throw new RuntimeException("Signature is deleted");
        }
        return signature;
    }

    @Transactional
    public Signature createSignature(SignatureRequest request, String changedBy) {
        validateSignatureRequest(request);
        validateHexFormat(request);
        
        if (signatureRepository.existsByThreatName(request.getThreatName())) {
            throw new RuntimeException("Signature with threat name '" + request.getThreatName() + "' already exists");
        }
        
        Instant now = Instant.now();
        
        Signature signature = Signature.builder()
                .threatName(request.getThreatName())
                .firstBytesHex(request.getFirstBytesHex())
                .remainderHashHex(request.getRemainderHashHex())
                .remainderLength(request.getRemainderLength())
                .fileType(request.getFileType())
                .offsetStart(request.getOffsetStart())
                .offsetEnd(request.getOffsetEnd())
                .updatedAt(now)
                .status(SignatureStatus.ACTUAL)
                .build();
        
        Signature saved = signatureRepository.save(signature);
        
        String signatureHash = generateSignatureHash(saved);
        saved.setDigitalSignatureBase64(signatureHash);
        saved = signatureRepository.save(saved);
        
        saveAudit(saved.getId(), changedBy, "CREATE", null, "Signature created");
        
        log.info("Signature created: {} by {}", saved.getThreatName(), changedBy);
        return saved;
    }

    @Transactional
    public Signature updateSignature(UUID id, SignatureRequest request, String changedBy) {
        Signature signature = signatureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Signature not found"));
        
        if (signature.getStatus() == SignatureStatus.DELETED) {
            throw new RuntimeException("Cannot update deleted signature");
        }
        
        validateSignatureRequest(request);
        validateHexFormat(request);
        
        saveHistory(signature);
        
        List<String> changedFields = new ArrayList<>();
        
        if (!signature.getThreatName().equals(request.getThreatName())) {
            changedFields.add("threatName");
            signature.setThreatName(request.getThreatName());
        }
        if (!signature.getFirstBytesHex().equals(request.getFirstBytesHex())) {
            changedFields.add("firstBytesHex");
            signature.setFirstBytesHex(request.getFirstBytesHex());
        }
        if (!signature.getRemainderHashHex().equals(request.getRemainderHashHex())) {
            changedFields.add("remainderHashHex");
            signature.setRemainderHashHex(request.getRemainderHashHex());
        }
        if (!signature.getRemainderLength().equals(request.getRemainderLength())) {
            changedFields.add("remainderLength");
            signature.setRemainderLength(request.getRemainderLength());
        }
        if (!signature.getFileType().equals(request.getFileType())) {
            changedFields.add("fileType");
            signature.setFileType(request.getFileType());
        }
        if (!signature.getOffsetStart().equals(request.getOffsetStart())) {
            changedFields.add("offsetStart");
            signature.setOffsetStart(request.getOffsetStart());
        }
        if (!signature.getOffsetEnd().equals(request.getOffsetEnd())) {
            changedFields.add("offsetEnd");
            signature.setOffsetEnd(request.getOffsetEnd());
        }
        
        signature.setUpdatedAt(Instant.now());
        
        Signature saved = signatureRepository.save(signature);
        
        String newSignatureHash = generateSignatureHash(saved);
        saved.setDigitalSignatureBase64(newSignatureHash);
        saved = signatureRepository.save(saved);
        
        String fieldsChangedJson = formatFieldsChanged(changedFields);
        saveAudit(saved.getId(), changedBy, "UPDATE", fieldsChangedJson, 
                   "Signature updated. Changed fields: " + String.join(", ", changedFields));
        
        log.info("Signature updated: {} by {}", saved.getThreatName(), changedBy);
        return saved;
    }

    @Transactional
    public void deleteSignature(UUID id, String changedBy) {
        Signature signature = signatureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Signature not found"));
        
        if (signature.getStatus() == SignatureStatus.DELETED) {
            throw new RuntimeException("Signature already deleted");
        }
        
        saveHistory(signature);
        
        signature.setStatus(SignatureStatus.DELETED);
        signature.setUpdatedAt(Instant.now());
        Signature saved = signatureRepository.save(signature);
        
        saveAudit(saved.getId(), changedBy, "DELETE", null, "Signature deleted (logical)");
        
        log.info("Signature deleted: {} by {}", signature.getThreatName(), changedBy);
    }

    @Transactional(readOnly = true)
    public List<SignatureHistory> getHistory(UUID signatureId) {
        return historyRepository.findBySignatureIdOrderByVersionCreatedAtDesc(signatureId);
    }

    @Transactional(readOnly = true)
    public List<SignatureAudit> getAudit(UUID signatureId) {
        return auditRepository.findBySignatureIdOrderByChangedAtDesc(signatureId);
    }

    private void validateSignatureRequest(SignatureRequest request) {
        if (request.getThreatName() == null || request.getThreatName().trim().isEmpty()) {
            throw new RuntimeException("Threat name is required");
        }
        if (request.getFirstBytesHex() == null || request.getFirstBytesHex().trim().isEmpty()) {
            throw new RuntimeException("First bytes hex is required");
        }
        if (request.getRemainderHashHex() == null || request.getRemainderHashHex().trim().isEmpty()) {
            throw new RuntimeException("Remainder hash hex is required");
        }
        if (request.getFileType() == null || request.getFileType().trim().isEmpty()) {
            throw new RuntimeException("File type is required");
        }
        if (request.getRemainderLength() == null || request.getRemainderLength() < 0) {
            throw new RuntimeException("Remainder length must be >= 0");
        }
        if (request.getOffsetStart() == null || request.getOffsetStart() < 0) {
            throw new RuntimeException("Offset start must be >= 0");
        }
        if (request.getOffsetEnd() == null || request.getOffsetEnd() < request.getOffsetStart()) {
            throw new RuntimeException("Offset end must be >= offset start");
        }
    }

    private void validateHexFormat(SignatureRequest request) {
        if (!request.getFirstBytesHex().matches(HEX_PATTERN)) {
            throw new RuntimeException("First bytes hex contains invalid characters");
        }
        if (!request.getRemainderHashHex().matches(HEX_PATTERN)) {
            throw new RuntimeException("Remainder hash hex contains invalid characters");
        }
    }

    private String generateSignatureHash(Signature signature) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("threatName", signature.getThreatName());
            payload.put("firstBytesHex", signature.getFirstBytesHex());
            payload.put("remainderHashHex", signature.getRemainderHashHex());
            payload.put("remainderLength", signature.getRemainderLength());
            payload.put("fileType", signature.getFileType());
            payload.put("offsetStart", signature.getOffsetStart());
            payload.put("offsetEnd", signature.getOffsetEnd());
            payload.put("status", signature.getStatus().name());
            
            return signingService.sign(payload);
        } catch (Exception e) {
            log.error("Failed to generate signature hash: {}", e.getMessage());
            return null;
        }
    }

    private void saveHistory(Signature signature) {
        SignatureHistory history = SignatureHistory.builder()
                .signatureId(signature.getId())
                .versionCreatedAt(Instant.now())
                .threatName(signature.getThreatName())
                .firstBytesHex(signature.getFirstBytesHex())
                .remainderHashHex(signature.getRemainderHashHex())
                .remainderLength(signature.getRemainderLength())
                .fileType(signature.getFileType())
                .offsetStart(signature.getOffsetStart())
                .offsetEnd(signature.getOffsetEnd())
                .updatedAt(signature.getUpdatedAt())
                .status(signature.getStatus())
                .digitalSignatureBase64(signature.getDigitalSignatureBase64())
                .build();
        historyRepository.save(history);
    }

    private void saveAudit(UUID signatureId, String changedBy, String action, String fieldsChanged, String description) {
        SignatureAudit audit = SignatureAudit.builder()
                .signatureId(signatureId)
                .changedBy(changedBy)
                .changedAt(Instant.now())
                .fieldsChanged(fieldsChanged)
                .description(description)
                .build();
        auditRepository.save(audit);
    }

    private String formatFieldsChanged(List<String> changedFields) {
        if (changedFields == null || changedFields.isEmpty()) {
            return null;
        }
        try {
            Map<String, List<String>> wrapper = new HashMap<>();
            wrapper.put("changed", changedFields);
            return objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            return changedFields.toString();
        }
    }
}