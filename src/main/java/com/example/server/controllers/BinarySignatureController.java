package com.example.server.controllers;

import com.example.server.binary.BinarySignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/binary/signatures")
@RequiredArgsConstructor
public class BinarySignatureController {

    private final BinarySignatureService binarySignatureService;

    @GetMapping("/full")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getFull() {
        try {
            return binarySignatureService.buildFullResponse();
        } catch (Exception e) {
            log.error("Failed to build full binary response: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to build binary package: " + e.getMessage()));
        }
    }

    @GetMapping("/increment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getIncrement(@RequestParam String since) {
        Instant sinceInstant;
        try {
            sinceInstant = Instant.parse(since);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid 'since' parameter: " + e.getMessage()));
        }
        try {
            return binarySignatureService.buildIncrementResponse(sinceInstant);
        } catch (Exception e) {
            log.error("Failed to build increment binary response: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to build binary package: " + e.getMessage()));
        }
    }

    @PostMapping("/by-ids")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getByIds(@RequestBody Map<String, List<UUID>> request) {
        List<UUID> ids = request.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Field 'ids' is required and must not be empty"));
        }
        try {
            return binarySignatureService.buildByIdsResponse(ids);
        } catch (Exception e) {
            log.error("Failed to build by-ids binary response: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to build binary package: " + e.getMessage()));
        }
    }
}